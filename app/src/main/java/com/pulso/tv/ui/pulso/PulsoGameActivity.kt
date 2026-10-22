package com.pulso.tv.ui.pulso

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.PagingSource
import com.pulso.tv.PulsoApp
import com.pulso.tv.graphql.type.StreamSort
import com.pulso.tv.model.ui.Stream
import com.pulso.tv.repository.datasource.GameStreamsDataSource
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A game (League of Legends, Counter-Strike…) and the channels streaming it now:
 * first a shelf with channels in the user's language (ten at a time, "Carregar mais"
 * adds ten more), then "Outros canais" with the biggest ones in any language.
 */
class PulsoGameActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra(EXTRA_ID)
        val slug = intent.getStringExtra(EXTRA_SLUG)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                GameScreen(id, slug, name)
            }
        }
    }

    /** [local] = only the user's own language; otherwise every language. */
    private fun makeSource(id: String?, slug: String?, name: String, local: Boolean): GameStreamsDataSource {
        val m = (application as PulsoApp).pulsoModule
        return GameStreamsDataSource(
            gameId = id,
            gameSlug = slug,
            gameName = name,
            gqlQueryLanguages = if (local) PulsoRegion.queryLanguages else null,
            gqlQuerySort = StreamSort.VIEWER_COUNT,
            gqlLanguages = if (local) PulsoRegion.rawLanguages else null,
            gqlSort = "VIEWER_COUNT",
            tags = null,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(this@PulsoGameActivity),
            graphQLRepository = m.graphQLRepository,
            helixHeaders = TwitchApiHelper.getHelixHeaders(this@PulsoGameActivity),
            helixRepository = m.helixRepository,
            enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
        )
    }

    /** One page of channels; [key] is the previous page's nextKey (null = first page). */
    private suspend fun loadPage(
        source: GameStreamsDataSource, key: Int?, size: Int,
    ): PagingSource.LoadResult.Page<Int, Stream> = withContext(Dispatchers.IO) {
        val params: PagingSource.LoadParams<Int> = if (key == null) {
            PagingSource.LoadParams.Refresh(key = null, loadSize = size, placeholdersEnabled = false)
        } else {
            PagingSource.LoadParams.Append(key = key, loadSize = size, placeholdersEnabled = false)
        }
        when (val result = source.load(params)) {
            is PagingSource.LoadResult.Page -> result
            is PagingSource.LoadResult.Error -> throw result.throwable
            else -> throw IllegalStateException("resultado inesperado")
        }
    }

    @Composable
    private fun GameScreen(id: String?, slug: String?, name: String) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val hasLocal = remember { PulsoRegion.queryLanguages != null }
        val localSource = remember { makeSource(id, slug, name, local = true) }
        val globalSource = remember { makeSource(id, slug, name, local = false) }

        var local by remember { mutableStateOf<List<Stream>>(emptyList()) }
        var localKey by remember { mutableStateOf<Int?>(null) }
        var localMore by remember { mutableStateOf(hasLocal) }
        var loadingLocal by remember { mutableStateOf(false) }

        var others by remember { mutableStateOf<List<Stream>>(emptyList()) }
        var othersKey by remember { mutableStateOf<Int?>(null) }
        var othersMore by remember { mutableStateOf(true) }
        var loadingOthers by remember { mutableStateOf(false) }

        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        // ten more channels in the user's language, appended to the shelf
        suspend fun loadMoreLocal() {
            if (loadingLocal || !localMore) return
            loadingLocal = true
            runCatching { loadPage(localSource, localKey, 10) }
                .onSuccess { page ->
                    local = (local + page.data).distinctBy { it.id ?: Any() }
                    localKey = page.nextKey
                    if (page.nextKey == null || page.data.isEmpty()) localMore = false
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    localMore = false
                }
            loadingLocal = false
        }

        // the biggest channels in any language, minus those already on the shelf
        suspend fun loadMoreOthers() {
            if (loadingOthers || !othersMore) return
            loadingOthers = true
            runCatching { loadPage(globalSource, othersKey, 30) }
                .onSuccess { page ->
                    val shown = local.mapNotNull { it.id }.toSet()
                    others = (others + page.data.filter { it.id !in shown }).distinctBy { it.id ?: Any() }
                    othersKey = page.nextKey
                    if (page.nextKey == null || page.data.isEmpty()) othersMore = false
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (others.isEmpty() && local.isEmpty()) error = it.message ?: "erro"
                    othersMore = false
                }
            loadingOthers = false
        }

        LaunchedEffect(Unit) {
            loadMoreLocal()
            loadMoreOthers()
            loading = false
        }

        // after "Carregar mais": scroll to the first new card and put the focus on it,
        // so it is obvious that something arrived
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        val newCard = remember { androidx.compose.ui.focus.FocusRequester() }
        var focusIdx by remember { mutableStateOf(-1) }
        LaunchedEffect(local.size, focusIdx) {
            if (focusIdx in 0 until local.size) {
                rowState.animateScrollToItem(focusIdx)
                repeat(10) {
                    if (runCatching { newCard.requestFocus() }.getOrDefault(false)) return@repeat
                    androidx.compose.runtime.withFrameNanos { }
                }
                focusIdx = -1
            }
        }

        val total = local.size + others.size
        val localTitle = if (PulsoRegion.code == "PT") "Canais em português" else "Canais no seu idioma"

        Box(Modifier.fillMaxSize().background(Pulso.Background)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(
                    start = Pulso.Dp.safeH, end = Pulso.Dp.safeH, top = Pulso.Dp.safeV, bottom = 90.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text(name, color = Pulso.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                loading -> "Carregando canais…"
                                error != null -> "Não foi possível carregar: $error"
                                total == 0 -> "Ninguém está transmitindo agora"
                                else -> "$total canais ao vivo agora"
                            },
                            color = Pulso.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                if (local.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "$localTitle · ${local.size}", color = Pulso.TextPrimary, fontSize = Pulso.Sp.shelfTitle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(Pulso.Dp.gap)) {
                            itemsIndexed(local, key = { _, s -> s.id ?: s.hashCode() }) { i, s ->
                                ChannelCard(s, if (i == focusIdx) newCard else null)
                            }
                            // right after the last card, so it is the next thing the D-pad reaches
                            if (localMore) {
                                item(key = "load-more") {
                                    LoadMoreCard(loadingLocal) {
                                        focusIdx = local.size // the first of the new cards
                                        scope.launch {
                                            loadMoreLocal()
                                            if (focusIdx >= local.size) focusIdx = -1 // nothing new arrived
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (others.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Outros canais", color = Pulso.TextPrimary, fontSize = Pulso.Sp.shelfTitle,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    itemsIndexed(others, key = { i, s -> s.id ?: "o$i" }) { i, s ->
                        // close to the end of the list: fetch the next page
                        if (i >= others.size - 6) LaunchedEffect(others.size) { loadMoreOthers() }
                        ChannelCard(s)
                    }
                }
            }
            PulsoOkHint(
                "Assistir ao canal",
                Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(end = Pulso.Dp.safeH, bottom = Pulso.Dp.safeV),
            )
        }
    }

    /** Card at the end of a shelf: same size as a channel card, "+" and the label. */
    @Composable
    private fun LoadMoreCard(loading: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        val latestClick by androidx.compose.runtime.rememberUpdatedState(onClick)
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(Pulso.Dp.cardRadius)
        Box(
            Modifier
                .width(Pulso.Dp.cardWidth)
                .height(Pulso.Dp.cardHeight)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { e ->
                    if (!loading && e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                        latestClick(); true
                    } else false
                }
                .pointerInput(Unit) { detectTapGestures { if (!loading) latestClick() } }
                .then(if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape) else Modifier)
                .clip(shape)
                .background(if (focused) Pulso.SurfaceRaised else Pulso.Surface),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(if (loading) "…" else "+", color = Pulso.Yellow, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (loading) "Carregando…" else "Carregar mais",
                    color = Pulso.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                if (!loading) Text("mais 10 canais", color = Pulso.TextSecondary, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ChannelCard(s: Stream, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
        PulsoCard(
            focusRequester = focusRequester,
            thumb = s.thumbnail,
            title = s.title.orEmpty(),
            subtitle = s.channelName ?: s.channelLogin.orEmpty(),
            live = true,
            bottomStart = compactCount(s.viewerCount ?: 0) + " espectadores",
        ) {
            startActivity(
                Intent(this@PulsoGameActivity, PulsoPlayerActivity::class.java)
                    .putExtra(PulsoPlayerActivity.EXTRA_STREAM, s)
            )
        }
    }

    companion object {
        const val EXTRA_ID = "pulso_game_id"
        const val EXTRA_SLUG = "pulso_game_slug"
        const val EXTRA_NAME = "pulso_game_name"
    }
}
