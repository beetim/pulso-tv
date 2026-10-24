package com.pulso.tv.ui.pulso

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import com.pulso.tv.ui.player.ExoPlayerService
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import com.pulso.tv.PulsoApp
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREVIEW_DELAY_MS = 1200L

/** Same test the player engine uses (ExoPlayerService.checkPlaylist): is the newest segment a stitched ad? */
private fun isAdPlaylist(playlist: HlsMediaPlaylist?): Boolean {
    return playlist?.segments?.lastOrNull()?.let { segment ->
        val title = segment.title.substringBefore('|')
        if (title == "Amazon" || title == "Adform" || title == "DCM") {
            true
        } else {
            val segmentStartTime = playlist.startTimeUs + segment.relativeStartTimeUs
            playlist.interstitials.find { dateRange ->
                if (dateRange.id.startsWith("stitched-ad-")
                    || dateRange.clientDefinedAttributes.find { it.name == "CLASS" }?.textValue == "twitch-stitched-ad"
                    || dateRange.clientDefinedAttributes.find { it.name.startsWith("X-TV-TWITCH-AD-") } != null
                ) {
                    val startTime = dateRange.startDateUnixUs
                    val endTime = dateRange.endDateUnixUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }
                        ?: dateRange.durationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it }
                        ?: dateRange.plannedDurationUs.takeIf { it != androidx.media3.common.C.TIME_UNSET }?.let { startTime + it }
                    if (endTime != null) segmentStartTime in startTime..<endTime else false
                } else false
            } != null
        }
    } == true
}

/**
 * Live preview (with sound, at the user's player volume) drawn over a stream card's thumbnail while the card holds the D-pad focus
 * (like the YouTube TV home). It waits a moment so scrolling through the shelf doesn't open a
 * connection per card, plays a small low-bitrate rendition and fades in on the first frame; the
 * thumbnail underneath stays visible until then. Only one card is focused at a time, so only one
 * player exists. It is released when the focus leaves, the screen is paused or the card leaves
 * composition. OK opens the real player ([PulsoPlayerActivity]) from the card's own onClick.
 */
@Composable
fun StreamPreview(channelLogin: String?, focused: Boolean, modifier: Modifier = Modifier) {
    if (channelLogin.isNullOrBlank()) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PulsoSettingsActivity.PREF_PREVIEW, true)
    val active = enabled && focused && resumed

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var firstFrame by remember { mutableStateOf(false) }
    var inAd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(active, channelLogin) {
        firstFrame = false
        if (!active) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // attempt 0 = the app's own player type; when it comes with an ad the preview reloads the channel
        // through the next one (the same trick the player engine uses), and only hides if all of them do
        val playerTypes = listOf<Pair<String?, String?>>(
            null to null, "android" to "autoplay", "web" to "popout", "web" to "autoplay",
        )
        suspend fun urlFor(attempt: Int): String? = try {
            val app = context.applicationContext as PulsoApp
            val (platform, type) = playerTypes[attempt]
            app.pulsoModule.playerRepository.loadStreamPlaylistUrl(
                context = context,
                networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(context, prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = channelLogin,
                platform = platform ?: prefs.getString(C.TOKEN_PLATFORM, "web"),
                playerType = type ?: prefs.getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = "h264", // the lightest decode; the preview is tiny anyway
                proxyPlaybackAccessToken = false,
                proxyHost = null, proxyPort = null, proxyUser = null, proxyPassword = null, proxyTimeout = null,
                enableIntegrity = false,
            )
        } catch (e: Exception) {
            null
        }
        val url = urlFor(0) ?: return@LaunchedEffect // no preview: the thumbnail just stays
        fun source(u: String) = HlsMediaSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            .setPlaylistParserFactory(ExoPlayerService.CustomHlsPlaylistParserFactory())
            .createMediaSource(MediaItem.fromUri(u))

        val p = ExoPlayer.Builder(context).build()
        val userVolume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
        var attempt = 0
        var swapping = false
        var clearJob: kotlinx.coroutines.Job? = null
        p.volume = userVolume
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setMaxVideoSize(640, 360).build()
        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val playlist = (p.currentManifest as? HlsManifest)?.mediaPlaylist
                val ad = isAdPlaylist(playlist)
                if (ad) {
                    clearJob?.cancel(); clearJob = null
                    if (!inAd) { inAd = true; p.volume = 0f }
                } else if (inAd && clearJob == null) {
                    // the playlist is ad-free at the live edge, but the player is still seconds behind it:
                    // keep hiding until playback gets past the last ad segment (same wait the engine uses)
                    val wait = playlist?.segments?.lastOrNull()?.let { (it.relativeStartTimeUs / 1000) - p.currentPosition } ?: 0L
                    clearJob = scope.launch {
                        delay(wait.coerceIn(0L, 20_000L))
                        if (player === p) { inAd = false; p.volume = userVolume }
                        clearJob = null
                    }
                }
                if (ad && !swapping && attempt + 1 < playerTypes.size) {
                    swapping = true
                    attempt++
                    scope.launch {
                        val next = urlFor(attempt)
                        swapping = false
                        if (next != null && player === p) {
                            p.setMediaSource(source(next))
                            p.prepare()
                        }
                    }
                }
            }
        })
        p.setMediaSource(source(url))
        p.playWhenReady = true
        p.prepare()
        player = p
    }

    // focus left, screen paused or card gone: free the decoder
    DisposableEffect(active, channelLogin) {
        onDispose {
            player?.release()
            player = null
            firstFrame = false
            inAd = false
        }
    }

    val alpha by animateFloatAsState(if (firstFrame && !inAd) 1f else 0f, label = "previewFade")
    val p = player
    if (p != null) {
        AndroidView(
            modifier = modifier.fillMaxSize().alpha(alpha),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                    setKeepContentOnPlayerReset(false)
                    this.player = p
                }
            },
            update = { it.player = p },
        )
    }
}
