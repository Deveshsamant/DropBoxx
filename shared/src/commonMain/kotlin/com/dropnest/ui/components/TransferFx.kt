package com.dropnest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dropnest.ui.motion.loopPhase
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** apSheen: a soft accent band sweeping across a live card every 2.6 s. Put it behind the content. */
@Composable
fun Sheen(modifier: Modifier = Modifier, active: Boolean = true) {
    if (!active) return
    val t = N
    val phase = loopPhase(2600)
    Canvas(modifier.fillMaxSize()) {
        val bandW = size.width * 0.32f
        val x = -bandW * 1.5f + phase * (size.width + bandW * 4.2f)
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, t.accentSoft, Color.Transparent), startX = x, endX = x + bandW),
            topLeft = Offset(x, 0f), size = Size(bandW, size.height),
        )
    }
}

/**
 * The conveyor from the Transfers card: a dashed lane between the two devices with three chips
 * looping along it, bobbing and turning as they go (apStripe + the JS conveyor loop).
 */
@Composable
fun Conveyor(fromIcon: ImageVector, toIcon: ImageVector, active: Boolean, modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val t = N
    val phase = loopPhase(3200)
    val stripe = loopPhase(800)
    val density = LocalDensity.current.density
    Well(modifier.height(height), radius = 11.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val laneL = 72f * density; val laneR = w - 80f * density
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2
                val dash = 10f * density; val gap = 12f * density
                var x = laneL - (stripe * (dash + gap))
                while (x < laneR) {
                    val x0 = x.coerceAtLeast(laneL); val x1 = (x + dash).coerceAtMost(laneR)
                    if (x1 > x0) drawLine(t.accentLine, Offset(x0, y), Offset(x1, y), 2f)
                    x += dash + gap
                }
            }
            EndCap(fromIcon, muted = true, Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
            EndCap(toIcon, muted = false, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), size = 52.dp)
            if (active) repeat(3) { i ->
                val tt = (phase + i * 0.33f) % 1f
                val x = laneL + tt * (laneR - laneL - 38f * density)
                val y = sin(tt * 2 * PI).toFloat() * 7f * density
                val depth = cos(tt * 2 * PI).toFloat()          // fake translateZ: nearer = bigger
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .graphicsLayer { val sc = 1f + 0.08f * depth; scaleX = sc; scaleY = sc; alpha = if (tt > 0.94f) 0f else 1f; rotationY = tt * 300f; cameraDistance = 12f * density }
                        .size(38.dp, 28.dp).clip(RoundedCornerShape(7.dp)).background(t.surface).border(1.dp, t.edgeStrong, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(listOf(Ph.Image, Ph.File, Ph.Folder)[i], null, tint = t.accent, modifier = Modifier.size(15.dp)) }
            }
        }
    }
}

@Composable
private fun EndCap(icon: ImageVector, muted: Boolean, modifier: Modifier, size: Dp = 44.dp) {
    val t = N
    Box(
        modifier.size(size, 52.dp).clip(RoundedCornerShape(9.dp)).background(t.surface).border(1.dp, t.edge, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (muted) t.muted else t.accent, modifier = Modifier.size(20.dp)) }
}

/** Phone transfer ring: 64 px, stroke 5, accent arc from the top, tabular percentage inside. */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val t = N
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(300), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(t.soft, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(t.accent, -90f, 360f * f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(f * 100).roundToInt()}%", color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
