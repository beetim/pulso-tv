package com.pulso.tv.ui.pulso

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.PagingSource
import com.pulso.tv.PulsoApp
import com.pulso.tv.model.ui.Game
import com.pulso.tv.model.ui.Stream
import com.pulso.tv.model.ui.User
import com.pulso.tv.repository.datasource.SearchChannelsDataSource
import com.pulso.tv.repository.datasource.SearchGamesDataSource
import com.pulso.tv.repository.datasource.SearchStreamsDataSource
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Design 05: search field, tabs (Canais / Ao vivo / Categorias) and a results grid. */
class PulsoSearchActivity : ComponentActivity() {

    /** Repeated OK on a result must not stack duplicate screens. */
    private val openGuard = OpenGuard()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                SearchScreen()
            }
        }
    }

    private suspend fun <T : Any> page(source: PagingSource<Int, T>): List<T> {
        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false))
        // an Error is not "no results": offline would otherwise read "Nenhum resultado"
        return when (result) {
            is PagingSource.LoadResult.Page -> result.data
            is PagingSource.LoadResult.Error -> throw result.throwable
            else -> emptyList()
        }
    }

    private suspend fun searchStreams(query: String): List<Stream> = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            SearchStreamsDataSource(
                query, TwitchApiHelper.getGQLHeaders(this@PulsoSearchActivity), m.graphQLRepository,
                TwitchApiHelper.getHelixHeaders(this@PulsoSearchActivity), m.helixRepository,
                prefs().getBoolean(C.ENABLE_INTEGRITY, false), prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            )
        )
    }

    // ---- recent searches ----------------------------------------------

    private fun loadHistory(): List<String> =
        getSharedPreferences("pulso_search", MODE_PRIVATE).getString("history", "")
            .orEmpty().split('\n').filter { it.isNotBlank() }

    private fun saveHistory(query: String): List<String> {
        val q = query.trim()
        if (q.isBlank()) return loadHistory()
        val list = (listOf(q) + loadHistory().filter { !it.equals(q, ignoreCase = true) }).take(10)
        getSharedPreferences("pulso_search", MODE_PRIVATE).edit().putString("history", list.joinToString("\n")).apply()
        return list
    }

    private fun clearHistory() {
        getSharedPreferences("pulso_search", MODE_PRIVATE).edit().remove("history").apply()
    }

    private suspend fun searchChannels(query: String): List<User> = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            SearchChannelsDataSource(
                query, TwitchApiHelper.getGQLHeaders(this@PulsoSearchActivity), m.graphQLRepository,
                TwitchApiHelper.getHelixHeaders(this@PulsoSearchActivity), m.helixRepository,
                prefs().getBoolean(C.ENABLE_INTEGRITY, false), prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            )
        )
    }

    private suspend fun searchGames(query: String): List<Game> = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        page(
            SearchGamesDataSource(
                query, TwitchApiHelper.getGQLHeaders(this@PulsoSearchActivity), m.graphQLRepository,
                TwitchApiHelper.getHelixHeaders(this@PulsoSearchActivity), m.helixRepository,
                prefs().getBoolean(C.ENABLE_INTEGRITY, false), prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            )
        )
    }

    @Composable
    private fun SearchScreen() {
        var query by remember { mutableStateOf("") }
        var tab by remember { mutableIntStateOf(1) } // 0 Canais · 1 Ao vivo · 2 Categorias
        var streams by remember { mutableStateOf<List<Stream>>(emptyList()) }
        var channels by remember { mutableStateOf<List<User>>(emptyList()) }
        var games by remember { mutableStateOf<List<Game>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var history by remember { mutableStateOf(loadHistory()) }
        val field = remember { FocusRequester() }

        LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

        // Focus has to land somewhere when the focused button disappears (history item
        // picked, history cleared): back on the field, ready to refine the query.
        var refocusField by remember { mutableIntStateOf(0) }
        LaunchedEffect(refocusField) {
            if (refocusField == 0) return@LaunchedEffect
            withFrameNanos { }
            runCatching { field.requestFocus() }
        }

        LaunchedEffect(query, tab) {
            error = null
            if (query.isBlank()) {
                streams = emptyList(); channels = emptyList(); games = emptyList(); loading = false
                return@LaunchedEffect
            }
            // "Buscando…" from the first keystroke: the old results (or a stale error) must
            // not sit on screen during the debounce. A newer effect cancels this one.
            loading = true
            delay(450) // wait for the typing to settle
            try {
                when (tab) {
                    // most followed first, live channels breaking ties
                    0 -> channels = searchChannels(query).sortedWith(
                        compareByDescending<User> { it.followerCount ?: 0 }.thenByDescending { it.isLive == true }
                    )
                    // most watched first
                    1 -> streams = searchStreams(query).sortedByDescending { it.viewerCount ?: 0 }
                    else -> games = searchGames(query)
                }
            } catch (e: CancellationException) {
                // typing again or switching tab cancels this search: not an error to show
                throw e
            } catch (e: Exception) {
                error = e.message ?: "erro na busca"
            }
            loading = false
        }

        Box(Modifier.fillMaxSize().background(Pulso.Background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = Pulso.Dp.safeH).padding(top = Pulso.Dp.safeV)) {
                SearchField(query, { query = it }, field, onSearch = { history = saveHistory(query) })
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Canais", "Ao vivo", "Categorias").forEachIndexed { i, label ->
                        PulsoPill(label, primary = tab == i) { tab = i }
                    }
                }
                Box(Modifier.padding(top = 20.dp).weight(1f)) {
                    when {
                        query.isBlank() -> History(
                            history,
                            onPick = { query = it; refocusField++ },
                            onClear = { clearHistory(); history = emptyList(); refocusField++ },
                        )
                        error != null -> PulsoScreenMessage("Não foi possível buscar: $error", Modifier.padding(top = 80.dp))
                        loading -> PulsoScreenMessage("Buscando…", Modifier.padding(top = 80.dp))
                        else -> Results(tab, streams, channels, games, onOpen = { history = saveHistory(query) }) { game ->
                            startActivity(
                                Intent(this@PulsoSearchActivity, PulsoGameActivity::class.java)
                                    .putExtra(PulsoGameActivity.EXTRA_ID, game.id)
                                    .putExtra(PulsoGameActivity.EXTRA_SLUG, game.slug)
                                    .putExtra(PulsoGameActivity.EXTRA_NAME, game.name)
                            )
                        }
                    }
                }
            }
            PulsoOkHint(
                "OK digitar · baixo para resultados",
                Modifier.align(Alignment.BottomEnd).padding(end = Pulso.Dp.safeH, bottom = Pulso.Dp.safeV),
            )
        }
    }

    @Composable
    private fun Results(
        tab: Int,
        streams: List<Stream>,
        channels: List<User>,
        games: List<Game>,
        onOpen: () -> Unit,
        onGame: (Game) -> Unit,
    ) {
        val empty = when (tab) { 0 -> channels.isEmpty(); 1 -> streams.isEmpty(); else -> games.isEmpty() }
        if (empty) {
            PulsoScreenMessage("Nenhum resultado", Modifier.padding(top = 80.dp))
            return
        }
        LazyVerticalGrid(
            // games are portrait covers: five across
            columns = GridCells.Fixed(if (tab == 2) 5 else 3),
            horizontalArrangement = Arrangement.spacedBy(if (tab == 2) 12.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 80.dp, top = 6.dp, start = 6.dp, end = 6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (tab) {
                0 -> items(channels) { u ->
                    PulsoCard(
                        thumb = u.profileImage,
                        title = u.name ?: u.login.orEmpty(),
                        subtitle = u.followerCount?.let { tr(compactCount(it) + " seguidores") } ?: u.login.orEmpty(),
                        live = u.isLive == true,
                        translateSubtitle = false,
                    ) {
                        if (!openGuard.allow()) return@PulsoCard
                        onOpen()
                        startActivity(
                            Intent(this@PulsoSearchActivity, PulsoChannelActivity::class.java)
                                .putExtra(PulsoChannelActivity.EXTRA_LOGIN, u.login)
                                .putExtra(PulsoChannelActivity.EXTRA_ID, u.id)
                                .putExtra(PulsoChannelActivity.EXTRA_NAME, u.name)
                                .putExtra(PulsoChannelActivity.EXTRA_IMAGE, u.profileImage)
                        )
                    }
                }
                1 -> items(streams) { s ->
                    PulsoCard(
                        thumb = s.thumbnail,
                        title = s.title.orEmpty(),
                        subtitle = playingText(s.channelName ?: s.channelLogin, s.gameName),
                        live = true,
                        bottomStart = (s.viewerCount ?: 0).let { compactCount(it) + " espectadores" },
                        translateSubtitle = false, // playingText is already translated
                    ) {
                        if (!openGuard.allow()) return@PulsoCard
                        onOpen()
                        startActivity(
                            Intent(this@PulsoSearchActivity, PulsoPlayerActivity::class.java)
                                .putExtra(PulsoPlayerActivity.EXTRA_STREAM, s)
                        )
                    }
                }
                else -> items(games) { g ->
                    PulsoGameCard(
                        boxArt = g.boxArt,
                        name = g.name.orEmpty(),
                        viewers = g.viewerCount?.let { compactCount(it) + " espectadores" } ?: "",
                    ) { if (openGuard.allow()) onGame(g) }
                }
            }
        }
    }

    /** Recent searches shown under the field while it is empty. */
    @Composable
    private fun History(items: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
        if (items.isEmpty()) {
            PulsoScreenMessage("Digite para buscar canais, transmissões e categorias", Modifier.padding(top = 80.dp))
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pesquisas recentes", color = Pulso.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            items.forEach { q ->
                // the text is something the person typed: never translated
                PulsoPill(q, height = 40.dp, modifier = Modifier.fillMaxWidth(0.5f), translate = false) { onPick(q) }
            }
            Box(Modifier.height(4.dp))
            PulsoPill("Limpar histórico", height = 32.dp, onClick = onClear)
        }
    }

    @Composable
    private fun SearchField(query: String, onChange: (String) -> Unit, requester: FocusRequester, onSearch: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        val keyboard = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val shape = RoundedCornerShape(10.dp)
        val fg = if (focused) Pulso.Background else Pulso.TextPrimary
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape) else Modifier)
                .background(if (focused) Pulso.TextPrimary else Pulso.Surface, shape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width
                drawCircle(fg, radius = w * 0.34f, center = Offset(w * 0.4f, w * 0.4f), style = Stroke(w * 0.12f))
                drawLine(fg, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.92f, w * 0.92f), w * 0.12f, StrokeCap.Round)
            }
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Buscar", color = if (focused) Color(0xFF4B4B57) else Pulso.TextSecondary, fontSize = 18.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 18.sp, fontFamily = PulsoFont),
                    cursorBrush = SolidColor(Pulso.Yellow),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester)
                        .onFocusChanged { focused = it.isFocused }
                        // A text field uses the arrow keys to move the caret, which traps the
                        // remote's D-pad inside the field. Preview the event first so down
                        // hands the focus to the tab row and then to the results grid.
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                                keyboard?.hide()
                                focusManager.moveFocus(FocusDirection.Down)
                            } else false
                        }
                        .onKeyEvent { e ->
                            // OK on the remote opens the system keyboard / dictation
                            if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                                keyboard?.show(); true
                            } else false
                        },
                )
            }
            if (focused) {
                Text("OK teclado · baixo resultados", color = Color(0xFF4B4B57), fontSize = 13.sp)
            }
        }
    }
}
