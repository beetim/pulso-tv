@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.pulso.tv.ui.pulso

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pulso.tv.model.ui.Stream

/**
 * Design tokens from design/SPEC.md.
 * Artwork is authored on a 1920x1080 canvas; the TV reports 960x540 dp, so every
 * measured value is halved into dp/sp.
 */
object Pulso {
    // pulso.tv brand: yellow = action and selection, cobalt = information (avatars),
    // red = "ao vivo" only. White belongs to the focus ring alone.
    val Yellow = Color(0xFFFFC800)
    val YellowPressed = Color(0xFFD9A700)
    val Cobalt = Color(0xFF3D6BFF)
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF18181B)
    val SurfaceRaised = Color(0xFF26262C)
    val Border = Color(0xFF3A3A3D)
    val TextPrimary = Color(0xFFEFEFF1)
    val TextSecondary = Color(0xFFADADB8)
    val Live = Color(0xFFEB0400)
    val FocusRing = Color(0xFFFFFFFF)

    object Dp {
        val safeH = 48.dp
        val safeV = 27.dp
        val headerHeight = 86.dp
        val cardWidth = 272.dp
        val cardHeight = 229.dp
        val thumbHeight = 153.dp
        val gap = 24.dp
        val cardRadius = 8.dp
        val ringWidth = 3.dp
        val ringGap = 2.dp
        val logo = 28.dp
    }

    object Sp {
        val screenTitle = 22.sp
        val shelfTitle = 16.sp
        val cardTitle = 14.sp
        val channel = 12.sp
        val meta = 12.sp
        val badge = 10.sp
        val viewers = 11.sp
        val nav = 16.sp
    }
}

/** One horizontal shelf: live streams, or games when [games] is filled. */
data class Shelf(
    val title: String,
    val streams: List<Stream> = emptyList(),
    val games: List<com.pulso.tv.model.ui.Game> = emptyList(),
    val videos: List<PulsoHistory.ResumeItem> = emptyList(),
)

@Composable
fun PulsoHome(
    loading: Boolean,
    error: String?,
    shelves: List<Shelf>,
    loggedIn: Boolean,
    userName: String?,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    onOpen: (Stream) -> Unit,
    onOpenGame: (com.pulso.tv.model.ui.Game) -> Unit,
    onOpenVideo: (com.pulso.tv.model.ui.Video) -> Unit,
    onSearch: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit = {},
    onFollowedChannels: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    var profileOpen by remember { mutableStateOf(false) }
    var langOpen by remember { mutableStateOf(false) }
    var exitOpen by remember { mutableStateOf(false) }
    var updateOpen by remember { mutableStateOf(false) }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val update = PulsoUpdater.state as? PulsoUpdater.State.Available
    // Voltar closes an open panel; on the bare Home it asks before leaving the app
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            updateOpen -> updateOpen = false
            profileOpen || langOpen -> { profileOpen = false; langOpen = false }
            exitOpen -> exitOpen = false
            else -> exitOpen = true
        }
    }
    val firstCard = remember { FocusRequester() }
    // keyed on "has content" only: refreshing a shelf must not yank focus back to the top
    focusWhenReady(firstCard, shelves.isNotEmpty(), shelves.isNotEmpty())

    Box(
        Modifier
            .fillMaxSize()
            .background(Pulso.Background)
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                loggedIn = loggedIn, userName = userName, onSearch = onSearch, onLogin = onLogin,
                onProfile = { profileOpen = true }, onLanguage = { langOpen = true },
                updateVersion = update?.version?.takeIf { !PulsoUpdater.dismissed },
                onUpdate = { updateOpen = true },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .focusGroup()
                    .focusRestorer()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp)
            ) {
                if (!loggedIn && shelves.isNotEmpty()) {
                    LoginShelf(onLogin)
                }
                shelves.forEachIndexed { index, shelf ->
                    ShelfRow(
                        shelf = shelf,
                        firstRequester = if (index == 0) firstCard else null,
                        onOpen = onOpen,
                        onOpenGame = onOpenGame,
                        onOpenVideo = onOpenVideo,
                    )
                }
            }
        }

        when {
            loading -> CenterMessage("Carregando…", Modifier.align(Alignment.Center))
            error != null -> ErrorPanel(error, onRetry, Modifier.align(Alignment.Center))
            shelves.isEmpty() -> CenterMessage("Sem transmissões ao vivo", Modifier.align(Alignment.Center))
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(70.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Pulso.Background)))
        )

        HintPill(
            text = "Assistir ao canal",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Pulso.Dp.safeH, bottom = Pulso.Dp.safeV)
        )

        if (profileOpen) {
            ProfilePanel(
                userName = userName.orEmpty(),
                onLogout = { profileOpen = false; onLogout() },
                onFollowed = { profileOpen = false; onFollowedChannels() },
                onSettings = { profileOpen = false; onSettings() },
                onClose = { profileOpen = false },
            )
        }

        if (langOpen) {
            LanguageCard(onClose = { langOpen = false })
        }

        if (exitOpen) {
            ExitCard(onStay = { exitOpen = false }, onExit = onExit)
        }

        if (updateOpen) {
            UpdateCard(
                onUpdate = { PulsoUpdater.downloadAndInstall(appContext) },
                onLater = { PulsoUpdater.dismissed = true; updateOpen = false },
                onClose = { updateOpen = false },
            )
        }
    }
}

