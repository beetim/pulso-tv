package com.pulso.tv.ui.pulso

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.paging.PagingSource
import com.pulso.tv.PulsoApp
import com.pulso.tv.graphql.type.StreamSort
import com.pulso.tv.model.ui.Stream
import com.pulso.tv.repository.datasource.FollowedStreamsDataSource
import com.pulso.tv.repository.datasource.StreamsDataSource
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TV-first home. Shelves instead of a single grid: followed channels first,
 * then the most watched, then recent ones.
 */
class PulsoHomeActivity : ComponentActivity() {

    private var reload by mutableIntStateOf(0)
    private var resumeTick by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                val loggedIn = remember(reload) { isLoggedIn() }
                val userName = remember(reload) { tokenPrefs().getString(C.USERNAME, null) }

                LaunchedEffect(reload) {
                    loading = true
                    error = null
                    runCatching { loadShelves() }
                        .onSuccess { shelves = it }
                        .onFailure {
                            // runCatching catches CancellationException too, which would
                            // leave the error panel up over data that did load.
                            if (it is CancellationException) throw it
                            Log.e("PulsoHome", "falha ao carregar prateleiras", it)
                            error = "Falha de rede"
                        }
                    loading = false
                }

                // coming back from the player: refresh only the "Continuar assistindo" shelf
                LaunchedEffect(resumeTick) {
                    if (shelves.isEmpty()) return@LaunchedEffect
                    val videos = withContext(Dispatchers.IO) { PulsoHistory.loadResume(applicationContext) }
                    val rest = shelves.filter { it.title != "Continuar assistindo" }
                    shelves = if (videos.isEmpty()) rest else listOf(Shelf("Continuar assistindo", videos = videos)) + rest
                }

