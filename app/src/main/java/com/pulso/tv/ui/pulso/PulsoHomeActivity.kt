package com.pulso.tv.ui.pulso

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

    /** When the shelves were last fetched; the Home refreshes itself when it comes back after a while. */
    private var lastLoadAt = 0L

    /** Repeated OK on a card must not stack duplicate screens. */
    private val openGuard = OpenGuard()

    override fun onResume() {
        super.onResume()
        PulsoUpdater.homeVisible = true
        resumeTick++
        PulsoUpdater.checkIfDue(applicationContext)
        checkSession()
    }

    /** Renews an expired Twitch token in the background; if that is impossible, asks for a new sign-in. */
    private fun checkSession() {
        if (!isLoggedIn()) return
        lifecycleScope.launch {
            val alive = withContext(Dispatchers.IO) { PulsoSession.ensureValid(application as PulsoApp) }
            if (!alive) signOut(expired = true)
        }
    }

    override fun onPause() {
        PulsoUpdater.homeVisible = false
        super.onPause()
    }

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    runCatching { loadShelves(shelves, silent = false) }
                        .onSuccess { shelves = it; lastLoadAt = SystemClock.elapsedRealtime() }
                        .onFailure {
                            // runCatching catches CancellationException too, which would
                            // leave the error panel up over data that did load.
                            if (it is CancellationException) throw it
                            Log.e("PulsoHome", "falha ao carregar prateleiras", it)
                            error = "Falha de rede"
                        }
                    loading = false
                }

                // coming back from the player: refresh only the "Continuar assistindo" shelf...
                LaunchedEffect(resumeTick) {
                    if (shelves.isEmpty()) return@LaunchedEffect
                    val videos = withContext(Dispatchers.IO) { PulsoHistory.loadResume(applicationContext) }
                    val rest = shelves.filter { it.title != "Continuar assistindo" }
                    shelves = if (videos.isEmpty()) rest else listOf(Shelf("Continuar assistindo", videos = videos)) + rest

                    // ...and reload everything quietly when the data is old. No loading flag, no
                    // error panel: the focus and scroll stay where they are, the cards just update.
                    if (!loading && SystemClock.elapsedRealtime() - lastLoadAt > STALE_MS) {
                        runCatching { loadShelves(shelves, silent = true) }
                            .onSuccess { shelves = it; lastLoadAt = SystemClock.elapsedRealtime() }
                            .onFailure {
                                if (it is CancellationException) throw it
                                Log.w("PulsoHome", "atualização silenciosa falhou: ${it.message}")
                            }
                    }
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
                        if (openGuard.allow()) startActivity(Intent(this@PulsoHomeActivity, PulsoSettingsActivity::class.java))
                    },
                    onFollowedChannels = {
                        if (openGuard.allow()) startActivity(Intent(this@PulsoHomeActivity, PulsoFollowedActivity::class.java))
                    },
                    onOpenVideo = { v ->
                        if (openGuard.allow()) startActivity(
                            Intent(this@PulsoHomeActivity, PulsoPlayerActivity::class.java)
                                .putExtra(PulsoPlayerActivity.EXTRA_VIDEO, v)
                        )
                    },
                    onOpen = { openChannel(it) },
                    onOpenGame = { g ->
                        if (openGuard.allow()) startActivity(
                            Intent(this@PulsoHomeActivity, PulsoGameActivity::class.java)
                                .putExtra(PulsoGameActivity.EXTRA_ID, g.id)
                                .putExtra(PulsoGameActivity.EXTRA_SLUG, g.slug)
                                .putExtra(PulsoGameActivity.EXTRA_NAME, g.name)
                        )
                    },
                    onSearch = {
                        if (openGuard.allow()) startActivity(Intent(this@PulsoHomeActivity, PulsoSearchActivity::class.java))
                    },
                )
            }
        }
    }

    /** Same fields the stock login clears before a new sign-in. */
    private fun signOut(expired: Boolean = false) {
        PulsoSession.clear(this)
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
        Toast.makeText(
            this,
            tr(if (expired) "Sua sessão expirou. Entre de novo." else "Você saiu da conta"),
            Toast.LENGTH_LONG
        ).show()
        reload++
    }

    private fun isLoggedIn(): Boolean =
        !tokenPrefs().getString(C.USERNAME, null).isNullOrBlank()

    /** load() does not throw: it hands back LoadResult.Error, which must not look like "no data". */
    private fun <T : Any> PagingSource.LoadResult<Int, T>.dataOrThrow(): List<T> = when (this) {
        is PagingSource.LoadResult.Page -> data
        is PagingSource.LoadResult.Error -> throw throwable
        else -> emptyList()
    }

    /**
     * Every network shelf is fetched on its own: one failing shelf (an expired token on
     * "Seguindo", a hiccup on "Jogos") only drops that shelf — or keeps the one from
     * [previous] — and the Home is an error only when all of them failed.
     */
    private suspend fun loadShelves(previous: List<Shelf>, silent: Boolean): List<Shelf> = withContext(Dispatchers.IO) {
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
            // an Error here would otherwise show "no live streams" whenever the network is down
            return source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
            ).dataOrThrow()
        }

        var attempts = 0
        var failures = 0
        var firstError: Throwable? = null
        suspend fun <T> attempt(name: String, block: suspend () -> T): T? {
            attempts++
            return try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                if (firstError == null) firstError = e
                Log.w("PulsoHome", "$name falhou: ${e.message}")
                null
            }
        }

        val shelves = mutableListOf<Shelf>()
        // a shelf that failed to refresh keeps what the screen already had
        fun keepOld(title: String) {
            previous.firstOrNull { it.title == title }?.let { shelves += it }
        }

        // recorded videos the user stopped in the middle of
        PulsoHistory.loadResume(context).takeIf { it.isNotEmpty() }?.let {
            shelves += Shelf("Continuar assistindo", videos = it)
        }

        var followedFailed = false
        if (isLoggedIn()) {
            val followed = attempt("seguindo") {
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
                source.load(
                    PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
                ).dataOrThrow()
            }
            if (followed == null) {
                followedFailed = true
                keepOld("Seguindo")
            } else if (followed.isNotEmpty()) {
                shelves += Shelf("Seguindo", followed)
            }
        }

        val games = attempt("jogos") {
            val source = com.pulso.tv.repository.datasource.GamesDataSource(
                tags = null,
                gqlHeaders = gqlHeaders,
                graphQLRepository = module.graphQLRepository,
                helixHeaders = helixHeaders,
                helixRepository = module.helixRepository,
                enableIntegrity = integrity,
                networkLibrary = network,
            )
            source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
            ).dataOrThrow()
        }
        if (games == null) keepOld("Jogos") else if (games.isNotEmpty()) shelves += Shelf("Jogos", games = games)

        val top = attempt("mais vistos") { streamsPage(StreamSort.VIEWER_COUNT, "VIEWER_COUNT") }
        if (top == null) keepOld("Mais vistos") else if (top.isNotEmpty()) shelves += Shelf("Mais vistos", top)

        // recommendations come from the user's own language; any language if that is empty
        val recommended = attempt("recomendados") {
            val local = try {
                streamsPage(StreamSort.RECENT, "RECENT", local = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            local.ifEmpty { streamsPage(StreamSort.RECENT, "RECENT") }
        }
        if (recommended == null) keepOld("Recomendados") else if (recommended.isNotEmpty()) shelves += Shelf("Recomendados", recommended)

        // an error only when there is nothing to show because everything failed
        if (attempts > 0 && failures == attempts) throw firstError ?: IllegalStateException("falha ao carregar")

        if (followedFailed && !silent) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, tr("Não foi possível carregar os canais que você segue"), Toast.LENGTH_LONG).show()
            }
        }

        shelves.filter { it.streams.isNotEmpty() || it.games.isNotEmpty() || it.videos.isNotEmpty() }
    }

    private fun openChannel(stream: Stream) {
        val login = stream.channelLogin
        if (login.isNullOrBlank()) return
        if (!openGuard.allow()) return
        val intent = Intent(this, PulsoPlayerActivity::class.java)
            .putExtra(PulsoPlayerActivity.EXTRA_STREAM, stream)
        runCatching { startActivity(intent) }
            .onFailure { Log.w("PulsoHome", "nao consegui abrir o canal $login", it) }
    }

    private companion object {
        /** Older than this when the Home comes back to the front: reload the shelves. */
        const val STALE_MS = 5 * 60 * 1000L
    }
}