/** "Nova versão disponível": release notes plus Atualizar agora / Depois; shows download progress. */
@Composable
private fun UpdateCard(onUpdate: () -> Unit, onLater: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    focusWhenReady(first, true, Unit)
    val state = PulsoUpdater.state
    val info = state as? PulsoUpdater.State.Available
    val busy = state is PulsoUpdater.State.Downloading
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Pulso.Surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 32.dp, vertical = 27.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nova versão disponível", color = Pulso.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "v${info?.version.orEmpty()}  (atual v${com.pulso.tv.BuildConfig.VERSION_NAME})",
                color = Pulso.Yellow, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            // release notes are Markdown: show them as plain bullets
            val notes = info?.notes.orEmpty().lines()
                .map { it.trim().trimStart('#', ' ').replace("**", "").replace("`", "") }
                .filter { it.isNotBlank() }
                .map { if (it.startsWith("- ") || it.startsWith("* ")) "•  " + it.drop(2) else it }
                .take(8).joinToString("\n")
            if (notes.isNotEmpty()) {
                Text("O que há de novo", color = Pulso.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(notes, color = Pulso.TextSecondary, fontSize = 14.sp)
            }
            when (state) {
                is PulsoUpdater.State.Downloading -> Text("Baixando… ${state.percent}%", color = Pulso.TextPrimary, fontSize = 14.sp)
                is PulsoUpdater.State.Failed -> Text(state.reason, color = Pulso.TextSecondary, fontSize = 13.sp)
                else -> {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                PulsoPill("Atualizar agora", primary = true, enabled = !busy, height = 40.dp, requester = first, onClick = onUpdate)
                PulsoPill("Depois", height = 40.dp, onClick = onLater)
            }
        }
    }
}

/** "Sair do Pulso?" — focus starts on the safe choice (stay). */
@Composable
private fun ExitCard(onStay: () -> Unit, onExit: () -> Unit) {
    val stay = remember { FocusRequester() }
    focusWhenReady(stay, true, Unit)
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .pointerInput(Unit) { detectTapGestures { onStay() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Pulso.Surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 32.dp, vertical = 27.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sair do pulso.tv?", color = Pulso.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Você voltará para a tela inicial da TV.", color = Pulso.TextSecondary, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                PulsoPill("Continuar", primary = true, height = 40.dp, requester = stay, onClick = onStay)
                PulsoPill("Sair", height = 40.dp, onClick = onExit)
            }
        }
    }
}

