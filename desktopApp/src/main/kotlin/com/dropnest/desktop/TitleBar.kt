package com.dropnest.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.dropnest.core.AppInfo
import com.dropnest.ui.theme.N
import java.awt.Rectangle
import java.awt.Toolkit

val TITLE_BAR_HEIGHT = 38.dp

/**
 * Custom window chrome that follows the Nocturne theme instead of the Windows title bar:
 * logo + name on the left, minimise / maximise / close on the right, drag anywhere, double-click
 * to toggle maximise. Colour matches the sidebar so the two read as one surface.
 */
@Composable
fun FrameWindowScope.NocturneTitleBar(state: WindowState, logo: Painter, onClose: () -> Unit) {
    val t = N
    val maximized = state.placement == WindowPlacement.Maximized
    // Undecorated frames maximise over the taskbar unless told the usable area of their screen.
    fun fitWorkArea() {
        val gc = window.graphicsConfiguration ?: return
        val b = gc.bounds
        val ins = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        window.maximizedBounds = Rectangle(b.x + ins.left, b.y + ins.top, b.width - ins.left - ins.right, b.height - ins.top - ins.bottom)
    }
    LaunchedEffect(Unit) { fitWorkArea() }
    fun toggleMax() {
        if (maximized) state.placement = WindowPlacement.Floating
        else { fitWorkArea(); state.placement = WindowPlacement.Maximized }
    }

    Row(
        Modifier.fillMaxWidth().height(TITLE_BAR_HEIGHT).background(t.bg2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().padding(start = 12.dp).pointerInput(maximized) {
                    // Observe presses without consuming them so the drag handler above still works.
                    var last = 0L
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.type == PointerEventType.Press) {
                                val now = System.currentTimeMillis()
                                if (now - last < 350) { toggleMax(); last = 0 } else last = now
                            }
                        }
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(logo, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicText(AppInfo.NAME, style = TextStyle(color = t.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp))
                Spacer(Modifier.width(8.dp))
                BasicText("·", style = TextStyle(color = t.muted, fontSize = 12.sp))
                Spacer(Modifier.width(8.dp))
                BasicText("your box, on every device", style = TextStyle(color = t.muted, fontSize = 11.sp))
            }
        }
        CaptionButton(Glyph.Minimize) { state.isMinimized = true }
        CaptionButton(if (maximized) Glyph.Restore else Glyph.Maximize) { toggleMax() }
        CaptionButton(Glyph.Close, danger = true, onClick = onClose)
    }
}

private enum class Glyph { Minimize, Maximize, Restore, Close }

@Composable
private fun CaptionButton(glyph: Glyph, danger: Boolean = false, onClick: () -> Unit) {
    val t = N
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        hovered && danger -> Color(0xFFC42B1C)
        hovered -> t.surface2
        else -> Color.Transparent
    }
    val fg = if (hovered && danger) Color.White else t.text
    Box(
        Modifier.width(46.dp).fillMaxHeight().background(bg).hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) {
            val w = 1.dp.toPx()
            val s = size.width
            when (glyph) {
                Glyph.Minimize -> drawLine(fg, Offset(0f, s / 2), Offset(s, s / 2), w, StrokeCap.Square)
                Glyph.Maximize -> drawRect(fg, Offset(w / 2, w / 2), Size(s - w, s - w), style = Stroke(w))
                Glyph.Restore -> {
                    drawRect(fg, Offset(w / 2, 2.5f * w), Size(s - 3 * w, s - 3 * w), style = Stroke(w))
                    drawLine(fg, Offset(2.5f * w, 2.5f * w), Offset(2.5f * w, w / 2), w)
                    drawLine(fg, Offset(2.5f * w, w / 2), Offset(s - w / 2, w / 2), w)
                    drawLine(fg, Offset(s - w / 2, w / 2), Offset(s - w / 2, s - 2.5f * w), w)
                    drawLine(fg, Offset(s - w / 2, s - 2.5f * w), Offset(s - 2.5f * w, s - 2.5f * w), w)
                }
                Glyph.Close -> {
                    drawLine(fg, Offset(0f, 0f), Offset(s, s), w, StrokeCap.Round)
                    drawLine(fg, Offset(s, 0f), Offset(0f, s), w, StrokeCap.Round)
                }
            }
        }
    }
}
