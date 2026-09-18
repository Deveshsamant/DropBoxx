package com.dropnest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dropnest.ui.motion.NocturneEase
import com.dropnest.ui.theme.N

/** 44x26 pill switch with a knob that slides in 240 ms (the prototype's `data-switch`). */
@Composable
fun NSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, width: Dp = 44.dp, height: Dp = 26.dp) {
    val t = N
    val knob = height - 6.dp
    val x by animateDpAsState(if (checked) width - knob - 3.dp else 3.dp, tween(240, easing = NocturneEase), label = "knob")
    val track by animateColorAsState(if (checked) t.accentSoft else t.soft, tween(240), label = "track")
    val border by animateColorAsState(if (checked) t.accentLine else t.line, tween(240), label = "border")
    val fill by animateColorAsState(if (checked) t.accent else t.muted, tween(240), label = "fill")
    Box(
        modifier.size(width, height).clip(CircleShape).background(track).border(1.dp, border, CircleShape)
            .clickable { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(knob).clip(CircleShape).background(fill))
    }
}

/** `.seg`: bordered segmented control; the active option gets accent text + soft fill. */
@Composable
fun <T> NSeg(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    val t = N
    Row(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, t.line, RoundedCornerShape(8.dp))) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            Box(
                Modifier.then(if (fill) Modifier.weight(1f) else Modifier)
                    .background(if (on) t.accentSoft else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) t.accent else t.text, fontSize = 12.sp, lineHeight = 14.sp) }
            if (i < options.lastIndex) Box(Modifier.width(1.dp).height(34.dp).background(t.line))
        }
    }
}

/** `.input`: surface field with a divider border that turns accent on focus. */
@Composable
fun NField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 38.dp,
    numeric: Boolean = false,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = N
    androidx.compose.foundation.layout.Column(modifier) {
        if (label != null) Text(label, color = t.muted, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
        Row(
            Modifier.fillMaxWidth().height(minHeight).clip(RoundedCornerShape(8.dp)).background(t.surface).border(1.dp, t.line, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, color = t.muted, fontSize = 13.sp)
                BasicTextField(
                    value, onValueChange, singleLine = singleLine,
                    textStyle = TextStyle(color = t.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(t.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
    }
}

/** Panel with the bg2 tone and a hairline — the "well" behind the nest, lists, sheets. */
@Composable
fun Well(modifier: Modifier = Modifier, radius: Dp = 14.dp, shape: Shape = RoundedCornerShape(radius), glow: Boolean = false, content: @Composable () -> Unit) {
    val t = N
    Box(
        modifier.clip(shape).background(t.bg2).border(1.dp, t.edge, shape)
            .then(if (glow) Modifier.background(Brush.radialGradient(listOf(t.accentSoft, Color.Transparent), radius = 700f)) else Modifier),
    ) { content() }
}

/** Thin horizontal gauge (storage / progress). */
@Composable
fun Gauge(fraction: Float, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    val t = N
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = NocturneEase), label = "gauge")
    Box(modifier.height(height).clip(CircleShape).background(t.soft)) {
        Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.001f)).height(height).clip(CircleShape).background(Brush.horizontalGradient(listOf(t.accentDeep, t.accent))))
    }
}

@Composable
fun VSpace(dp: Dp) = Spacer(Modifier.height(dp))

@Composable
fun HSpace(dp: Dp) = Spacer(Modifier.width(dp))
