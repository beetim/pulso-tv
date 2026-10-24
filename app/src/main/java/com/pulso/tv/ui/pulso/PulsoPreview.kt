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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import com.pulso.tv.PulsoApp
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import kotlinx.coroutines.delay

private const val PREVIEW_DELAY_MS = 1200L

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

    LaunchedEffect(active, channelLogin) {
        firstFrame = false
        if (!active) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        val url = try {
            val app = context.applicationContext as PulsoApp
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            app.pulsoModule.playerRepository.loadStreamPlaylistUrl(
                context = context,
                networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(context, prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = channelLogin,
                platform = prefs.getString(C.TOKEN_PLATFORM, "web"),
                playerType = prefs.getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = "h264", // the lightest decode; the preview is tiny anyway
                proxyPlaybackAccessToken = false,
                proxyHost = null, proxyPort = null, proxyUser = null, proxyPassword = null, proxyTimeout = null,
                enableIntegrity = false,
            )
        } catch (e: Exception) {
            return@LaunchedEffect // no preview: the thumbnail just stays
        }
        val p = ExoPlayer.Builder(context).build().apply {
            volume = PreferenceManager.getDefaultSharedPreferences(context).getInt(C.PLAYER_VOLUME, 100) / 100f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(640, 360)
                .build()
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrame = true }
            })
            setMediaSource(
                HlsMediaSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
                    .createMediaSource(MediaItem.fromUri(url))
            )
            playWhenReady = true
            prepare()
        }
        player = p
    }

    // focus left, screen paused or card gone: free the decoder
    DisposableEffect(active, channelLogin) {
        onDispose {
            player?.release()
            player = null
            firstFrame = false
        }
    }

    val alpha by animateFloatAsState(if (firstFrame) 1f else 0f, label = "previewFade")
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
