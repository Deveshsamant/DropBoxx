package com.dropnest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.dropnest.model.ItemKind
import com.dropnest.core.MimeTypes
import com.dropnest.ui.motion.NocturneEase
import com.dropnest.ui.motion.SpinState
import com.dropnest.ui.motion.loopPhase
import com.dropnest.ui.motion.spinDrag
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.NocturneTokens
import com.dropnest.ui.theme.Ph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the nest shows for one box item. */
data class NestChip(val id: String, val kind: ItemKind, val mimeType: String)

fun nestIcon(kind: ItemKind, mime: String): ImageVector = when {
    kind == ItemKind.URL -> Ph.Link
    kind == ItemKind.TEXT -> Ph.TextLines
    MimeTypes.isImage(mime) -> Ph.Image
    else -> Ph.File
}

/**
 * The prototype's `[data-nest]`: a hexagonal floor with six glass panels, a glowing fill
 * that rises with the item count, two radar rings, and item chips floating inside.
 * Drawn on one Canvas with a simple tilt-camera projection so it stays cheap on phones.
 * Drag horizontally to spin; it auto-rotates slowly while idle.
 */
@Composable
fun NestHero(
    chips: List<NestChip>,
    spin: SpinState,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    interactive: Boolean = true,
    motion: Boolean = true,
) {
    val t = N
    val ringPhase = loopPhase(3400)
    val icons = listOf(Ph.Image, Ph.File, Ph.TextLines, Ph.Link).map { rememberVectorPainter(it) }
    // apRise for chips: each chip animates in once, staggered by index.
    val rise = remember { mutableMapOf<String, Animatable<Float, *>>() }
    chips.forEachIndexed { i, chip ->
        val anim = rise.getOrPut(chip.id) { Animatable(0f) }
        LaunchedEffect(chip.id) {
            if (anim.value < 1f) { kotlinx.coroutines.delay((i.coerceAtMost(8) * 50).toLong()); anim.animateTo(1f, tween(500, easing = NocturneEase)) }
        }
    }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(chips.size) { fill.animateTo((chips.size / 8.5f).coerceAtMost(0.84f), tween(700, easing = NocturneEase)) }

    Box(modifier.then(if (interactive) Modifier.spinDrag(spin) else Modifier)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = (scale * size.minDimension / 260f).coerceIn(0.6f, size.minDimension / 225f)
            val cx = size.width / 2
            val cy = size.height * 0.52f
            val spinRad = (spin.degrees * PI / 180).toFloat()
            drawNest(t, cx, cy, s, spinRad, if (motion) ringPhase else 0.25f, fill.value, chips, icons, rise)
        }
    }
}

private const val TILT = 0.36f            // camera elevation ~20°: floors squash to sin(20°)
private const val COS_T = 0.94f

/** World: x right, y up, z toward the viewer. */
private fun project(x: Float, y: Float, z: Float, spin: Float, cx: Float, cy: Float, s: Float): Offset {
    val rx = x * cos(spin) + z * sin(spin)
    val rz = -x * sin(spin) + z * cos(spin)
    return Offset(cx + rx * s, cy - y * COS_T * s + rz * TILT * s)
}

private fun depthOf(x: Float, z: Float, spin: Float): Float = -x * sin(spin) + z * cos(spin)

