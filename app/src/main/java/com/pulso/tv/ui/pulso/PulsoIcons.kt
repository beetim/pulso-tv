package com.pulso.tv.ui.pulso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

// Drawn icons: font glyphs (gear, fullscreen, arrows) render differently on every
// device, so the player buttons draw their own.

@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val toothW = r * 0.34f
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                drawRect(color, Offset(c.x - toothW / 2f, c.y - r), Size(toothW, r * 0.38f))
            }
        }
        drawCircle(color, radius = r * 0.58f, center = c, style = Stroke(width = r * 0.3f))
    }
}

@Composable
fun FullscreenIcon(expand: Boolean, color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.12f
        val leg = w * 0.32f
        val inset = w * 0.3f
        for (ex in listOf(0f, w)) {
            for (ey in listOf(0f, h)) {
                val dx = if (ex == 0f) 1f else -1f
                val dy = if (ey == 0f) 1f else -1f
                if (expand) {
                    val v = Offset(ex, ey)
                    drawLine(color, v, Offset(v.x + dx * leg, v.y), stroke, StrokeCap.Round)
                    drawLine(color, v, Offset(v.x, v.y + dy * leg), stroke, StrokeCap.Round)
                } else {
                    val v = Offset(ex + dx * inset, ey + dy * inset)
                    drawLine(color, v, Offset(v.x - dx * inset, v.y), stroke, StrokeCap.Round)
                    drawLine(color, v, Offset(v.x, v.y - dy * inset), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun PlayIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.22f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.22f, h * 0.88f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun PauseIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(color, Offset(w * 0.18f, h * 0.12f), Size(w * 0.22f, h * 0.76f))
        drawRect(color, Offset(w * 0.6f, h * 0.12f), Size(w * 0.22f, h * 0.76f))
    }
}

@Composable
fun HeartIcon(filled: Boolean, color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.05f, h * 0.55f, w * 0.1f, h * 0.15f, w * 0.32f, h * 0.15f)
            cubicTo(w * 0.43f, h * 0.15f, w * 0.5f, h * 0.24f, w * 0.5f, h * 0.3f)
            cubicTo(w * 0.5f, h * 0.24f, w * 0.57f, h * 0.15f, w * 0.68f, h * 0.15f)
            cubicTo(w * 0.9f, h * 0.15f, w * 0.95f, h * 0.55f, w * 0.5f, h * 0.88f)
            close()
        }
        if (filled) drawPath(p, color) else drawPath(p, color, style = Stroke(width = w * 0.1f))
    }
}

/** Speech bubble; slashed when the chat is hidden. */
@Composable
fun ChatIcon(visible: Boolean, color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.12f, h * 0.18f)
            lineTo(w * 0.88f, h * 0.18f)
            lineTo(w * 0.88f, h * 0.66f)
            lineTo(w * 0.5f, h * 0.66f)
            lineTo(w * 0.3f, h * 0.86f)
            lineTo(w * 0.3f, h * 0.66f)
            lineTo(w * 0.12f, h * 0.66f)
            close()
        }
        drawPath(p, color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        if (!visible) {
            drawLine(color, Offset(w * 0.05f, h * 0.95f), Offset(w * 0.95f, h * 0.05f), stroke, StrokeCap.Round)
        }
    }
}

/** Person: head and shoulders. */
@Composable
fun ChannelIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.3f))
        drawArc(
            color, startAngle = 180f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(w * 0.14f, h * 0.58f), size = Size(w * 0.72f, h * 0.7f),
        )
    }
}

/** Three vertical dots. */
@Composable
fun MoreIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.1f
        listOf(0.2f, 0.5f, 0.8f).forEach { y -> drawCircle(color, r, Offset(w * 0.5f, h * y)) }
    }
}

/** Speaker; with sound waves, or crossed out when [muted]. */
@Composable
fun VolumeIcon(muted: Boolean, color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        // speaker body: small box + cone
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.04f, h * 0.38f)
            lineTo(w * 0.28f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.16f)
            lineTo(w * 0.52f, h * 0.84f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.04f, h * 0.62f)
            close()
        }
        drawPath(body, color)
        if (muted) {
            drawLine(color, Offset(w * 0.68f, h * 0.36f), Offset(w * 0.96f, h * 0.64f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.96f, h * 0.36f), Offset(w * 0.68f, h * 0.64f), stroke, StrokeCap.Round)
        } else {
            drawArc(
                color, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(w * 0.4f, h * 0.3f), size = Size(w * 0.4f, h * 0.4f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(w * 0.3f, h * 0.14f), size = Size(w * 0.66f, h * 0.72f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun BackIcon(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.12f
        val mid = h / 2f
        drawLine(color, Offset(w * 0.92f, mid), Offset(w * 0.08f, mid), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.08f, mid), Offset(w * 0.46f, mid - w * 0.38f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.08f, mid), Offset(w * 0.46f, mid + w * 0.38f), stroke, StrokeCap.Round)
    }
}