/** Right-hand account panel: who is connected, and how to sign out. */
@Composable
private fun ProfilePanel(
    userName: String,
    onLogout: () -> Unit,
    onFollowed: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    // focus starts on "Fechar" so an accidental OK never signs the user out
    focusWhenReady(closeFocus, true, Unit)
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(380.dp)
                .fillMaxHeight()
                .background(Pulso.Surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 24.dp, vertical = 27.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Perfil", color = Pulso.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(50)).background(Pulso.Cobalt),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        userName.take(1).uppercase(), color = Color.White,
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
                Box(Modifier.width(14.dp))
                Column {
                    Text("@$userName", color = Pulso.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Conectado à Twitch", color = Pulso.TextSecondary, fontSize = 12.sp)
                }
            }
            PulsoPill("Fechar", height = 40.dp, requester = closeFocus, modifier = Modifier.fillMaxWidth(), onClick = onClose)
            PulsoPill("Canais que sigo", height = 40.dp, modifier = Modifier.fillMaxWidth(), onClick = onFollowed)
            PulsoPill("Configurações", height = 40.dp, modifier = Modifier.fillMaxWidth(), onClick = onSettings)
            PulsoPill("Sair da conta", height = 40.dp, modifier = Modifier.fillMaxWidth(), onClick = onLogout)
            Spacer(Modifier.weight(1f))
            ProfileFooter()
        }
    }
}

/** Bottom of the profile panel: brand, version, update status and the legal notes. */
@Composable
private fun ProfileFooter() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val state = PulsoUpdater.state
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pulso.SurfaceRaised))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PulsoLogo(markSize = 28.dp)
        Text(
            "versão ${com.pulso.tv.BuildConfig.VERSION_NAME} (${com.pulso.tv.BuildConfig.VERSION_CODE})",
            color = Pulso.TextSecondary, fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    val status = when {
        !PulsoUpdater.enabled -> "Atualização desativada nesta versão"
        state is PulsoUpdater.State.Checking -> "Verificando…"
        state is PulsoUpdater.State.UpToDate -> "Você está na versão mais recente"
        state is PulsoUpdater.State.Available -> "Nova versão disponível: v${state.version}"
        state is PulsoUpdater.State.Downloading -> "Baixando… ${state.percent}%"
        state is PulsoUpdater.State.Failed -> state.reason
        else -> null
    }
    if (status != null) Text(status, color = Pulso.TextSecondary, fontSize = 12.sp)
    if (PulsoUpdater.enabled) {
        if (state is PulsoUpdater.State.Available) {
            PulsoPill("Atualizar agora", primary = true, height = 40.dp, modifier = Modifier.fillMaxWidth()) {
                PulsoUpdater.downloadAndInstall(context)
            }
        } else {
            PulsoPill(
                "Verificar atualizações", height = 40.dp, modifier = Modifier.fillMaxWidth(),
                enabled = state !is PulsoUpdater.State.Checking && state !is PulsoUpdater.State.Downloading,
            ) { PulsoUpdater.checkNow(context) }
        }
    }
}

@Composable
private fun Header(
    loggedIn: Boolean,
    userName: String?,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    onProfile: () -> Unit,
    onLanguage: () -> Unit,
    updateVersion: String? = null,
    onUpdate: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Pulso.Dp.headerHeight)
            .background(Pulso.Background)
            .padding(horizontal = Pulso.Dp.safeH, vertical = Pulso.Dp.safeV),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsoLogo(markSize = 40.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (updateVersion != null) NavPill("Atualizar v$updateVersion", onClick = onUpdate)
            NavPill("Busca", onClick = onSearch)
            // The chip already carries the language's own name, so it is readable no
            // matter which language is active. OK opens the card to change it.
            NavPill(PulsoLang.label, onClick = onLanguage)
            if (loggedIn) {
                val handle = userName.orEmpty().trim()
                NavPill(if (handle.isEmpty()) "Perfil" else "@$handle", onClick = onProfile)
            } else {
                NavPill("Entrar", onClick = onLogin)
            }
        }
    }
}

