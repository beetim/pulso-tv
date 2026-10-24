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
import androidx.compose.runtime.derivedStateOf
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
        var localFailed by remember { mutableStateOf(false) }

        var others by remember { mutableStateOf<List<Stream>>(emptyList()) }
        var othersKey by remember { mutableStateOf<Int?>(null) }
        var othersMore by remember { mutableStateOf(true) }
        var loadingOthers by remember { mutableStateOf(false) }
        var othersFailed by remember { mutableStateOf(false) }

        var loading by remember { mutableStateOf(true) }
        var retrying by remember { mutableStateOf(false) }
        var okHint by remember { mutableStateOf("Assistir ao canal") }

        // ten more channels in the user's language, appended to the shelf; a failure keeps
        // the "Carregar mais" card (as "Tentar de novo") instead of hiding it
        suspend fun loadMoreLocal() {
            if (loadingLocal || !localMore) return
            loadingLocal = true
            localFailed = false
            try {
                val page = loadPage(localSource, localKey, 10)
                local = (local + page.data).distinctBy { it.id ?: Any() }
                localKey = page.nextKey
                if (page.nextKey == null || page.data.isEmpty()) localMore = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localFailed = true
            } finally {
                loadingLocal = false
            }
        }

        // the biggest channels in any language (the ones already on the shelf are filtered
        // out when drawing, see shownOthers)
        suspend fun loadMoreOthers() {
            if (loadingOthers || !othersMore) return
            loadingOthers = true
            othersFailed = false
            try {
                val page = loadPage(globalSource, othersKey, 30)
                others = (others + page.data).distinctBy { it.id ?: Any() }
                othersKey = page.nextKey
                if (page.nextKey == null || page.data.isEmpty()) othersMore = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                othersFailed = true
            } finally {
                loadingOthers = false
            }
        }

        // a channel on the user's shelf never shows again under "Outros canais", even when
        // it reaches the shelf after the other list was loaded
        val shownOthers by remember {
            derivedStateOf {
                val shelf = local.mapNotNull { it.id }.toSet()
                others.filter { it.id == null || it.id !in shelf }
            }
        }

        LaunchedEffect(Unit) {
            loadMoreLocal()
            loadMoreOthers()
            loading = false
        }

        // puts the focus on a card; tries a few frames, until the card is on screen
        suspend fun grabFocus(requester: androidx.compose.ui.focus.FocusRequester) {
            for (attempt in 0 until 20) {
                if (runCatching { requester.requestFocus() }.getOrDefault(false)) break
                androidx.compose.runtime.withFrameNanos { }
            }
        }

        // after "Carregar mais": scroll to the first new card and put the focus on it,
        // so it is obvious that something arrived
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        val newCard = remember { androidx.compose.ui.focus.FocusRequester() }
        var focusIdx by remember { mutableStateOf(-1) }
        LaunchedEffect(local.size, focusIdx) {
            if (focusIdx in 0 until local.size) {
                rowState.animateScrollToItem(focusIdx)
                grabFocus(newCard)
                focusIdx = -1
            }
        }
        // same for "Tentar de novo" under "Outros canais": the first new card (or the last one)
        val newOther = remember { androidx.compose.ui.focus.FocusRequester() }
        var focusOtherIdx by remember { mutableStateOf(-1) }
        LaunchedEffect(shownOthers.size, focusOtherIdx) {
            if (focusOtherIdx in 0 until shownOthers.size) {
                grabFocus(newOther)
                focusOtherIdx = -1
            }
        }

        // nothing is focused when the screen opens: first card (or "Tentar de novo" when it failed)
        val firstCard = remember { androidx.compose.ui.focus.FocusRequester() }
        val retry = remember { androidx.compose.ui.focus.FocusRequester() }
        val hasCards = local.isNotEmpty() || others.isNotEmpty()
        val initialFailed = !loading && ((local.isEmpty() && localFailed) || (others.isEmpty() && othersFailed))
        val retryShown = initialFailed || retrying
        LaunchedEffect(!loading && hasCards, retryShown) { if (!loading && hasCards && !retryShown) grabFocus(firstCard) }
        LaunchedEffect(initialFailed) { if (initialFailed) grabFocus(retry) }

        val total = local.size + shownOthers.size
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
                                retryShown -> "Não foi possível carregar os canais agora"
                                total == 0 -> "Ninguém está transmitindo agora"
                                // only the loaded ones are counted, so no number while there are more pages
                                localMore || othersMore -> "Ao vivo agora"
                                else -> "$total canais ao vivo agora"
                            },
                            color = Pulso.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                        if (retryShown) {
                            PulsoPill(
                                if (retrying) "Carregando…" else "Tentar de novo", primary = true, height = 36.dp,
                                requester = retry,
                                modifier = Modifier.padding(top = 12.dp).onFocusHint({ okHint = it }, "Tentar de novo"),
                            ) {
                                if (!retrying) scope.launch {
                                    retrying = true
                                    if (local.isEmpty() && localFailed) loadMoreLocal()
                                    if (others.isEmpty() && othersFailed) loadMoreOthers()
                                    retrying = false
                                }
                            }
                        }
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
                                ChannelCard(s, if (i == focusIdx) newCard else if (i == 0) firstCard else null) { okHint = it }
                            }
                            // right after the last card, so it is the next thing the D-pad reaches
                            if (localMore) {
                                item(key = "load-more") {
                                    PulsoLoadMoreCard(
                                        loading = loadingLocal, failed = localFailed, hint = "mais 10 canais",
                                        modifier = Modifier.onFocusHint({ okHint = it }, if (localFailed) "Tentar de novo" else "Carregar mais"),
                                    ) {
                                        focusIdx = local.size // the first of the new cards
                                        scope.launch {
                                            loadMoreLocal()
                                            // nothing new: a failure keeps the card (and the focus on it); the end of
                                            // the list hides it, so the focus moves to the last card first
                                            if (focusIdx >= local.size) focusIdx = if (localMore) -1 else local.size - 1
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!loading && hasLocal && !localFailed) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (PulsoRegion.code == "PT") "Nenhum canal em português agora" else "Nenhum canal no seu idioma agora",
                            color = Pulso.TextSecondary, fontSize = Pulso.Sp.meta,
                        )
                    }
                }

                if (shownOthers.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Outros canais", color = Pulso.TextPrimary, fontSize = Pulso.Sp.shelfTitle,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    itemsIndexed(shownOthers, key = { i, s -> s.id ?: "o$i" }) { i, s ->
                        // close to the end of the list: fetch the next page (not after a failure:
                        // the "Tentar de novo" below asks for it)
                        if (i >= shownOthers.size - 6 && !othersFailed) LaunchedEffect(others.size) { loadMoreOthers() }
                        val requester = if (i == focusOtherIdx) newOther else if (i == 0 && local.isEmpty()) firstCard else null
                        ChannelCard(s, requester) { okHint = it }
                    }
                    if (othersFailed) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PulsoPill(
                                if (loadingOthers) "Carregando…" else "Tentar de novo", height = 36.dp,
                                modifier = Modifier.onFocusHint({ okHint = it }, "Tentar de novo"),
                            ) {
                                val before = shownOthers.size
                                if (!loadingOthers) scope.launch {
                                    loadMoreOthers()
                                    // the new cards start where this pill was; none arrived: the last card
                                    if (!othersFailed) {
                                        focusOtherIdx = if (shownOthers.size > before) before else shownOthers.size - 1
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PulsoOkHint(
                okHint,
                Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(end = Pulso.Dp.safeH, bottom = Pulso.Dp.safeV),
            )
        }
    }

    /** Sets the bottom-right OK hint to [text] while the element is focused. */
    private fun Modifier.onFocusHint(set: (String) -> Unit, text: String): Modifier =
        onFocusChanged { if (it.isFocused) set(text) }

    @Composable
    private fun ChannelCard(
        s: Stream,
        focusRequester: androidx.compose.ui.focus.FocusRequester?,
        setHint: (String) -> Unit,
    ) {
        PulsoCard(
            modifier = Modifier.onFocusHint(setHint, "Assistir ao canal"),
            focusRequester = focusRequester,
            thumb = s.thumbnail,
            title = s.title.orEmpty(),
            subtitle = s.channelName ?: s.channelLogin.orEmpty(),
            live = true,
            previewLogin = s.channelLogin,
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

/**
 * Card at the end of a shelf or grid: same size as a content card, "+" and the label.
 * After a failure it stays, as "Tentar de novo", so the focus never has to leave it.
 */
@Composable
internal fun PulsoLoadMoreCard(
    loading: Boolean,
    failed: Boolean,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val latestClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Pulso.Dp.cardRadius)
    Box(
        modifier
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
            Text(
                if (loading) "…" else if (failed) "!" else "+",
                color = Pulso.Yellow, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (loading) "Carregando…" else if (failed) "Tentar de novo" else "Carregar mais",
                color = Pulso.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
            if (!loading) Text(if (failed) "Não foi possível carregar" else hint, color = Pulso.TextSecondary, fontSize = 12.sp)
        }
    }
}
