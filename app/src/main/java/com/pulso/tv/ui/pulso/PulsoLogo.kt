package com.pulso.tv.ui.pulso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * pulso.tv mark, built from the brand manual: a big yellow disc (the channel), a
 * smaller cobalt disc (you), and red only where they meet. Base 130 x 130 u, centres
 * (89, 89) and (131, 131), radii 54 and 34.
 */
@Composable
fun PulsoMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 130f
        fun p(x: Float, y: Float) = Offset((x - 35f) * k, (y - 35f) * k)
        val big = p(89f, 89f)
        val small = p(131f, 131f)
        drawCircle(Pulso.Yellow, radius = 54f * k, center = big)
        drawCircle(Pulso.Cobalt, radius = 34f * k, center = small)
        val bigDisc = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    big.x - 54f * k, big.y - 54f * k, big.x + 54f * k, big.y + 54f * k
                )
            )
        }
        clipPath(bigDisc, ClipOp.Intersect) {
            drawCircle(Pulso.Live, radius = 34f * k, center = small)
        }
    }
}

/** Mark + "pulso.tv" (Figtree 700; ".tv" in 500 and yellow). The text is 3/4 of the mark. */
@Composable
fun PulsoLogo(markSize: Dp = 40.dp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(markSize * 0.22f)) {
        PulsoMark(markSize)
        androidx.compose.material3.Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Pulso.TextPrimary, fontWeight = FontWeight.Bold)) { append("pulso") }
                withStyle(SpanStyle(color = Pulso.Yellow, fontWeight = FontWeight.Medium)) { append(".tv") }
            },
            fontSize = (markSize.value * 0.75f).sp,
            letterSpacing = (-0.03f * markSize.value * 0.75f).sp,
        )
    }
}
