package com.dropnest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dropnest.model.DeviceType
import com.dropnest.model.Peer
import com.dropnest.ui.motion.SpinState
import com.dropnest.ui.motion.bobOffset
import com.dropnest.ui.motion.spinDrag
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun DeviceType.phIcon(): ImageVector = when (this) {
    DeviceType.ANDROID, DeviceType.IOS -> Ph.Phone
    DeviceType.WINDOWS -> Ph.Desktop
    DeviceType.MACOS, DeviceType.LINUX -> Ph.Laptop
    DeviceType.WEB -> Ph.Globe
    DeviceType.UNKNOWN -> Ph.Desktop
}

/**
 * The prototype's `[data-orbit]`: nearby devices circle this device on a tilted ring.
 * Cards keep facing the viewer, shrink and fade as they pass behind, and are drawn back-to-front.
 * Drag to spin; tap a card to open that device's box.
 */
@Composable
fun OrbitField(
    peers: List<Peer>,
    spin: SpinState,
    me: DeviceType,
    meName: String,
    onOpen: (Peer) -> Unit,
    modifier: Modifier = Modifier,
    radius: Float = 158f,
    cardWidth: Int = 126,
    hint: String = "Drag to orbit",
    motion: Boolean = true,
) {
    val t = N
    val bob = if (motion) bobOffset() else 0f
    BoxWithConstraints(modifier.spinDrag(spin)) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val s = (minOf(w / (radius * 2.6f), h / (radius * 1.6f))).coerceIn(0.55f, 1.4f)
        val cx = w / 2; val cy = h * 0.54f
        val R = radius * s
        Canvas(Modifier.fillMaxSize()) {
            // Outer ring and dashed inner ring lie flat: squashed ellipses.
            drawOval(t.accentLine.copy(alpha = t.accentLine.alpha * 0.5f), Offset(cx - R, cy - R * 0.41f), Size(R * 2, R * 0.82f), style = Stroke(1.dp.toPx()))
            val r2 = R * 0.68f
            drawOval(t.soft, Offset(cx - r2, cy - r2 * 0.41f), Size(r2 * 2, r2 * 0.82f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }
        // This device, floating in the middle.
        val centerDy = (cy - h / 2).roundToInt()
        Column(Modifier.align(Alignment.Center).offset { IntOffset(0, centerDy + bob.roundToInt()) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(92.dp * s.coerceIn(0.7f, 1f)).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(t.accentSoft, Color.Transparent)))
                    .border(1.dp, t.accentLine, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(me.phIcon(), null, tint = t.accent, modifier = Modifier.size(30.dp)) }
            Text(meName, color = t.text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Muted("this device", 10)
        }
        // Peers, back to front.
        val n = peers.size.coerceAtLeast(1)
        val placed = peers.mapIndexed { i, peer ->
            val a = ((i.toFloat() / n) * 360f + spin.degrees) * PI.toFloat() / 180f
            val z = cos(a)                     // 1 = front, -1 = back
            Triple(peer, sin(a) * R, z)
        }.sortedBy { it.third }
        placed.forEach { (peer, x, z) ->
            val scale = 0.86f + 0.14f * (z + 1) / 2
            val alpha = 0.72f + 0.28f * (z + 1) / 2
            val y = cy + z * R * 0.62f - h / 2
            Box(
                Modifier.align(Alignment.Center)
                    .offset { IntOffset((cx - w / 2 + x).roundToInt(), y.roundToInt()) }
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .width(cardWidth.dp),
            ) { OrbitCard(peer) { onOpen(peer) } }
        }
        Muted(hint, 10, Modifier.align(Alignment.TopStart).padding(14.dp, 12.dp))
    }
}

@Composable
private fun OrbitCard(peer: Peer, onClick: () -> Unit) {
    val t = N
    NCard(radius = 11.dp, elevation = 2, lift = true, onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTile(peer.info.deviceType.phIcon(), size = 30.dp, iconSize = 16.dp, radius = 8.dp, muted = !peer.trusted)
            Column(Modifier.padding(start = 8.dp)) {
                Text(peer.info.alias, color = t.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                Text(if (peer.trusted) "trusted" else peer.address, color = if (peer.trusted) t.accent else t.muted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