                PulsoHome(
                    loading = loading,
                    error = error,
                    shelves = shelves,
                    loggedIn = loggedIn,
                    userName = userName,
                    onRetry = { reload++ },
                    onLogin = {
                        loginLauncher.launch(Intent(this@PulsoHomeActivity, PulsoLoginActivity::class.java))
                    },
                    onLogout = { signOut() },
                    onExit = { finish() },
                    onSettings = {
                        startActivity(Intent(this@PulsoHomeActivity, PulsoSettingsActivity::class.java))
                    },
                    onFollowedChannels = {
                        startActivity(Intent(this@PulsoHomeActivity, PulsoFollowedActivity::class.java))
                    },
                    onOpenVideo = { v ->
                        startActivity(
                            Intent(this@PulsoHomeActivity, PulsoPlayerActivity::class.java)
                                .putExtra(PulsoPlayerActivity.EXTRA_VIDEO, v)
                        )
                    },
                    onOpen = { openChannel(it) },
                    onOpenGame = { g ->
                        startActivity(
                            Intent(this@PulsoHomeActivity, PulsoGameActivity::class.java)
                                .putExtra(PulsoGameActivity.EXTRA_ID, g.id)
                                .putExtra(PulsoGameActivity.EXTRA_SLUG, g.slug)
                                .putExtra(PulsoGameActivity.EXTRA_NAME, g.name)
                        )
                    },
                    onSearch = {
                        startActivity(Intent(this@PulsoHomeActivity, PulsoSearchActivity::class.java))
                    },
                )
            }
        }
    }

    /** Same fields the stock login clears before a new sign-in. */
    private fun signOut() {
        tokenPrefs().edit()
            .putString(C.TOKEN, null)
            .putString(C.GQL_HEADERS, null)
            .putString(C.GQL_TOKEN2, null)
            .putString(C.GQL_TOKEN_WEB, null)
            .putString(C.USER_ID, null)
            .putString(C.USERNAME, null)
            // the stock login also resets this; without it an unexpired integrity
            // token would keep suppressing the integrity flow after signing out
            .putLong(C.INTEGRITY_EXPIRATION, 0)
            .apply()
        TwitchApiHelper.checkedValidation = false
        Toast.makeText(this, tr("Você saiu da conta"), Toast.LENGTH_SHORT).show()
        reload++
    }

    private fun isLoggedIn(): Boolean =
        !tokenPrefs().getString(C.USERNAME, null).isNullOrBlank()

    private suspend fun loadShelves(): List<Shelf> = withContext(Dispatchers.IO) {
        val context = applicationContext
        val module = (application as PulsoApp).pulsoModule
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(context)
        val gqlHeadersAuth = TwitchApiHelper.getGQLHeaders(context, true)
        val helixHeaders = TwitchApiHelper.getHelixHeaders(context)
        val integrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false)
        val network = context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)

        suspend fun streamsPage(sort: StreamSort, sortName: String, local: Boolean = false): List<Stream> {
            val source = StreamsDataSource(
                gqlQueryLanguages = if (local) PulsoRegion.queryLanguages else null,
                gqlQuerySort = sort,
                gqlLanguages = if (local) PulsoRegion.rawLanguages else null,
                gqlSort = sortName,
                tags = null,
                gqlHeaders = gqlHeaders,
                graphQLRepository = module.graphQLRepository,
                helixHeaders = helixHeaders,
                helixRepository = module.helixRepository,
                enableIntegrity = integrity,
                networkLibrary = network,
            )
            val page = source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
            )
            // load() does not throw: it hands back LoadResult.Error. Swallowing it here
            // would show "no live streams" whenever the network is down, with no retry.
            return when (page) {
                is PagingSource.LoadResult.Page -> page.data
                is PagingSource.LoadResult.Error -> throw page.throwable
                else -> emptyList()
            }
        }

        val shelves = mutableListOf<Shelf>()

        // recorded videos the user stopped in the middle of
        PulsoHistory.loadResume(context).takeIf { it.isNotEmpty() }?.let {
            shelves += Shelf("Continuar assistindo", videos = it)
        }

        if (isLoggedIn()) {
            runCatching {
                val source = FollowedStreamsDataSource(
                    userId = tokenPrefs().getString(C.USER_ID, null),
                    localChannelFollowsRepository = module.localChannelFollowsRepository,
                    gqlHeaders = gqlHeadersAuth,
                    graphQLRepository = module.graphQLRepository,
                    helixHeaders = helixHeaders,
                    helixRepository = module.helixRepository,
                    enableIntegrity = integrity,
                    networkLibrary = network,
                )
                val page = source.load(
                    PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
                )
                if (page is PagingSource.LoadResult.Page) page.data else emptyList()
            }.onSuccess { if (it.isNotEmpty()) shelves += Shelf("Seguindo", it) }
                .onFailure { Log.w("PulsoHome", "seguindo falhou: ${it.message}") }
        }

        runCatching {
            val source = com.pulso.tv.repository.datasource.GamesDataSource(
                tags = null,
                gqlHeaders = gqlHeaders,
                graphQLRepository = module.graphQLRepository,
                helixHeaders = helixHeaders,
                helixRepository = module.helixRepository,
                enableIntegrity = integrity,
                networkLibrary = network,
            )
            val page = source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
            )
            if (page is PagingSource.LoadResult.Page) page.data else emptyList()
        }.onSuccess { if (it.isNotEmpty()) shelves += Shelf("Jogos", games = it) }
            .onFailure { Log.w("PulsoHome", "jogos falhou: ${it.message}") }

        shelves += Shelf("Mais vistos", streamsPage(StreamSort.VIEWER_COUNT, "VIEWER_COUNT"))
        // recommendations come from the user's own language; any language if that is empty
        val recommended = runCatching { streamsPage(StreamSort.RECENT, "RECENT", local = true) }
            .getOrDefault(emptyList())
            .ifEmpty { streamsPage(StreamSort.RECENT, "RECENT") }
        shelves += Shelf("Recomendados", recommended)

        shelves.filter { it.streams.isNotEmpty() || it.games.isNotEmpty() || it.videos.isNotEmpty() }
    }

    private fun openChannel(stream: Stream) {
        val login = stream.channelLogin
        if (login.isNullOrBlank()) return
        val intent = Intent(this, PulsoPlayerActivity::class.java)
            .putExtra(PulsoPlayerActivity.EXTRA_STREAM, stream)
        runCatching { startActivity(intent) }
            .onFailure { Log.w("PulsoHome", "nao consegui abrir o canal $login", it) }
    }
}
