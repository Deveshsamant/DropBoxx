package com.dropnest.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** cubic-bezier(.2,.8,.2,1) — the prototype's signature ease. */
val NocturneEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** apScreen: 440 ms, fade + slide from the right + depth. */
val screenEnter: EnterTransition = fadeIn(tween(440, easing = NocturneEase)) +
    slideInHorizontally(tween(440, easing = NocturneEase)) { it / 24 } +
    scaleIn(tween(440, easing = NocturneEase), initialScale = 0.965f)
val screenExit: ExitTransition = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 40 }

/** apDialog: translateY(22px) scale(.92) → identity in 400 ms. */
val dialogEnter: EnterTransition = fadeIn(tween(400, easing = NocturneEase)) +
    slideInVertically(tween(400, easing = NocturneEase)) { 22 } + scaleIn(tween(400, easing = NocturneEase), initialScale = 0.92f)

/** apSheet: bottom sheet slides up in 460 ms. */
val sheetEnter: EnterTransition = slideInVertically(tween(460, easing = NocturneEase)) { it }

/** apPop: scale .94 → 1 in 660 ms (the frames on first paint). */
val popEnter: EnterTransition = fadeIn(tween(660, easing = NocturneEase)) + scaleIn(tween(660, easing = NocturneEase), initialScale = 0.94f)

/**
 * A rotating value with inertia — the prototype's `_spin` / `_vel` pair: auto-spins at
 * [autoDegPerSec] while idle, drag adds velocity that decays by 6 % per frame.
 */
@Stable
class SpinState(initial: Float = 0f, private val autoDegPerSec: Float) {
    var degrees by mutableFloatStateOf(initial)
    var velocity by mutableFloatStateOf(0f)
    var dragging by mutableFloatStateOf(0f)
    var enabled by mutableFloatStateOf(1f)

    fun drag(dxPx: Float) {
        degrees += dxPx * 0.5f
        velocity = dxPx * 0.06f
    }

    suspend fun run() {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dtFrames = ((now - last) / 16_666_667.0).toFloat().coerceAtMost(3f)
                    if (enabled > 0f && dragging == 0f) degrees += (autoDegPerSec / 60f) * dtFrames + velocity * dtFrames
                    else if (dragging == 0f) degrees += velocity * dtFrames
                    velocity *= Math.pow(0.94, dtFrames.toDouble()).toFloat()
                }
                last = now
            }
        }
    }
}

@Composable
fun rememberSpin(autoDegPerSec: Float, enabled: Boolean = true): SpinState {
    val state = remember { SpinState(autoDegPerSec = autoDegPerSec) }
    state.enabled = if (enabled) 1f else 0f
    LaunchedEffect(state) { state.run() }
    return state
}

/** Horizontal drag spins the given [SpinState]; a near-zero drag is treated as a tap by [onTap]. */
fun Modifier.spinDrag(spin: SpinState, onTap: (() -> Unit)? = null): Modifier = pointerInput(spin) {
    var moved = 0f
    detectDragGestures(
        onDragStart = { moved = 0f; spin.dragging = 1f },
        onDragEnd = { spin.dragging = 0f; if (moved < 5f) onTap?.invoke() },
        onDragCancel = { spin.dragging = 0f },
    ) { change, drag ->
        change.consume()
        moved += kotlin.math.abs(drag.x)
        spin.drag(drag.x)
    }
}

/** apRise: fade + 14px rise, staggered by [index] * 50 ms. */
fun Modifier.rise(index: Int, key: Any? = null): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        kotlinx.coroutines.delay((index.coerceAtMost(12) * 50).toLong())
        progress.animateTo(1f, tween(500, easing = NocturneEase))
    }
    val density = LocalDensity.current
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * with(density) { 14.dp.toPx() }
    }
}

/** data-lift: hover raises the element 3px and strengthens its shadow (desktop only, no-op on touch). */
fun Modifier.hoverLift(interaction: MutableInteractionSource, liftPx: Float = 3f): Modifier = composed {
    val hovered by interaction.collectIsHoveredAsState()
    val ty by androidx.compose.animation.core.animateFloatAsState(if (hovered) -liftPx else 0f, tween(220, easing = NocturneEase))
    hoverable(interaction).graphicsLayer { translationY = ty * density }
}

/** apBob: gentle vertical float, 5 s loop. */
@Composable
fun bobOffset(amplitudePx: Float = 5f, periodMs: Int = 5000): Float {
    val t = rememberInfiniteTransition(label = "bob")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    return -(kotlin.math.sin(v * 2 * Math.PI).toFloat()) * amplitudePx
}

/** A 0..1 sawtooth used by the sheen sweep, conveyor and stripe animations. */
@Composable
fun loopPhase(periodMs: Int): Float {
    val t = rememberInfiniteTransition(label = "loop")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    return v
}

val StandardEasing = FastOutSlowInEasing
