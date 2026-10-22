package com.pulso.tv.ui.pulso

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.PagingSource
import coil3.compose.AsyncImage
import com.pulso.tv.PulsoApp
import com.pulso.tv.graphql.type.VideoSort
import com.pulso.tv.model.ui.Stream
import com.pulso.tv.model.ui.User
import com.pulso.tv.model.ui.Video
import com.pulso.tv.repository.datasource.ChannelVideosDataSource
import com.pulso.tv.repository.datasource.SearchChannelsDataSource
import com.pulso.tv.repository.datasource.SearchStreamsDataSource
import com.pulso.tv.ui.player.PlayerViewModel
import com.pulso.tv.ui.player.PlayerViewModel.Companion.PlayerViewModelFactory
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Design 06: channel header (live status, Assistir ao vivo, Seguir) and recent videos. */
class PulsoChannelActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModelFactory }
    private var loginTick by mutableIntStateOf(0)

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { loginTick++ }

    private lateinit var login: String
    private var channelId: String? = null
    private var displayName: String? = null
    private var image: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        login = intent.getStringExtra(EXTRA_LOGIN).orEmpty()
        channelId = intent.getStringExtra(EXTRA_ID)
        displayName = intent.getStringExtra(EXTRA_NAME)
        image = intent.getStringExtra(EXTRA_IMAGE)
        if (login.isBlank()) {
            finish()
            return
        }
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                ChannelScreen()
            }
        }
    }

    // ---- data ----------------------------------------------------------

    private suspend fun <T : Any> page(source: PagingSource<Int, T>, size: Int): List<T> {
        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = size, placeholdersEnabled = false))
        return if (result is PagingSource.LoadResult.Page) result.data else emptyList()
    }

    private suspend fun loadUser(): User? = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            SearchChannelsDataSource(
                login, TwitchApiHelper.getGQLHeaders(this@PulsoChannelActivity), m.graphQLRepository,
                TwitchApiHelper.getHelixHeaders(this@PulsoChannelActivity), m.helixRepository,
                prefs().getBoolean(C.ENABLE_INTEGRITY, false), prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            ), 10
        ).firstOrNull { it.login.equals(login, ignoreCase = true) }
    }

    private suspend fun loadLive(): Stream? = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            SearchStreamsDataSource(
                login, TwitchApiHelper.getGQLHeaders(this@PulsoChannelActivity), m.graphQLRepository,
                TwitchApiHelper.getHelixHeaders(this@PulsoChannelActivity), m.helixRepository,
                prefs().getBoolean(C.ENABLE_INTEGRITY, false), prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            ), 10
        ).firstOrNull { it.channelLogin.equals(login, ignoreCase = true) }
    }

    private suspend fun loadVideos(id: String?): List<Video> = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            ChannelVideosDataSource(
                channelId = id,
                channelLogin = login,
                gqlQueryType = null,
                gqlQuerySort = VideoSort.TIME,
                gqlType = null,
                gqlSort = "TIME",
                helixPeriod = "all",
                helixBroadcastTypes = "all",
                helixSort = "time",
                gqlHeaders = TwitchApiHelper.getGQLHeaders(this@PulsoChannelActivity),
                graphQLRepository = m.graphQLRepository,
                helixHeaders = TwitchApiHelper.getHelixHeaders(this@PulsoChannelActivity),
                helixRepository = m.helixRepository,
                enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            ), 18
        )
    }

    private suspend fun loadClips(id: String?): List<com.pulso.tv.model.ui.Clip> = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            com.pulso.tv.repository.datasource.ChannelClipsDataSource(
                channelId = id,
                channelLogin = login,
                gqlQueryPeriod = com.pulso.tv.graphql.type.ClipsPeriod.ALL_TIME,
                gqlPeriod = "ALL_TIME",
                startedAt = null,
                endedAt = null,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(this@PulsoChannelActivity),
                graphQLRepository = m.graphQLRepository,
                helixHeaders = TwitchApiHelper.getHelixHeaders(this@PulsoChannelActivity),
                helixRepository = m.helixRepository,
                enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            ), 18
        )
    }

    private fun isLoggedIn() = !tokenPrefs().getString(C.USERNAME, null).isNullOrBlank()
    private fun followSetting() = prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0

    private fun toggleFollow(following: Boolean, id: String?, live: Stream?) {
        if (id.isNullOrBlank()) return
        if (following) {
            viewModel.deleteFollowChannel(
                tokenPrefs().getString(C.USER_ID, null), id, followSetting(),
                prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this, true), prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        } else {
            viewModel.saveFollowChannel(
                tokenPrefs().getString(C.USER_ID, null), id, login, displayName ?: login, followSetting(),
                prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                !prefs().getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                live?.createdAt, prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this, true), prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    // ---- UI ------------------------------------------------------------

    @Composable
    private fun ChannelScreen() {
        var user by remember { mutableStateOf<User?>(null) }
        var live by remember { mutableStateOf<Stream?>(null) }
        var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var askLogin by remember { mutableStateOf(false) }
        var showClips by remember { mutableStateOf(false) }
        var clips by remember { mutableStateOf<List<com.pulso.tv.model.ui.Clip>?>(null) }
        val following by viewModel.isFollowing.collectAsState()
        val followEvent by viewModel.follow.collectAsState()
        val watch = remember { FocusRequester() }
        @Suppress("UNUSED_EXPRESSION") loginTick

        LaunchedEffect(Unit) {
            runCatching { user = loadUser() }
            runCatching { live = loadLive() }
            channelId = channelId ?: user?.id ?: live?.channelId
            runCatching { videos = loadVideos(channelId) }
            loading = false
        }
        LaunchedEffect(channelId, user, loginTick) {
            val id = channelId ?: return@LaunchedEffect
            viewModel.isFollowingChannel(
                tokenPrefs().getString(C.USER_ID, null), id, login, followSetting(),
                prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this@PulsoChannelActivity, true),
                TwitchApiHelper.getHelixHeaders(this@PulsoChannelActivity),
            )
        }
        LaunchedEffect(followEvent) {
            followEvent?.let { (now, error) ->
                Toast.makeText(
                    this@PulsoChannelActivity,
                    error ?: tr(if (now) "Você segue ${displayName ?: login}" else "Deixou de seguir"),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.follow.value = null
            }
        }
        LaunchedEffect(loading) { if (!loading) runCatching { watch.requestFocus() } }
        // clips are fetched the first time the tab is opened
        LaunchedEffect(showClips) {
            if (showClips && clips == null) {
                clips = runCatching { loadClips(channelId) }.getOrDefault(emptyList())
            }
        }

        val name = user?.name ?: displayName ?: login
        val avatar = user?.profileImage ?: image

        Box(Modifier.fillMaxSize().background(Pulso.Background)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(start = Pulso.Dp.safeH, end = Pulso.Dp.safeH, top = Pulso.Dp.safeV, bottom = 90.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(avatar, name)
                            Box(Modifier.width(22.dp))
                            Column {
                                Text(name, color = Pulso.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                if (live != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                        Box(
                                            Modifier.clip(RoundedCornerShape(3.dp)).background(Pulso.Live)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) { Text("AO VIVO", color = Color.White, fontSize = Pulso.Sp.badge, fontWeight = FontWeight.Bold) }
                                        Box(Modifier.width(8.dp))
                                        Text(
                                            live?.title.orEmpty() + " · " + compactCount(live?.viewerCount ?: 0) + " espectadores",
                                            color = Pulso.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                val meta = listOfNotNull(
                                    user?.followerCount?.let { compactCount(it) + " de seguidores" },
                                    live?.gameName?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(meta, color = Pulso.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PulsoPill(
                                        "Assistir ao vivo", primary = true, enabled = live != null, height = 36.dp, requester = watch,
                                    ) {
                                        live?.let {
                                            startActivity(
                                                Intent(this@PulsoChannelActivity, PulsoPlayerActivity::class.java)
                                                    .putExtra(PulsoPlayerActivity.EXTRA_STREAM, it)
                                            )
                                        }
                                    }
                                    PulsoPill(if (following == true) "Seguindo" else "Seguir", height = 36.dp) {
                                        if (!isLoggedIn()) askLogin = true else toggleFollow(following == true, channelId, live)
                                    }
                                }
                            }
                        }
                        if (!loading && live == null) {
                            OfflineBanner(name, user?.lastBroadcast)
                        }
                        Row(Modifier.padding(top = 24.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PulsoPill("Vídeos recentes", primary = !showClips, height = 36.dp) { showClips = false }
                            PulsoPill("Clipes", primary = showClips, height = 36.dp) { showClips = true }
                        }
                        when {
                            loading -> PulsoScreenMessage("Carregando…", Modifier.padding(top = 24.dp))
                            showClips && clips == null -> PulsoScreenMessage("Carregando…", Modifier.padding(top = 24.dp))
                            showClips && clips.orEmpty().isEmpty() -> PulsoScreenMessage("Nenhum clipe", Modifier.padding(top = 24.dp))
                            !showClips && videos.isEmpty() -> PulsoScreenMessage("Nenhum vídeo recente", Modifier.padding(top = 24.dp))
                        }
                    }
                }
                if (showClips) items(clips.orEmpty()) { c ->
                    PulsoCard(
                        thumb = c.thumbnail,
                        title = c.title.orEmpty(),
                        subtitle = listOfNotNull(
                            c.viewCount?.let { compactCount(it) + " visualizações" },
                            timeAgo(c.createdAt).takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        bottomEnd = durationText(c.durationSeconds),
                    ) {
                        startActivity(
                            Intent(this@PulsoChannelActivity, PulsoPlayerActivity::class.java)
                                .putExtra(PulsoPlayerActivity.EXTRA_CLIP, c)
                        )
                    }
                }
                if (!showClips) items(videos) { v ->
                    PulsoCard(
                        thumb = v.thumbnail,
                        title = v.title.orEmpty(),
                        subtitle = listOfNotNull(
                            v.viewCount?.let { compactCount(it) + " visualizações" },
                            timeAgo(v.createdAt).takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        bottomEnd = durationText(v.durationSeconds),
                    ) {
                        startActivity(
                            Intent(this@PulsoChannelActivity, PulsoPlayerActivity::class.java)
                                .putExtra(PulsoPlayerActivity.EXTRA_VIDEO, v)
                        )
                    }
                }
            }
            PulsoOkHint(
                "Assistir ao vivo",
                Modifier.align(Alignment.BottomEnd).padding(end = Pulso.Dp.safeH, bottom = Pulso.Dp.safeV),
            )
            if (askLogin) LoginAsk(
                onLogin = {
                    askLogin = false
                    loginLauncher.launch(Intent(this@PulsoChannelActivity, PulsoLoginActivity::class.java))
                },
                onCancel = { askLogin = false },
            )
        }
    }

    /** Big, calm notice shown instead of the live status when the channel is not streaming. */
    @Composable
    private fun OfflineBanner(name: String, lastBroadcast: String?) {
        val ago = timeAgo(lastBroadcast)
        Box(
            Modifier
                .padding(top = 22.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Pulso.SurfaceRaised, Pulso.Surface)
                    )
                )
                .padding(horizontal = 32.dp, vertical = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(Pulso.TextSecondary)
                )
                Box(Modifier.width(18.dp))
                Column {
                    Text(
                        "$name está offline",
                        color = Pulso.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (ago.isNotBlank()) "Última transmissão $ago · veja os vídeos recentes abaixo"
                        else "Veja os vídeos recentes abaixo",
                        color = Pulso.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun Avatar(url: String?, name: String) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(Pulso.Cobalt), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            if (!url.isNullOrBlank()) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }

    @Composable
    private fun LoginAsk(onLogin: () -> Unit, onCancel: () -> Unit) {
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
                    PulsoPill("Login", primary = true, height = 36.dp, requester = first, onClick = onLogin)
                    PulsoPill("Cancelar", height = 36.dp, onClick = onCancel)
                }
            }
        }
    }

    companion object {
        const val EXTRA_LOGIN = "pulso_login"
        const val EXTRA_ID = "pulso_id"
        const val EXTRA_NAME = "pulso_name"
        const val EXTRA_IMAGE = "pulso_image"
    }
}
