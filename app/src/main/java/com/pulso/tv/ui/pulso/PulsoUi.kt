package com.pulso.tv.ui.pulso

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.time.Duration
import java.time.Instant

/** Shared building blocks for the Pulso screens (search, channel). */

@Composable
fun PulsoCard(
    thumb: String?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    bottomStart: String? = null,
    bottomEnd: String? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val latestClick by rememberUpdatedState(onClick)
    val outerShape = RoundedCornerShape(Pulso.Dp.cardRadius + 5.dp)
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(Pulso.Dp.cardWidth)
            .height(Pulso.Dp.cardHeight)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                    latestClick(); true
                } else false
            }
            .pointerInput(Unit) { detectTapGestures { latestClick() } }
    ) {
        Box(
            Modifier.fillMaxSize().then(
                if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, outerShape).padding(Pulso.Dp.ringGap)
                else Modifier
            )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Pulso.Dp.cardRadius))
                    .background(if (focused) Pulso.SurfaceRaised else Pulso.Surface)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Pulso.Dp.thumbHeight)
                        .background(Brush.linearGradient(listOf(Pulso.SurfaceRaised, Pulso.Surface)))
                ) {
                    if (!thumb.isNullOrBlank()) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (live) {
                        Box(
                            Modifier.align(Alignment.TopStart).padding(8.dp)
                                .clip(RoundedCornerShape(3.dp)).background(Pulso.Live)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("AO VIVO", color = Color.White, fontSize = Pulso.Sp.badge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                    if (!bottomStart.isNullOrBlank()) PulsoChip(bottomStart, Modifier.align(Alignment.BottomStart).padding(8.dp))
                    if (!bottomEnd.isNullOrBlank()) PulsoChip(bottomEnd, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                    Text(
                        title,
                        color = Pulso.TextPrimary,
                        fontSize = Pulso.Sp.cardTitle,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(36.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, color = Pulso.TextSecondary, fontSize = Pulso.Sp.channel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Portrait card (3:4, the shape of a game's box art) so the cover is never cropped. */
@Composable
fun PulsoGameCard(
    boxArt: String?,
    name: String,
    viewers: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val latestClick by rememberUpdatedState(onClick)
    val outerShape = RoundedCornerShape(Pulso.Dp.cardRadius + 5.dp)
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(GAME_CARD_WIDTH)
            .height(GAME_CARD_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                    latestClick(); true
                } else false
            }
            .pointerInput(Unit) { detectTapGestures { latestClick() } }
    ) {
        Box(
            Modifier.fillMaxSize().then(
                if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, outerShape).padding(Pulso.Dp.ringGap)
                else Modifier
            )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Pulso.Dp.cardRadius))
                    .background(if (focused) Pulso.SurfaceRaised else Pulso.Surface)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(GAME_ART_HEIGHT)
                        .background(Brush.linearGradient(listOf(Pulso.SurfaceRaised, Pulso.Surface)))
                ) {
                    if (!boxArt.isNullOrBlank()) {
                        AsyncImage(
                            model = boxArt,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        name, color = Pulso.TextPrimary, fontSize = 12.sp, lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(30.dp),
                    )
                    Text(viewers, color = Pulso.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// 3:4 art; 5 fit on the 864 dp content width with a sixth peeking in
val GAME_CARD_WIDTH = 150.dp
val GAME_ART_HEIGHT = 200.dp
val GAME_CARD_HEIGHT = 284.dp // art + name (2 lines) + viewers, with room for the focus ring

@Composable
private fun PulsoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xE00E0E10))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = Pulso.TextPrimary, fontSize = Pulso.Sp.viewers, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Pill button: neutral (#26262C, inverts when focused), primary (yellow fill, keeps it)
 * or selected (yellow, used by tabs).
 */
@Composable
fun PulsoPill(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 40.dp,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val latestClick by rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(50)
    val bg = when {
        primary -> Pulso.Yellow
        focused -> Pulso.TextPrimary
        else -> Pulso.SurfaceRaised
    }
    val fg = when {
        primary -> Pulso.Background
        focused -> Pulso.Background
        else -> Pulso.TextPrimary
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (requester != null && enabled) Modifier.focusRequester(requester) else Modifier)
            .then(
                if (enabled) Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                            latestClick(); true
                        } else false
                    }
                    .pointerInput(Unit) { detectTapGestures { latestClick() } }
                else Modifier
            )
            .height(height)
            .then(if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape) else Modifier)
            .background(bg, shape)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = Pulso.Sp.nav, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Bottom-right "OK <action>" pill from the design. */
@Composable
fun PulsoOkHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Pulso.SurfaceRaised)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.height(24.dp).clip(RoundedCornerShape(50)).background(Pulso.TextPrimary).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("OK", color = Pulso.Background, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
        Box(Modifier.width(8.dp))
        Text(text, color = Pulso.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PulsoScreenMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = Pulso.TextSecondary, fontSize = Pulso.Sp.meta)
    }
}

// ---- formatting ----------------------------------------------------------

/** "mesa_redonda está jogando League of Legends" (just the name when the game is unknown). */
fun playingText(name: String?, game: String?): String =
    if (game.isNullOrBlank()) name.orEmpty() else "${name.orEmpty()} está jogando $game"

/** 48200 -> "48,2 mil" (pt) · "48.2K" (en) · "4.8万" (ja); rules live in PulsoLang. */
fun compactCount(n: Int): String = PulsoLang.compact(n)

fun durationText(seconds: Int?): String {
    val s = seconds ?: return ""
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val locale = java.util.Locale.US
    return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, sec)
    else String.format(locale, "%d:%02d", m, sec)
}

/** ISO instant -> "há 3 dias" in the active language. */
fun timeAgo(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val d = Duration.between(Instant.parse(iso), Instant.now())
        val min = d.toMinutes()
        val hours = d.toHours()
        val days = d.toDays()
        when {
            min < 1 -> PulsoLang.justNow()
            min < 60 -> PulsoLang.ago(min, PulsoAgoUnit.MINUTE)
            hours < 24 -> PulsoLang.ago(hours, PulsoAgoUnit.HOUR)
            days < 7 -> PulsoLang.ago(days, PulsoAgoUnit.DAY)
            days < 30 -> PulsoLang.ago(days / 7, PulsoAgoUnit.WEEK)
            days < 365 -> PulsoLang.ago(days / 30, PulsoAgoUnit.MONTH)
            else -> PulsoLang.ago(days / 365, PulsoAgoUnit.YEAR)
        }
    }.getOrDefault("")
}