@Composable
private fun LoginShelf(onLogin: () -> Unit) {
    Column(Modifier.padding(start = Pulso.Dp.safeH, top = 8.dp)) {
        Text(
            "Seus canais",
            color = Pulso.TextPrimary,
            fontSize = Pulso.Sp.shelfTitle,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        PillButton("Entrar para ver os canais que você segue", onLogin)
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    firstRequester: FocusRequester?,
    onOpen: (Stream) -> Unit,
    onOpenGame: (com.pulso.tv.model.ui.Game) -> Unit,
    onOpenVideo: (com.pulso.tv.model.ui.Video) -> Unit,
) {
    // A shelf carries exactly one kind of content, so only that list may own the
    // shared FocusRequester: attaching it to two nodes at once is an error.
    val ownerKind = when {
        shelf.streams.isNotEmpty() -> 1
        shelf.videos.isNotEmpty() -> 2
        shelf.games.isNotEmpty() -> 3
        else -> 0
    }
    Column(Modifier.padding(start = Pulso.Dp.safeH, top = 30.dp)) {
        Text(
            shelf.title,
            color = Pulso.TextPrimary,
            fontSize = Pulso.Sp.shelfTitle,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        // keys keep each card's state (focus ring) tied to the item, not to its slot
        val videos = remember(shelf) { shelf.videos.distinctBy { it.video.id ?: Any() } }
        val games = remember(shelf) { shelf.games.distinctBy { it.id ?: Any() } }
        val streams = remember(shelf) { shelf.streams.distinctBy { it.id ?: Any() } }
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(Pulso.Dp.gap),
        ) {
            items(videos.size, key = { "v${videos[it].video.id ?: it}" }) { i ->
                val r = videos[i]
                PulsoCard(
                    thumb = r.video.thumbnail,
                    title = r.video.title.orEmpty(),
                    subtitle = (r.video.channelName ?: r.video.channelLogin).orEmpty() + " · " +
                        durationText((r.positionMs / 1000).toInt()) + " / " + durationText(r.video.durationSeconds),
                    bottomEnd = durationText(r.video.durationSeconds),
                    focusRequester = if (i == 0 && ownerKind == 2) firstRequester else null,
                    onClick = { onOpenVideo(r.video) },
                )
            }
            items(games.size, key = { "g${games[it].id ?: it}" }) { i ->
                val g = games[i]
                PulsoGameCard(
                    boxArt = g.boxArt,
                    name = g.name.orEmpty(),
                    viewers = g.viewerCount?.let { compactCount(it) + " espectadores" } ?: "",
                    focusRequester = if (i == 0 && ownerKind == 3) firstRequester else null,
                    onClick = { onOpenGame(g) },
                )
            }
            items(streams.size, key = { "s${streams[it].id ?: it}" }) { i ->
                StreamCard(
                    stream = streams[i],
                    focusRequester = if (i == 0 && ownerKind == 1) firstRequester else null,
                    onClick = { onOpen(streams[i]) },
                )
            }
        }
    }
}

@Composable
private fun NavPill(label: String, onClick: () -> Unit) {
    var focused by remember { mutableIntStateOf(0) }
    val latestClick by rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .onFocusChanged { focused = if (it.isFocused) 1 else 0 }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .pointerInput(Unit) { detectTapGestures { latestClick() } }
            .height(40.dp)
            .then(
                if (focused == 1) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape)
                else Modifier
            )
            .background(if (focused == 1) Pulso.TextPrimary else Pulso.SurfaceRaised, shape)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (focused == 1) Pulso.Background else Pulso.TextPrimary,
            fontSize = Pulso.Sp.nav,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    NavPill(label, onClick)
}

@Composable
private fun StreamCard(
    stream: Stream,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableIntStateOf(0) }
    val latestClick by rememberUpdatedState(onClick)
    val outerShape = RoundedCornerShape(Pulso.Dp.cardRadius + 5.dp)
    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(Pulso.Dp.cardWidth)
            .height(Pulso.Dp.cardHeight)
            .onFocusChanged { focused = if (it.isFocused) 1 else 0 }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .pointerInput(Unit) { detectTapGestures { latestClick() } }
    ) {
        Box(
            Modifier.fillMaxSize().then(
                if (focused == 1) Modifier
                    .border(Pulso.Dp.ringWidth, Pulso.FocusRing, outerShape)
                    .padding(Pulso.Dp.ringGap)
                else Modifier
            )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Pulso.Dp.cardRadius))
                    .background(if (focused == 1) Pulso.SurfaceRaised else Pulso.Surface)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Pulso.Dp.thumbHeight)
                        .background(Brush.linearGradient(listOf(Pulso.SurfaceRaised, Pulso.Surface)))
                ) {
                    val thumb = stream.thumbnail
                    if (!thumb.isNullOrBlank()) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    LiveBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
                    ViewersPill(
                        formatViewers(stream.viewerCount ?: 0) + " espectadores",
                        Modifier.align(Alignment.BottomStart).padding(8.dp)
                    )
                }
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                    Text(
                        stream.title.orEmpty(),
                        color = Pulso.TextPrimary,
                        fontSize = Pulso.Sp.cardTitle,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(36.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        playingText(stream.channelName ?: stream.channelLogin, stream.gameName),
                        color = Pulso.TextSecondary,
                        fontSize = Pulso.Sp.channel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Pulso.TextSecondary, fontSize = Pulso.Sp.meta, modifier = modifier)
}

