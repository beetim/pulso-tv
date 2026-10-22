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
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
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
import com.pulso.tv.model.ui.Clip
import com.pulso.tv.model.ui.Stream
import com.pulso.tv.model.ui.User
import com.pulso.tv.model.ui.Video
import com.pulso.tv.repository.datasource.ChannelClipsDataSource
import com.pulso.tv.repository.datasource.ChannelVideosDataSource
import com.pulso.tv.repository.datasource.SearchChannelsDataSource
import com.pulso.tv.ui.player.PlayerViewModel
import com.pulso.tv.ui.player.PlayerViewModel.Companion.PlayerViewModelFactory
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Design 06: channel header (live status, Assistir ao vivo, Seguir) and recent videos. */
class PulsoChannelActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModelFactory }
    private var loginTick by mutableIntStateOf(0)
    // bumped every time the screen comes back (from the player, from the login): follow/live may have changed
    private var resumeTick by mutableIntStateOf(0)
    private var resumedOnce = false

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { loginTick++ }

    private lateinit var login: String
    private var channelId: String? = null
    private var displayName: String? = null
    private var image: String? = null

    // the data sources remember their cursor, so one per list lives as long as the screen
    private var videosSource: ChannelVideosDataSource? = null
    private var clipsSource: ChannelClipsDataSource? = null

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

    override fun onResume() {
        super.onResume()
        // the first onResume is the screen opening; the data is already being loaded
        if (resumedOnce) resumeTick++
        resumedOnce = true
    }

    // ---- data ----------------------------------------------------------

    /** What the live query says about the channel: its id/name (even when offline) and the stream if live. */
    private class LiveInfo(val channelId: String?, val name: String?, val stream: Stream?)

    /** One page; [key] is the previous page's nextKey (null = first page). Errors are thrown, not hidden. */
    private suspend fun <T : Any> page(source: PagingSource<Int, T>, key: Int?, size: Int): PagingSource.LoadResult.Page<Int, T> {
        val params: PagingSource.LoadParams<Int> = if (key == null) {
            PagingSource.LoadParams.Refresh(key = null, loadSize = size, placeholdersEnabled = false)
        } else {
            PagingSource.LoadParams.Append(key = key, loadSize = size, placeholdersEnabled = false)
        }
        return when (val result = source.load(params)) {
            is PagingSource.LoadResult.Page -> result
            is PagingSource.LoadResult.Error -> throw result.throwable
            else -> throw IllegalStateException("resultado inesperado")
        }
    }

    private suspend fun loadUser(): User? = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            SearchChannelsDataSource(
                login, TwitchApiHelper.getGQLHeaders(this@PulsoChannelActivity), m.graphQLRepository,
                TwitchApiHelper.getHelixHeaders(this@PulsoChannelActivity), m.helixRepository,
                prefs().getBoolean(C.ENABLE_INTEGRITY, false), prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            ), null, 10
        ).data.firstOrNull { it.login.equals(login, ignoreCase = true) }
    }

    /** Asks Twitch for this exact channel (not a text search), so a live channel is never missed. Throws on failure. */
    private suspend fun loadLive(): LiveInfo = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        val response = m.graphQLRepository.loadQueryUsersStream(
            prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            TwitchApiHelper.getGQLHeaders(this@PulsoChannelActivity),
            logins = listOf(login),
        )
        val users = response.data?.users
            ?: throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "sem dados")
        val u = users.firstOrNull() ?: return@withContext LiveInfo(null, null, null)
        LiveInfo(
            u.id, u.displayName,
            u.stream?.let { s ->
                Stream(
                    id = s.id,
                    channelId = u.id,
                    channelLogin = u.login,
                    channelName = u.displayName,
                    channelImageURL = u.profileImageURL,
                    gameId = s.game?.id,
                    gameSlug = s.game?.slug,
                    gameName = s.game?.displayName,
                    title = s.broadcaster?.broadcastSettings?.title,
                    thumbnailURL = s.previewImageURL,
                    createdAt = s.createdAt?.toString(),
                    viewerCount = s.viewersCount,
                    tags = s.freeformTags?.mapNotNull { tag -> tag.name },
                )
            },
        )
    }

    private suspend fun loadVideos(id: String?, key: Int?, size: Int): PagingSource.LoadResult.Page<Int, Video> =
        withContext(Dispatchers.IO) {
            val m = (application as PulsoApp).pulsoModule
            val source = videosSource ?: ChannelVideosDataSource(
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
            ).also { videosSource = it }
            page(source, key, size)
        }

    private suspend fun loadClips(id: String?, key: Int?, size: Int): PagingSource.LoadResult.Page<Int, Clip> =
        withContext(Dispatchers.IO) {
            val m = (application as PulsoApp).pulsoModule
            val source = clipsSource ?: ChannelClipsDataSource(
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
            ).also { clipsSource = it }
            page(source, key, size)
        }

    private fun isLoggedIn() = !tokenPrefs().getString(C.USERNAME, null).isNullOrBlank()
    private fun followSetting() = prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0

    private fun toggleFollow(following: Boolean, id: String?, name: String, live: Stream?) {
        if (id.isNullOrBlank()) {
            // the channel could not be identified (lookups failed): say so instead of doing nothing
            Toast.makeText(this, tr("Não foi possível seguir este canal"), Toast.LENGTH_SHORT).show()
            return
        }
        if (following) {
            viewModel.deleteFollowChannel(
                tokenPrefs().getString(C.USER_ID, null), id, followSetting(),
                prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this, true), prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        } else {
            viewModel.saveFollowChannel(
                tokenPrefs().getString(C.USER_ID, null), id, login, name, followSetting(),
                prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                !prefs().getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                live?.createdAt, prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(this, true), prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    // ---- UI ------------------------------------------------------------

    /** Puts the focus on an element; tries a few frames, until it is on screen. */
    private suspend fun grabFocus(requester: FocusRequester) {
        for (attempt in 0 until 20) {
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) break
            withFrameNanos { }
        }
    }

    /** Remembers what is focused ([kind]), so the bottom-right OK hint can say what OK does. */
    private fun Modifier.onFocusHint(set: (String) -> Unit, kind: String): Modifier =
        onFocusChanged { if (it.isFocused) set(kind) }

    private fun hintFor(kind: String, following: Boolean?, loadFailed: Boolean): String = when (kind) {
        "follow" -> if (following == true) "Deixar de seguir" else "Seguir"
        "videos" -> "Vídeos recentes"
        "clips" -> "Clipes"
        "video" -> "Assistir vídeo"
        "clip" -> "Assistir clipe"
        "more" -> if (loadFailed) "Tentar de novo" else "Carregar mais"
        "retry" -> "Tentar de novo"
        else -> "Assistir ao vivo"
    }

    @Composable
    private fun ChannelScreen() {
        val scope = rememberCoroutineScope()
        var user by remember { mutableStateOf<User?>(null) }
        var live by remember { mutableStateOf<Stream?>(null) }
        var info by remember { mutableStateOf<LiveInfo?>(null) }
        var liveChecked by remember { mutableStateOf(false) } // false until the live query worked once
        var loading by remember { mutableStateOf(true) }
        var askLogin by remember { mutableStateOf(false) }
        var showClips by remember { mutableStateOf(false) }
        var focusKind by remember { mutableStateOf("watch") }

        var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
        var videosKey by remember { mutableStateOf<Int?>(null) }
        var videosMore by remember { mutableStateOf(true) }
        var loadingVideos by remember { mutableStateOf(false) }
        var videosFailed by remember { mutableStateOf(false) }

        var clips by remember { mutableStateOf<List<Clip>?>(null) } // null until the first page arrives
        var clipsKey by remember { mutableStateOf<Int?>(null) }
        var clipsMore by remember { mutableStateOf(true) }
        var loadingClips by remember { mutableStateOf(false) }
        var clipsFailed by remember { mutableStateOf(false) }
        var retrying by remember { mutableStateOf(false) }

        val following by viewModel.isFollowing.collectAsState()
        val followEvent by viewModel.follow.collectAsState()
        val watch = remember { FocusRequester() }
        val followReq = remember { FocusRequester() }
        val newCard = remember { FocusRequester() }
        val gridState = rememberLazyGridState()
        var focusIdx by remember { mutableStateOf(-1) }
        val currentLoginTick = loginTick
        val currentResumeTick = resumeTick

        val name = user?.name ?: info?.name ?: displayName ?: login
        val avatar = user?.profileImage ?: image

        suspend fun refreshLive() {
            try {
                val result = loadLive()
                info = result
                live = result.stream
                liveChecked = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // unknown, not offline: keep what we had and do not claim the channel is offline
            }
        }

        // a page of videos; a failure keeps the "Carregar mais" card as "Tentar de novo"
        suspend fun loadMoreVideos() {
            if (loadingVideos || !videosMore) return
            loadingVideos = true
            videosFailed = false
            try {
                val p = loadVideos(channelId, videosKey, 18)
                videos = (videos + p.data).distinctBy { it.id ?: Any() }
                videosKey = p.nextKey
                if (p.nextKey == null || p.data.isEmpty()) videosMore = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                videosFailed = true
            } finally {
                loadingVideos = false
            }
        }

        suspend fun loadMoreClips() {
            if (loadingClips || !clipsMore) return
            loadingClips = true
            clipsFailed = false
            try {
                val p = loadClips(channelId, clipsKey, 18)
                clips = (clips.orEmpty() + p.data).distinctBy { it.id ?: Any() }
                clipsKey = p.nextKey
                if (p.nextKey == null || p.data.isEmpty()) clipsMore = false
            } catch (e: CancellationException) {
                throw e // leaving the tab: clips stays null and loads again next time
            } catch (e: Exception) {
                clipsFailed = true
            } finally {
                loadingClips = false
            }
        }

        LaunchedEffect(Unit) {
            try {
                user = loadUser()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the header still works with what the intent brought
            }
            refreshLive()
            channelId = channelId ?: user?.id ?: info?.channelId
            loadMoreVideos()
            loading = false
        }
        // back from the player: the stream may have started or ended
        LaunchedEffect(currentResumeTick) { if (currentResumeTick > 0) refreshLive() }
        // follow state: after the id is known, after a login and when coming back (re-checks every time)
        LaunchedEffect(channelId, user, info, currentLoginTick, currentResumeTick) {
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
                    if (error != null) tr(if (now) "Não foi possível seguir este canal" else "Não foi possível deixar de seguir")
                    else tr(if (now) "Você segue $name" else "Deixou de seguir"),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.follow.value = null
            }
        }
        // initial focus: "Assistir ao vivo", or "Seguir" when the channel is offline (that button is disabled);
        // and again if the stream ends while the screen is open
        var focusPlaced by remember { mutableStateOf(false) }
        LaunchedEffect(loading, live == null) {
            if (!loading && (!focusPlaced || live == null)) {
                focusPlaced = true
                grabFocus(if (live != null) watch else followReq)
            }
        }
        // clips are fetched the first time the tab is opened
        LaunchedEffect(showClips) {
            if (showClips && clips == null && !clipsFailed) loadMoreClips()
        }
        // the login dialog takes the focus; when it closes the focus goes back to "Seguir"
        var asked by remember { mutableStateOf(false) }
        LaunchedEffect(askLogin) {
            if (askLogin) asked = true else if (asked) {
                asked = false
                grabFocus(followReq)
            }
        }
        // after "Carregar mais" (or "Tentar de novo"): scroll to the first new card and put the focus on it,
        // so it is obvious that something arrived. The grid's item 0 is the header.
        val cardCount = if (showClips) clips.orEmpty().size else videos.size
        LaunchedEffect(cardCount, focusIdx) {
            if (focusIdx in 0 until cardCount) {
                gridState.animateScrollToItem(1 + focusIdx)
                grabFocus(newCard)
                focusIdx = -1
            }
        }

        val canFocusBehind = !askLogin
        val listFailed = if (showClips) clipsFailed && clips.isNullOrEmpty() else videosFailed && videos.isEmpty()

        Box(Modifier.fillMaxSize().background(Pulso.Background)) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(start = Pulso.Dp.safeH, end = Pulso.Dp.safeH, top = Pulso.Dp.safeV, bottom = 90.dp),
                // while the login dialog is open nothing behind it can take the focus
                modifier = Modifier.fillMaxSize().focusProperties { canFocus = canFocusBehind },
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
                                    user?.followerCount?.let { compactCount(it) + " seguidores" },
                                    live?.gameName?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(meta, color = Pulso.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PulsoPill(
                                        "Assistir ao vivo", primary = true, enabled = live != null, height = 36.dp, requester = watch,
                                        modifier = Modifier.onFocusHint({ focusKind = it }, "watch"),
                                    ) {
                                        live?.let {
                                            startActivity(
                                                Intent(this@PulsoChannelActivity, PulsoPlayerActivity::class.java)
                                                    .putExtra(PulsoPlayerActivity.EXTRA_STREAM, it)
                                            )
                                        }
                                    }
                                    PulsoPill(
                                        if (following == true) "Seguindo" else "Seguir", height = 36.dp, requester = followReq,
                                        modifier = Modifier.onFocusHint({ focusKind = it }, "follow"),
                                    ) {
                                        if (!isLoggedIn()) askLogin = true else toggleFollow(following == true, channelId, name, live)
                                    }
                                }
                            }
                        }
                        // only when the live query worked: a network error must not look like "offline"
                        if (!loading && liveChecked && live == null) {
                            OfflineBanner(name, user?.lastBroadcast, below = !showClips && videos.isNotEmpty())
                        }
                        Row(Modifier.padding(top = 24.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PulsoPill(
                                "Vídeos recentes", primary = !showClips, height = 36.dp,
                                modifier = Modifier.onFocusHint({ focusKind = it }, "videos"),
                            ) { showClips = false }
                            PulsoPill(
                                "Clipes", primary = showClips, height = 36.dp,
                                modifier = Modifier.onFocusHint({ focusKind = it }, "clips"),
                            ) { showClips = true }
                        }
                        when {
                            loading -> PulsoScreenMessage("Carregando…", Modifier.padding(top = 24.dp))
                            // a failed first page: say so and offer to try again (the button stays while it retries)
                            listFailed || retrying -> {
                                PulsoScreenMessage(
                                    if (retrying) "Carregando…"
                                    else if (showClips) "Não foi possível carregar os clipes" else "Não foi possível carregar os vídeos",
                                    Modifier.padding(top = 24.dp),
                                )
                                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                                    PulsoPill(
                                        if (retrying) "Carregando…" else "Tentar de novo", primary = true, height = 36.dp,
                                        modifier = Modifier.onFocusHint({ focusKind = it }, "retry"),
                                    ) {
                                        if (!retrying) scope.launch {
                                            retrying = true
                                            if (showClips) {
                                                clipsSource = null
                                                loadMoreClips()
                                            } else {
                                                videosSource = null
                                                loadMoreVideos()
                                            }
                                            retrying = false
                                            val now = if (showClips) clips.orEmpty().size else videos.size
                                            focusIdx = if (now > 0) 0 else -1 // the first card, so the focus is not lost
                                        }
                                    }
                                }
                            }
                            showClips && clips == null -> PulsoScreenMessage("Carregando…", Modifier.padding(top = 24.dp))
                            showClips && clips.orEmpty().isEmpty() -> PulsoScreenMessage("Nenhum clipe", Modifier.padding(top = 24.dp))
                            !showClips && videos.isEmpty() -> PulsoScreenMessage("Nenhum vídeo recente", Modifier.padding(top = 24.dp))
                        }
                    }
                }
                if (showClips) {
                    itemsIndexed(clips.orEmpty()) { i, c ->
                        PulsoCard(
                            modifier = Modifier.onFocusHint({ focusKind = it }, "clip"),
                            focusRequester = if (i == focusIdx) newCard else null,
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
                    // right after the last card, so it is the next thing the D-pad reaches
                    if (clipsMore && !clips.isNullOrEmpty()) {
                        item(key = "more-clips") {
                            PulsoLoadMoreCard(
                                loading = loadingClips, failed = clipsFailed, hint = "mais clipes",
                                modifier = Modifier.onFocusHint({ focusKind = it }, "more"),
                            ) {
                                val size = clips.orEmpty().size
                                focusIdx = size // the first of the new cards
                                scope.launch {
                                    loadMoreClips()
                                    // nothing new: a failure keeps the card (and the focus on it); the end of the
                                    // list hides it, so the focus moves to the last card first
                                    val now = clips.orEmpty().size
                                    if (focusIdx >= now) focusIdx = if (clipsMore) -1 else now - 1
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(videos) { i, v ->
                        PulsoCard(
                            modifier = Modifier.onFocusHint({ focusKind = it }, "video"),
                            focusRequester = if (i == focusIdx) newCard else null,
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
                    if (videosMore && videos.isNotEmpty()) {
                        item(key = "more-videos") {
                            PulsoLoadMoreCard(
                                loading = loadingVideos, failed = videosFailed, hint = "mais vídeos",
                                modifier = Modifier.onFocusHint({ focusKind = it }, "more"),
                            ) {
                                focusIdx = videos.size // the first of the new cards
                                scope.launch {
                                    loadMoreVideos()
                                    if (focusIdx >= videos.size) focusIdx = if (videosMore) -1 else videos.size - 1
                                }
                            }
                        }
                    }
                }
            }
            PulsoOkHint(
                hintFor(focusKind, following, if (showClips) clipsFailed else videosFailed),
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
    private fun OfflineBanner(name: String, lastBroadcast: String?, below: Boolean) {
        val ago = timeAgo(lastBroadcast)
        // "abaixo" only when the recent videos really are listed under the banner
        val line = when {
            ago.isNotBlank() && below -> "Última transmissão $ago · veja os vídeos recentes abaixo"
            ago.isNotBlank() -> "Última transmissão $ago"
            below -> "Veja os vídeos recentes abaixo"
            else -> null
        }
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
                    if (line != null) {
                        Text(
                            line,
                            color = Pulso.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
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
        LaunchedEffect(Unit) { grabFocus(first) }
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
                // a group of its own: the D-pad stays between these two buttons
                Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
