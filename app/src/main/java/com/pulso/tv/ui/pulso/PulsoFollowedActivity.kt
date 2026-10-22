package com.pulso.tv.ui.pulso

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.PagingSource
import com.pulso.tv.PulsoApp
import com.pulso.tv.model.ui.User
import com.pulso.tv.repository.datasource.FollowedChannelsDataSource
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every channel the account follows, live or not, in alphabetical order. */
class PulsoFollowedActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    /** Repeated OK on a card must not stack duplicate screens. */
    private val openGuard = OpenGuard()

    override fun onResume() {
        super.onResume()
        resumeTick++ // coming back from a channel: the follow list may have changed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = PulsoFont)) {
                FollowedScreen()
            }
        }
    }

    private suspend fun loadAll(): List<User> = withContext(Dispatchers.IO) {
        val m = (application as PulsoApp).pulsoModule
        val source = FollowedChannelsDataSource(
            sort = "login",
            order = "asc",
            userId = tokenPrefs().getString(C.USER_ID, null),
            localChannelFollowsRepository = m.localChannelFollowsRepository,
            offlineVideosRepository = m.offlineVideosRepository,
            bookmarksRepository = m.bookmarksRepository,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(this@PulsoFollowedActivity, true),
            graphQLRepository = m.graphQLRepository,
            helixHeaders = TwitchApiHelper.getHelixHeaders(this@PulsoFollowedActivity),
            helixRepository = m.helixRepository,
            enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
        )
        val all = mutableListOf<User>()
        var key: Int? = null
        repeat(15) {
            val params: PagingSource.LoadParams<Int> =
                if (key == null) PagingSource.LoadParams.Refresh(null, 100, false)
                else PagingSource.LoadParams.Append(key!!, 100, false)
            when (val r = source.load(params)) {
                is PagingSource.LoadResult.Page -> {
                    all += r.data
                    key = r.nextKey
                    if (key == null) return@withContext all.distinctBy { it.id ?: Any() }
                }
                is PagingSource.LoadResult.Error -> {
                    if (all.isEmpty()) throw r.throwable
                    return@withContext all.distinctBy { it.id ?: Any() }
                }
                else -> return@withContext all.distinctBy { it.id ?: Any() }
            }
        }
        all.distinctBy { it.id ?: Any() }
    }

    @Composable
    private fun FollowedScreen() {
        var channels by remember { mutableStateOf<List<User>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        val firstCard = remember { FocusRequester() }
        val retryButton = remember { FocusRequester() }

        // reloads on resume and on "Tentar de novo"; the list on screen stays while it refreshes
        LaunchedEffect(resumeTick, retry) {
            if (channels.isEmpty()) loading = true
            runCatching { loadAll() }
                .onSuccess { channels = it; error = false }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w("PulsoFollowed", "falha ao carregar", it)
                    if (channels.isEmpty()) error = true
                }
            loading = false
        }

        // focus lands on the first channel, or on the retry button when there is none
        val showRetry = !loading && channels.isEmpty()
        focusWhenReady(firstCard, channels.isNotEmpty(), channels.isNotEmpty())
        focusWhenReady(retryButton, showRetry, showRetry)

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
                        Text("Canais que sigo", color = Pulso.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                loading -> "Carregando canais…"
                                error -> "Não foi possível carregar"
                                channels.isEmpty() -> "Você ainda não segue nenhum canal"
                                else -> "${channels.size} canais"
                            },
                            color = Pulso.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (showRetry) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PulsoPill("Tentar de novo", primary = true, requester = retryButton) { retry++ }
                    }
                }
                itemsIndexed(channels, key = { _, u -> u.id ?: u.login ?: u.hashCode() }) { index, u ->
                    PulsoCard(
                        thumb = u.profileImage,
                        title = u.name ?: u.login.orEmpty(),
                        subtitle = u.login.orEmpty(),
                        live = u.isLive == true,
                        translateSubtitle = false,
                        focusRequester = if (index == 0) firstCard else null,
                    ) {
                        if (!openGuard.allow()) return@PulsoCard
                        val login = u.login ?: return@PulsoCard
                        startActivity(
                            Intent(this@PulsoFollowedActivity, PulsoChannelActivity::class.java)
                                .putExtra(PulsoChannelActivity.EXTRA_LOGIN, login)
                                .putExtra(PulsoChannelActivity.EXTRA_ID, u.id)
                                .putExtra(PulsoChannelActivity.EXTRA_NAME, u.name)
                                .putExtra(PulsoChannelActivity.EXTRA_IMAGE, u.profileImage)
                        )
                    }
                }
            }
            PulsoOkHint(
                "Abrir canal",
                Modifier.align(Alignment.BottomEnd).padding(end = Pulso.Dp.safeH, bottom = Pulso.Dp.safeV),
            )
        }
    }
}