private fun DrawScope.drawNest(
    t: NocturneTokens, cx: Float, cy: Float, s: Float, spin: Float, ringPhase: Float, fill: Float,
    chips: List<NestChip>, icons: List<VectorPainter>, rise: Map<String, Animatable<Float, *>>,
) {
    // Glow disc under the floor.
    drawOval(
        Brush.radialGradient(listOf(t.accentSoft, Color.Transparent), center = Offset(cx, cy + 20 * s), radius = 104 * s),
        topLeft = Offset(cx - 104 * s, cy + 20 * s - 104 * s * TILT), size = Size(208 * s, 208 * s * TILT),
    )
    // Radar rings (apRing): scale .3 -> 1.75, alpha .55 -> 0, two of them half a cycle apart.
    for (k in 0..1) {
        val p = (ringPhase + k * 0.5f) % 1f
        val r = 70 * s * (0.3f + 1.45f * p)
        drawOval(t.accentLine.copy(alpha = t.accentLine.alpha * 0.55f * (1 - p)), topLeft = Offset(cx - r, cy - r * TILT), size = Size(r * 2, r * 2 * TILT), style = Stroke(1.dp.toPx()))
    }
    // Hexagon helper (flat, radius R at height h).
    fun hex(radius: Float, h: Float): Path = Path().apply {
        for (i in 0..5) {
            val a = (PI / 3 * i).toFloat()
            val p = project(cos(a) * radius, h, sin(a) * radius, spin, cx, cy, s)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    // Accent fill that rises with the item count (sits below the floor, showing through the glass).
    val fillHeight = -30f + fill * 40f
    drawPath(hex(63f, fillHeight), Brush.linearGradient(listOf(t.accent.copy(alpha = 0.5f), t.accentDeep.copy(alpha = 0.5f))))
    // Floor.
    drawPath(hex(63f, 0f), Brush.linearGradient(listOf(t.surface2, t.bg2)))
    drawPath(hex(63f, 0f), t.accentLine, style = Stroke(1.dp.toPx()))

    // Six glass panels standing on the hex edges, plus chips — everything depth-sorted.
    data class Item(val depth: Float, val draw: () -> Unit)
    val items = mutableListOf<Item>()
    for (k in 0..5) {
        val a = (PI / 3 * k + PI / 6).toFloat()
        val ux = cos(a); val uz = sin(a)
        val px = -uz; val pz = ux
        val R = 54f; val w = 32f; val hgt = 116f
        val c1x = ux * R + px * w; val c1z = uz * R + pz * w
        val c2x = ux * R - px * w; val c2z = uz * R - pz * w
        val depth = depthOf(ux * R, uz * R, spin)
        items += Item(depth) {
            val b1 = project(c1x, 0f, c1z, spin, cx, cy, s); val b2 = project(c2x, 0f, c2z, spin, cx, cy, s)
            val t1 = project(c1x, hgt, c1z, spin, cx, cy, s); val t2 = project(c2x, hgt, c2z, spin, cx, cy, s)
            val path = Path().apply { moveTo(b1.x, b1.y); lineTo(t1.x, t1.y); lineTo(t2.x, t2.y); lineTo(b2.x, b2.y); close() }
            val front = (depth + 54f) / 108f
            drawPath(path, Brush.verticalGradient(listOf(t.accent.copy(alpha = 0.03f + 0.13f * front), t.accent.copy(alpha = 0.02f)), startY = t1.y, endY = b1.y))
            drawLine(t.accentLine, b1, t1, 1.dp.toPx()); drawLine(t.accentLine, t1, t2, 1.dp.toPx()); drawLine(t.accentLine, t2, b2, 1.dp.toPx())
        }
    }
    chips.forEachIndexed { i, chip ->
        val n = chips.size.coerceAtLeast(1)
        val a = (i.toFloat() / n) * 2 * PI.toFloat() + i * 0.6f
        val r = 20f + (i % 3) * 9f
        val h = 8f + i * 13f + (i % 2) * 5f
        val x = cos(a) * r; val z = sin(a) * r
        val progress = rise[chip.id]?.value ?: 1f
        items += Item(depthOf(x, z, spin)) {
            val p = project(x, h - (1 - progress) * 14f, z, spin, cx, cy, s)
            val cw = 46 * s; val ch = 30 * s
            val alpha = progress
            drawRoundRect(t.shadow.copy(alpha = 0.35f * alpha), Offset(p.x - cw / 2, p.y - ch / 2 + 3 * s), Size(cw, ch), androidx.compose.ui.geometry.CornerRadius(7 * s))
            drawRoundRect(t.surface.copy(alpha = alpha), Offset(p.x - cw / 2, p.y - ch / 2), Size(cw, ch), androidx.compose.ui.geometry.CornerRadius(7 * s))
            drawRoundRect(t.edgeStrong.copy(alpha = alpha), Offset(p.x - cw / 2, p.y - ch / 2), Size(cw, ch), androidx.compose.ui.geometry.CornerRadius(7 * s), style = Stroke(1f))
            val icon = icons[when { chip.kind == ItemKind.URL -> 3; chip.kind == ItemKind.TEXT -> 2; MimeTypes.isImage(chip.mimeType) -> 0; else -> 1 }]
            val isz = 16 * s
            translate(p.x - isz / 2, p.y - isz / 2) { with(icon) { draw(Size(isz, isz), alpha = alpha, colorFilter = ColorFilter.tint(t.accent)) } }
        }
    }
    items.sortedBy { it.depth }.forEach { it.draw() }
}

