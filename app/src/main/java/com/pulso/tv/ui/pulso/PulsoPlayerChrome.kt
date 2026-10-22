package com.pulso.tv.ui.pulso

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Player chrome from the design: top info block plus the bottom bar with the
 * four controls in the designed focus order (1 Pausar, 2 Seguir, 3 Chat,
 * 4 Opcoes). Buttons proxy the existing player views, so no playback logic
 * lives here.
 */
@Composable
fun PulsoPlayerChrome(
    channelName: String,
    title: String,
    viewers: String,
    onPlayPause: () -> Unit,
    onFollow: () -> Unit,
    onChat: () -> Unit,
    onOptions: () -> Unit,
) {
    var focused by remember { mutableIntStateOf(0) }
    val firstButton = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { firstButton.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            )
            Row(
                Modifier.padding(start = 48.dp, top = 27.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Pulso.Cobalt),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        channelName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        color = Pulso.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(channelName, color = Pulso.TextPrimary, fontSize = 14.sp)
                        Box(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(3.dp)).background(Pulso.Live)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("AO VIVO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.width(8.dp))
                        Text(viewers, color = Pulso.TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, bottom = 27.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Pulso.Live)
                )
                Box(Modifier.width(12.dp))
                Text("AO VIVO", color = Pulso.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BarButton("Pausar", focused == 1, { focused = 1 }, onPlayPause, firstButton)
                BarButton("Seguir", focused == 2, { focused = 2 }, onFollow)
                BarButton("Chat", focused == 3, { focused = 3 }, onChat)
                BarButton("Opções", focused == 4, { focused = 4 }, onOptions)
            }
        }
    }
}

@Composable
private fun BarButton(
    label: String,
    focused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    requester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .then(
                if (requester != null)
                    Modifier.focusRequester(requester)
                else Modifier
            )
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick(); true
                } else false
            }
            .pointerInput(onClick) { detectTapGestures { onFocus(); onClick() } }
            .height(36.dp)
            .then(
                if (focused) Modifier.border(Pulso.Dp.ringWidth, Pulso.FocusRing, shape)
                else Modifier
            )
            .background(if (focused) Pulso.TextPrimary else Pulso.SurfaceRaised, shape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (focused) Pulso.Background else Pulso.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