@Composable
private fun ErrorPanel(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val retryFocus = remember { FocusRequester() }
    focusWhenReady(retryFocus, true, error)
    Column(
        modifier.focusProperties { exit = { FocusRequester.Cancel } }.focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Não foi possível carregar",
            color = Pulso.TextPrimary,
            fontSize = Pulso.Sp.screenTitle,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error.take(180),
            color = Pulso.TextSecondary,
            fontSize = Pulso.Sp.meta,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(24.dp))
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .focusRequester(retryFocus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        onRetry(); true
                    } else false
                }
                .border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape)
                .background(Pulso.TextPrimary, shape)
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text("Tentar de novo", color = Pulso.Background, fontSize = Pulso.Sp.nav, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Pulso.Live)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "AO VIVO",
            color = Color.White,
            fontSize = Pulso.Sp.badge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ViewersPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Pulso.Background.copy(alpha = 0.88f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = Pulso.TextPrimary, fontSize = Pulso.Sp.viewers, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Asks for the focus once the target exists. A [FocusRequester] can only be used
 * after its component is attached, which may take a frame or two, so retry rather
 * than swallowing the failure: otherwise the D-pad lands wherever the traversal
 * order starts.
 */
@Composable
private fun focusWhenReady(requester: FocusRequester, enabled: Boolean = true, key: Any?) {
    LaunchedEffect(key) {
        if (!enabled) return@LaunchedEffect
        repeat(30) {
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
}

/**
 * Modal card listing every language under its own name. Picking one applies it
 * immediately — the whole UI reads [PulsoLang.current] while drawing — and closes.
 */
@Composable
private fun LanguageCard(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val active = PulsoLang.current
    val firstLang = remember { FocusRequester() }
    focusWhenReady(firstLang, true, active)
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .width(760.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Pulso.Surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 32.dp, vertical = 27.dp),
        ) {
            Text("Idioma", color = Pulso.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            PulsoLang.all.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    row.forEachIndexed { colIndex, lang ->
                        PulsoPill(
                            label = lang.endonym,
                            primary = lang.code == active,
                            height = 44.dp,
                            requester = if (rowIndex == 0 && colIndex == 0) firstLang else null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                PulsoLang.set(ctx, lang.code)
                                onClose()
                            },
                        )
                    }
                    // keep the last row aligned when it does not fill up
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            PulsoOkHint("Escolher")
        }
    }
}

@Composable
private fun HintPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Pulso.SurfaceRaised)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Pulso.TextPrimary)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("OK", color = Pulso.Background, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.width(8.dp))
        Text(text, color = Pulso.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
