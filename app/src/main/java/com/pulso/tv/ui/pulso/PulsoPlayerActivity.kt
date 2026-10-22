package com.pulso.tv.ui.pulso

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.pulso.tv.PulsoApp
import com.pulso.tv.model.PlaybackState
import com.pulso.tv.model.VideoQuality
import com.pulso.tv.model.ui.Clip
import com.pulso.tv.model.ui.Stream
import com.pulso.tv.model.ui.Video
import com.pulso.tv.ui.chat.ChatViewModel
import com.pulso.tv.ui.chat.ChatViewModel.Companion.ChatViewModelFactory
import com.pulso.tv.ui.player.BasePlaybackService
import com.pulso.tv.ui.player.ExoPlayerService
import com.pulso.tv.ui.player.PlayerViewModel
import com.pulso.tv.ui.player.PlayerViewModel.Companion.PlayerViewModelFactory
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pulso's own player screen. The decoding engine is Pulso's ExoPlayerService
 * (stream token, usher playlist, qualities, low latency): this activity only
 * binds to it and draws the TV interface on top.
 */
class PulsoPlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModelFactory }
    private val chatViewModel: ChatViewModel by viewModels { ChatViewModelFactory }

    private var service by mutableStateOf<ExoPlayerService?>(null)
    private var connection: ServiceConnection? = null
    private var playerListener: Player.Listener? = null

    // bumped whenever something the UI reads from the service changes
    private var tick by mutableIntStateOf(0)
    private var playing by mutableStateOf(false)
    private var buffering by mutableStateOf(true)
    private var chatStarted = false

    // bumped by any key, tap or mouse move so the chrome comes back and its timer restarts
    private var interaction by mutableIntStateOf(0)

    // null | "options" | "quality" | "aspect" | "timer" | "login"
    private var menu by mutableStateOf<String?>(null)
    private var keepChromeHidden by mutableStateOf(false)
    private var aspect by mutableIntStateOf(0)         // 0 automatic 16:9 · 1 4:3 · 2 fill
    private var sleepMinutes by mutableIntStateOf(0)   // 0 = off
    private var isVod = false
    private var isClip = false
    private var muted by mutableStateOf(false)
    private var vod: Video? = null
    private var position by mutableLongStateOf(0L)
    private var duration by mutableLongStateOf(0L)
    private var playerError by mutableStateOf<String?>(null)
    private var ended by mutableStateOf(false)
    private var chatVisible by mutableStateOf(true)
    private var fullscreen by mutableStateOf(false)

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    private val listener = object : ExoPlayerService.Listener {
        override fun started() { runOnUiThread { tick++; startChatIfReady() } }
        override fun loaded() { runOnUiThread { tick++ } }
        override fun changePlayerMode() {}
        override fun toast(resId: Int, duration: Int) {
            runOnUiThread { Toast.makeText(this@PulsoPlayerActivity, resId, duration).show() }
        }
        override fun toast(text: CharSequence, duration: Int) {
            runOnUiThread { Toast.makeText(this@PulsoPlayerActivity, text, duration).show() }
        }
        override fun updateVideoInfo() { runOnUiThread { tick++ } }
        override fun setPlayerSurface(secondaryPlayer: Boolean) { runOnUiThread { attachSurface() } }
    }

    private var surfaceView: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val stream: Stream? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_STREAM, Stream::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_STREAM)
        }
        val video: Video? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VIDEO)
        }
        val clip: Clip? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_CLIP, Clip::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CLIP)
        }
        if (stream == null && video == null && clip == null) {
            finish()
            return
        }
        isVod = video != null || clip != null
        isClip = clip != null
        vod = video
        if (isVod) chatVisible = false // chat replay is not part of Pulso yet

        // the screen header reads a Stream; a recorded video or clip is shown through one too
        val header = stream ?: video?.let {
            Stream(
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                title = it.title,
                viewerCount = it.viewCount,
            )
        } ?: Stream(
            channelId = clip!!.channelId,
            channelLogin = clip.channelLogin,
            channelName = clip.channelName,
            title = clip.title,
            viewerCount = clip.viewCount,
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                PulsoPlayerScreen(header)
            }
        }
        startPlayback(stream, video, clip)
    }

    private fun startPlayback(stream: Stream?, video: Video?, clip: Clip?) {
        val repo = (application as PulsoApp).pulsoModule.playerRepository
        val state = if (clip != null) {
            PlaybackState(
                type = BasePlaybackService.CLIP,
                clipId = clip.id,
                channelId = clip.channelId,
                channelLogin = clip.channelLogin,
                channelName = clip.channelName,
                channelImage = clip.channelImage,
                gameId = clip.gameId,
                gameSlug = clip.gameSlug,
                gameName = clip.gameName,
                title = clip.title,
                thumbnail = clip.thumbnail,
                createdAt = clip.createdAt,
                durationSeconds = clip.durationSeconds,
            )
        } else if (video != null) {
            PlaybackState(
                type = BasePlaybackService.VIDEO,
                videoId = video.id,
                channelId = video.channelId,
                channelLogin = video.channelLogin,
                channelName = video.channelName,
                channelImage = video.channelImage,
                gameId = video.gameId,
                gameSlug = video.gameSlug,
                gameName = video.gameName,
                title = video.title,
                thumbnail = video.thumbnail,
                createdAt = video.createdAt,
                durationSeconds = video.durationSeconds,
                videoType = video.type,
                position = PulsoHistory.position(this, video.id), // resume where it stopped
            )
        } else {
            PlaybackState(
                type = BasePlaybackService.STREAM,
                streamId = stream!!.id,
                channelId = stream.channelId,
                channelLogin = stream.channelLogin,
                channelName = stream.channelName,
                channelImage = stream.channelImage,
                gameId = stream.gameId,
                gameSlug = stream.gameSlug,
                gameName = stream.gameName,
                title = stream.title,
                thumbnail = stream.thumbnail,
                createdAt = stream.createdAt,
                viewerCount = stream.viewerCount,
            )
        }
        lifecycleScope.launch {
            repo.savePlaybackStates(listOf(state))
            bindToService()
        }
    }

    private fun bindToService() {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val svc = (binder as ExoPlayerService.ServiceBinder).getService()
                svc.serviceListener = listener
                svc.setStopServiceTimer(false)
                service = svc
                val player = svc.secondaryPlayer ?: svc.player
                val l = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        buffering = playbackState == Player.STATE_BUFFERING
                        playing = player?.isPlaying == true
                        ended = playbackState == Player.STATE_ENDED
                        if (playbackState == Player.STATE_READY) playerError = null
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playerError = error.message ?: "erro de reprodução"
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        playing = playWhenReady && player?.playbackState == Player.STATE_READY
                    }
                }
                player?.addListener(l)
                playerListener = l
                attachSurface()
                if (svc.started) startChatIfReady()
                tick++
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val intent = Intent(this, ExoPlayerService::class.java).apply {
            action = ExoPlayerService.INTENT_START
        }
        startService(intent)
        bindService(intent, conn, Context.BIND_AUTO_CREATE)
        connection = conn
    }

    private fun attachSurface() {
        val svc = service ?: return
        val sv = surfaceView ?: return
        (svc.secondaryPlayer ?: svc.player)?.setVideoSurfaceView(sv)
    }

    private fun startChatIfReady() {
        val svc = service ?: return
        if (isVod || chatStarted || svc.channelLogin.isNullOrBlank()) return
        chatStarted = true
        chatViewModel.startLive(
            prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            prefs().getString(C.CHAT_RECENT_MESSAGES_URL, "https://recent-messages.robotty.de/api/v2/recent-messages/\$channel"),
            svc.channelId,
            svc.channelLogin,
            svc.channelName,
            svc.streamId,
        )
    }

    private fun toggleMute() {
        val svc = service ?: return
        muted = !muted
        val v = if (muted) 0f else 1f
        svc.player?.volume = v
        svc.secondaryPlayer?.volume = v
    }

    /** "Ir para o canal": opens the channel screen and closes the player. */
    private fun openChannelPage() {
        val svc = service ?: return
        val login = svc.channelLogin ?: return
        startActivity(
            Intent(this, PulsoChannelActivity::class.java)
                .putExtra(PulsoChannelActivity.EXTRA_LOGIN, login)
                .putExtra(PulsoChannelActivity.EXTRA_ID, svc.channelId)
                .putExtra(PulsoChannelActivity.EXTRA_NAME, svc.channelName)
                .putExtra(PulsoChannelActivity.EXTRA_IMAGE, svc.channelImage)
        )
        leave()
    }

    /** Voltar: closes a submenu, then the panel, then leaves the player. */
    private fun handleBack() {
        when (menu) {
            null -> leave()
            "quality", "aspect", "timer" -> menu = "options"
            else -> {
                // closing the panel returns to the bare player, controls hidden
                keepChromeHidden = true
                menu = null
            }
        }
    }

    private fun saveVodProgress() {
        val v = vod ?: return
        PulsoHistory.saveProgress(this, v, position, duration)
    }

    private fun leave() {
        saveVodProgress()
        val svc = service
        svc?.pause()
        svc?.stop()
        playerListener?.let { (svc?.secondaryPlayer ?: svc?.player)?.removeListener(it) }
        playerListener = null
        svc?.serviceListener = null
        connection?.let { runCatching { unbindService(it) } }
        connection = null
        svc?.stopSelf()
        service = null
        finish()
    }

    override fun onDestroy() {
        if (connection != null) {
            val svc = service
            playerListener?.let { (svc?.secondaryPlayer ?: svc?.player)?.removeListener(it) }
            svc?.serviceListener = null
            connection?.let { runCatching { unbindService(it) } }
            connection = null
            if (isFinishing) svc?.stopSelf()
        }
        super.onDestroy()
    }

    // ---- follow --------------------------------------------------------

    private fun isLoggedIn() = !tokenPrefs().getString(C.USERNAME, null).isNullOrBlank()

    private fun followSetting() = prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0

    private fun toggleFollow(following: Boolean) {
        val svc = service ?: return
        if (following) {
            viewModel.deleteFollowChannel(
                tokenPrefs().getString(C.USER_ID, null),
                svc.channelId,
                followSetting(),
                prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this, true),
                prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        } else {
            viewModel.saveFollowChannel(
                tokenPrefs().getString(C.USER_ID, null),
                svc.channelId,
                svc.channelLogin,
                svc.channelName,
                followSetting(),
                prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                !prefs().getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                svc.createdAt,
                prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this, true),
                prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    // ---- UI ------------------------------------------------------------

    @Composable
    private fun PulsoPlayerScreen(stream: Stream) {
        var chromeVisible by remember { mutableStateOf(true) }
        val following by viewModel.isFollowing.collectAsState()
        val followEvent by viewModel.follow.collectAsState()
        val svc = service
        @Suppress("UNUSED_EXPRESSION") tick

        // load follow state once the channel is known
        LaunchedEffect(svc?.channelId) {
            val s = svc ?: return@LaunchedEffect
            if (s.channelId.isNullOrBlank()) return@LaunchedEffect
            viewModel.isFollowingChannel(
                tokenPrefs().getString(C.USER_ID, null),
                s.channelId,
                s.channelLogin,
                followSetting(),
                prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this@PulsoPlayerActivity, true),
                TwitchApiHelper.getHelixHeaders(this@PulsoPlayerActivity),
            )
        }
        LaunchedEffect(followEvent) {
            followEvent?.let { (isNowFollowing, error) ->
                Toast.makeText(
                    this@PulsoPlayerActivity,
                    error ?: tr(if (isNowFollowing) "Você segue ${svc?.channelName ?: ""}" else "Deixou de seguir"),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.follow.value = null
            }
        }

        // chrome hides by itself after a few seconds without input
        LaunchedEffect(interaction, menu, keepChromeHidden) {
            if (menu == null && keepChromeHidden) {
                chromeVisible = false
                return@LaunchedEffect
            }
            chromeVisible = true
            if (menu == null) {
                delay(5000)
                chromeVisible = false
            }
        }

        // sleep timer: leave the player when it runs out
        LaunchedEffect(sleepMinutes) {
            if (sleepMinutes > 0) {
                delay(sleepMinutes * 60_000L)
                leave()
            }
        }

        // progress of the video (and the saved resume point for recorded ones)
        LaunchedEffect(svc) {
            val s = svc ?: return@LaunchedEffect
            var n = 0
            while (true) {
                val p = s.secondaryPlayer ?: s.player
                if (p != null) {
                    position = p.currentPosition.coerceAtLeast(0L)
                    duration = p.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
                }
                if (isVod && ++n % 20 == 0) saveVodProgress()
                delay(500)
            }
        }

        val showChat = chatVisible && !fullscreen
        Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { interaction++ }
                }
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AndroidView(
                    // 16:9 with black bars; full screen still fills everything
                    modifier = Modifier.align(Alignment.Center).then(
                        when (aspect) {
                            1 -> Modifier.aspectRatio(4f / 3f)
                            2 -> Modifier.fillMaxSize()
                            else -> Modifier.aspectRatio(16f / 9f)
                        }
                    ),
                    factory = { ctx ->
                        SurfaceView(ctx).also {
                            surfaceView = it
                            attachSurface()
                        }
                    }
                )
                if (buffering) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Carregando…", color = Pulso.TextSecondary, fontSize = 16.sp)
                    }
                }
                // The SurfaceView swallows pointer events, so this transparent layer
                // listens for taps and mouse movement to bring the chrome back.
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                interaction++
                            }
                        }
                    }
                )
                if (chromeVisible || menu != null) {
                    PlayerChrome(
                        title = svc?.title ?: stream.title.orEmpty(),
                        channel = svc?.channelName ?: stream.channelName.orEmpty(),
                        viewers = (svc?.viewerCount ?: stream.viewerCount)?.let { formatViewers(it) }.orEmpty(),
                        playing = playing,
                        following = following == true,
                        chatVisible = chatVisible,
                        fullscreen = fullscreen,
                        onInteract = { interaction++ },
                        onBack = { leave() },
                        onPlayPause = { service?.togglePause() },
                        onFollow = {
                            if (!isLoggedIn()) menu = "login" else toggleFollow(following == true)
                        },
                        onChat = { chatVisible = !chatVisible; if (chatVisible) fullscreen = false },
                        onChannel = { openChannelPage() },
                        onOptions = { menu = "options" },
                        onMute = { toggleMute() },
                        onFullscreen = { fullscreen = !fullscreen },
                        menuOpen = menu != null,
                    )
                }
            }
            if (showChat) {
                Column(Modifier.width(280.dp).fillMaxHeight().background(Pulso.Surface)) {
                    PulsoChat(chatViewModel, Modifier.weight(1f).fillMaxWidth())
                    Box(Modifier.fillMaxWidth().padding(10.dp)) {
                        PulsoPill(
                            "Escrever no chat", height = 36.dp, modifier = Modifier.fillMaxWidth(),
                        ) {
                            interaction++
                            menu = if (isLoggedIn()) "compose" else "login"
                        }
                    }
                }
            }
        }
        OptionsMenus(svc)
        StatusOverlay(svc)
        }
    }

    /** Shown instead of a silent black screen: stream ended, video ended, or a playback error. */
    @Composable
    private fun StatusOverlay(svc: ExoPlayerService?) {
        val err = playerError
        if (!ended && err == null) return
        val first = remember { FocusRequester() }
        LaunchedEffect(ended, err) { runCatching { first.requestFocus() } }
        val (title, body) = when {
            ended && !isVod -> "Transmissão encerrada" to "${svc?.channelName ?: "O canal"} parou de transmitir."
            ended -> "Fim do vídeo" to "Você chegou ao final."
            isVod -> "Não foi possível carregar o vídeo" to (err ?: "")
            else -> "Não foi possível carregar a transmissão" to (err ?: "")
        }
        Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, color = Pulso.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                if (body.isNotBlank()) {
                    Text(body, color = Pulso.TextSecondary, fontSize = 14.sp, maxLines = 2, modifier = Modifier.width(420.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    when {
                        ended && !isVod -> PulsoPill("Ir para o canal", primary = true, height = 38.dp, requester = first) { openChannelPage() }
                        ended -> PulsoPill("Ver de novo", primary = true, height = 38.dp, requester = first) {
                            svc?.seekTo(0L)
                            svc?.player?.play()
                            ended = false
                        }
                        else -> PulsoPill("Tentar de novo", primary = true, height = 38.dp, requester = first) {
                            playerError = null
                            svc?.retry(if (isClip) "refreshClip" else if (isVod) "refreshVideo" else "refreshStream")
                        }
                    }
                    PulsoPill("Voltar", height = 38.dp) { leave() }
                }
            }
        }
    }

    /** Progress bar of a recorded video: ← → seek 10 s, tap/click jumps. */
    @Composable
    private fun VodBar(onInteract: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        var widthPx by remember { mutableIntStateOf(1) }
        val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        fun seekBy(delta: Long) {
            service?.seekTo((position + delta).coerceIn(0L, duration.coerceAtLeast(0L)))
            onInteract()
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(durationText((position / 1000).toInt()), color = Pulso.TextPrimary, fontSize = 12.sp)
            Box(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(if (focused) 10.dp else 6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Pulso.SurfaceRaised)
                    .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteract() }
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { seekBy(-10_000L); true }
                        else if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { seekBy(10_000L); true }
                        else false
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            if (duration > 0) service?.seekTo((off.x / widthPx * duration).toLong())
                            onInteract()
                        }
                    }
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (focused) Pulso.TextPrimary else Pulso.Yellow))
            }
            Box(Modifier.width(10.dp))
            Text(durationText((duration / 1000).toInt()), color = Pulso.TextSecondary, fontSize = 12.sp)
        }
    }

    // ---- options panel (design 04) ---------------------------------------

    private class MenuRow(
        val label: String,
        val value: String? = null,
        val enabled: Boolean = true,
        val selected: Boolean = false,
        val onClick: () -> Unit = {},
    )

    private fun aspectLabel() = when (aspect) { 1 -> "4:3"; 2 -> "Preencher"; else -> "Automático" }

    private fun timerLabel() = when (sleepMinutes) {
        0 -> "Desligado"
        60 -> "1 hora"
        120 -> "2 horas"
        else -> "$sleepMinutes min"
    }

    @Composable
    private fun OptionsMenus(svc: ExoPlayerService?) {
        val qualities = svc?.qualities.orEmpty().filter {
            // Automático has no url of its own (it plays the master playlist), so it must not be filtered by it
            (it.name == VideoQuality.AUTO_QUALITY || !it.url.isNullOrBlank()) &&
                it.name != VideoQuality.AUDIO_ONLY_QUALITY && it.name != VideoQuality.CHAT_ONLY_QUALITY
        }
        when (menu) {
            "options" -> MenuPanel(
                title = "Opções",
                hint = "Escolher",
                rows = listOf(
                    MenuRow("Qualidade", svc?.quality?.let { qualityLabel(it) } ?: "—") { menu = "quality" },
                    MenuRow("Proporção", aspectLabel()) { menu = "aspect" },
                    MenuRow("Chat", if (isVod) "Indisponível" else if (chatVisible) "Ocultar" else "Mostrar", enabled = !isVod) {
                        chatVisible = !chatVisible
                        if (chatVisible) fullscreen = false
                    },
                    MenuRow("Legendas", "Indisponível", enabled = false),
                    MenuRow("Temporizador", timerLabel()) { menu = "timer" },
                ),
            )
            "quality" -> MenuPanel(
                title = "Qualidade",
                hint = "Escolher qualidade",
                rows = if (qualities.isEmpty()) listOf(MenuRow("Carregando qualidades…", enabled = false)) else qualities.map { q ->
                    MenuRow(qualityLabel(q), selected = q.name == svc?.quality?.name && q.url == svc?.quality?.url) {
                        service?.changeQuality(q)
                        menu = "options"
                    }
                },
            )
            "aspect" -> MenuPanel(
                title = "Proporção",
                hint = "Escolher proporção",
                rows = listOf("Automático", "4:3", "Preencher").mapIndexed { i, name ->
                    MenuRow(name, selected = aspect == i) { aspect = i; menu = "options" }
                },
            )
            "timer" -> MenuPanel(
                title = "Temporizador",
                hint = "Definir temporizador",
                rows = listOf(0, 15, 30, 60, 120).map { m ->
                    MenuRow(
                        when (m) { 0 -> "Desligado"; 60 -> "1 hora"; 120 -> "2 horas"; else -> "$m min" },
                        selected = sleepMinutes == m,
                    ) { sleepMinutes = m; menu = "options" }
                },
            )
            "compose" -> ComposeDialog(
                onSend = { text ->
                    sendChat(text)
                    menu = null
                },
            )
            "login" -> LoginDialog(
                onLogin = {
                    menu = null
                    loginLauncher.launch(Intent(this@PulsoPlayerActivity, PulsoLoginActivity::class.java))
                },
                onCancel = { menu = null },
            )
        }
    }

    /** Right-hand panel over a scrim: 760 px in the 1920 design = 380 dp. */
    @Composable
    private fun MenuPanel(title: String, hint: String, rows: List<MenuRow>) {
        val firstEnabled = rows.indexOfFirst { it.enabled }.coerceAtLeast(0)
        val first = remember(title) { FocusRequester() }
        LaunchedEffect(title) { runCatching { first.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(Color(0xB3000000))
                .pointerInput(Unit) { detectTapGestures { handleBack() } }
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(380.dp)
                    .fillMaxHeight()
                    .background(Pulso.Surface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 24.dp, vertical = 27.dp),
            ) {
                Text(title, color = Pulso.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    rows.forEachIndexed { i, row ->
                        MenuRowView(row, if (i == firstEnabled) first else null)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HintChip("OK", hint)
                    HintChip("Voltar", "Fechar o menu")
                }
            }
        }
    }

    @Composable
    private fun MenuRowView(row: MenuRow, requester: FocusRequester?) {
        var focused by remember { mutableStateOf(false) }
        val latestClick by rememberUpdatedState(row.onClick)
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .alpha(if (row.enabled) 1f else 0.4f)
                .then(if (row.enabled && requester != null) Modifier.focusRequester(requester) else Modifier)
                .then(
                    if (row.enabled) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                                    latestClick(); true
                                } else false
                            }
                            .pointerInput(Unit) { detectTapGestures { latestClick() } }
                    } else Modifier
                )
                .then(if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape) else Modifier)
                .background(if (focused) Pulso.TextPrimary else Pulso.SurfaceRaised, shape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (focused) Pulso.Background else Pulso.TextPrimary
            val sub = if (focused) Pulso.Background else Pulso.TextSecondary
            Text(
                (if (row.selected) "✓  " else "") + row.label,
                color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (row.value != null) Text(row.value, color = sub, fontSize = 13.sp, maxLines = 1)
        }
    }

    @Composable
    private fun HintChip(key: String, text: String) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Pulso.SurfaceRaised)
                .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(22.dp).clip(RoundedCornerShape(50)).background(Pulso.TextPrimary).padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(key, color = Pulso.Background, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
            Box(Modifier.width(7.dp))
            Text(text, color = Pulso.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }

    @Composable
    private fun PlayerChrome(
        title: String,
        channel: String,
        viewers: String,
        playing: Boolean,
        following: Boolean,
        chatVisible: Boolean,
        fullscreen: Boolean,
        onInteract: () -> Unit,
        onBack: () -> Unit,
        onPlayPause: () -> Unit,
        onFollow: () -> Unit,
        onChat: () -> Unit,
        onChannel: () -> Unit,
        onOptions: () -> Unit,
        onMute: () -> Unit,
        onFullscreen: () -> Unit,
        menuOpen: Boolean,
    ) {
        val first = remember { FocusRequester() }
        val optionsButton = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        // Voltar closes the options panel and returns focus to the Opções button
        var wasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(menuOpen) {
            if (!menuOpen && wasOpen) runCatching { optionsButton.requestFocus() }
            wasOpen = menuOpen
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            )
            Row(
                Modifier.padding(start = 32.dp, top = 24.dp).align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeButton("Voltar", false, onBack, onInteract, round = true, icon = { BackIcon(it) })
                Box(Modifier.width(16.dp))
                Column {
                    Text(title, color = Pulso.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(channel, color = Pulso.TextPrimary, fontSize = 14.sp)
                        Box(Modifier.width(8.dp))
                        if (!isVod) {
                            Box(
                                Modifier.clip(RoundedCornerShape(3.dp)).background(Pulso.Live)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) { Text("AO VIVO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            Box(Modifier.width(8.dp))
                        }
                        Text(viewers, color = Pulso.TextSecondary, fontSize = 14.sp)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
            )
            var hint by remember { mutableStateOf("") }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 20.dp),
            ) {
                if (isVod && duration > 0) VodBar(onInteract)
                // the name of the focused button, since the buttons themselves are icons
                Text(
                    hint, color = Pulso.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(16.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChromeButton(
                        if (playing) "Pausar" else "Continuar", false, onPlayPause, onInteract, first,
                        round = true, onHint = { hint = it },
                        icon = { if (playing) PauseIcon(it) else PlayIcon(it) },
                    )
                    ChromeButton(
                        if (following) "Deixar de seguir" else "Seguir", following, onFollow, onInteract,
                        round = true, onHint = { hint = it }, icon = { HeartIcon(filled = following, color = it) },
                    )
                    if (!isVod) ChromeButton(
                        if (chatVisible) "Ocultar chat" else "Mostrar chat", false, onChat, onInteract,
                        round = true, onHint = { hint = it }, icon = { ChatIcon(visible = chatVisible, color = it) },
                    )
                    ChromeButton(
                        "Ir para o canal", false, onChannel, onInteract,
                        round = true, onHint = { hint = it }, icon = { ChannelIcon(it) },
                    )
                    ChromeButton(
                        "Opções", false, onOptions, onInteract, optionsButton,
                        round = true, onHint = { hint = it }, icon = { MoreIcon(it) },
                    )
                    Box(Modifier.weight(1f))
                    ChromeButton(
                        if (muted) "Ativar som" else "Mutar", false, onMute, onInteract,
                        round = true, onHint = { hint = it }, icon = { VolumeIcon(muted = muted, color = it) },
                    )
                    ChromeButton(
                        if (fullscreen) "Sair da tela cheia" else "Tela cheia", false, onFullscreen, onInteract,
                        round = true, onHint = { hint = it },
                        icon = { FullscreenIcon(expand = !fullscreen, color = it, modifier = Modifier.size(13.dp)) },
                    )
                }
            }
        }
    }

    @Composable
    private fun ChromeButton(
        label: String,
        accent: Boolean,
        onClick: () -> Unit,
        onInteract: () -> Unit,
        requester: FocusRequester? = null,
        round: Boolean = false,
        onHint: (String) -> Unit = {},
        icon: (@Composable (Color) -> Unit)? = null,
    ) {
        var focused by remember { mutableStateOf(false) }
        val latestClick by rememberUpdatedState(onClick)
        val latestInteract by rememberUpdatedState(onInteract)
        val latestHint by rememberUpdatedState(onHint)
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) { onInteract(); latestHint(label) }
                }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                        onClick(); true
                    } else false
                }
                .pointerInput(Unit) { detectTapGestures { latestInteract(); latestClick() } }
                .height(34.dp)
                .then(if (round) Modifier.width(34.dp) else Modifier)
                .then(if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape) else Modifier)
                .background(
                    when {
                        focused -> Pulso.TextPrimary
                        accent -> Pulso.Yellow
                        else -> Pulso.SurfaceRaised
                    },
                    shape
                )
                .padding(horizontal = if (round) 0.dp else 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val fg = if (focused || accent) Pulso.Background else Pulso.TextPrimary
            if (icon != null) {
                icon(fg)
            } else {
                Text(label, color = fg, fontSize = if (round) 20.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }

    @Composable
    private fun QualityMenu(
        qualities: List<VideoQuality>,
        current: VideoQuality?,
        onPick: (VideoQuality) -> Unit,
        onClose: () -> Unit,
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && e.key == Key.Back) { onClose(); true } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.width(320.dp).clip(RoundedCornerShape(16.dp)).background(Pulso.Surface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Qualidade", color = Pulso.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                qualities.forEachIndexed { i, q ->
                    val selected = q.name == current?.name && q.url == current?.url
                    ChromeButton(
                        label = (if (selected) "✓ " else "") + qualityLabel(q),
                        accent = selected,
                        onClick = { onPick(q) },
                        onInteract = {},
                        requester = if (i == 0) first else null,
                    )
                }
                if (qualities.isEmpty()) {
                    Text("Carregando qualidades…", color = Pulso.TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }

    private fun sendChat(text: String) {
        val msg = text.trim()
        val svc = service ?: return
        if (msg.isEmpty()) return
        chatViewModel.send(
            message = msg,
            replyId = null,
            networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            gqlHeaders = TwitchApiHelper.getGQLHeaders(this, true),
            helixHeaders = TwitchApiHelper.getHelixHeaders(this),
            accountId = tokenPrefs().getString(C.USER_ID, null),
            channelId = svc.channelId,
            channelLogin = svc.channelLogin,
            useApiCommands = prefs().getBoolean(C.DEBUG_API_COMMANDS, true),
            useApiChatMessages = prefs().getBoolean(C.DEBUG_API_CHAT_MESSAGES, true),
            enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    /** Message box over the player: the system keyboard opens, OK/Enter sends, Voltar cancels. */
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable
    private fun ComposeDialog(onSend: (String) -> Unit) {
        var text by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .pointerInput(Unit) { detectTapGestures { menu = null } },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.padding(top = 60.dp).width(520.dp).clip(RoundedCornerShape(16.dp))
                    .background(Pulso.Surface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Enviar mensagem", color = Pulso.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Pulso.TextPrimary, fontSize = 16.sp, fontFamily = PulsoFont),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Pulso.Yellow),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend(text) }),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Pulso.SurfaceRaised).padding(14.dp)
                        .focusRequester(focus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChromeButton("Enviar", true, { onSend(text) }, {})
                    ChromeButton("Cancelar", false, { menu = null }, {})
                }
            }
        }
    }

    @Composable
    private fun LoginDialog(onLogin: () -> Unit, onCancel: () -> Unit) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .pointerInput(Unit) { detectTapGestures { onCancel() } }
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && e.key == Key.Back) { onCancel(); true } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(Pulso.Surface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "É necessário fazer login para seguir este canal. Deseja fazer login?",
                    color = Pulso.TextPrimary, fontSize = 16.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChromeButton("Login", true, onLogin, {}, first)
                    ChromeButton("Cancelar", false, onCancel, {})
                }
            }
        }
    }

    private fun qualityLabel(q: VideoQuality): String = when (q.name) {
        VideoQuality.AUTO_QUALITY -> "Automático"
        VideoQuality.SOURCE_QUALITY -> "Fonte" + (q.resolution?.let { " ($it)" } ?: "")
        else -> q.name.orEmpty()
    }

    private fun formatViewers(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1f mi", n / 1_000_000f)
        n >= 1_000 -> String.format("%.1f mil", n / 1_000f)
        else -> n.toString()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            keepChromeHidden = false
            interaction++
        }
        // Escape / Backspace also leave the player (remotes and keyboards)
        if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_ESCAPE || (event.keyCode == KeyEvent.KEYCODE_DEL && menu != "compose"))
        ) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_STREAM = "pulso_stream"
        const val EXTRA_VIDEO = "pulso_video"
        const val EXTRA_CLIP = "pulso_clip"
    }
}
