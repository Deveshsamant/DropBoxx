package com.dropnest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dropnest.ui.motion.hoverLift
import com.dropnest.ui.theme.N

enum class NButtonStyle { Primary, Secondary, Ghost, Nav }

/**
 * Nocturne `.btn`: transparent pill with a 1px border; Primary = accent text + accent border,
 * Secondary = divider border, Ghost = accent text, Nav = sidebar/bottom-bar item.
 */
@Composable
fun NButton(
    text: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NButtonStyle = NButtonStyle.Secondary,
    icon: ImageVector? = null,
    iconSize: Dp = 15.dp,
    enabled: Boolean = true,
    active: Boolean = false,
    fontSize: Int = 12,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = N
    val interaction = remember { MutableInteractionSource() }
    val fg = when {
        !enabled -> t.muted
        active || style == NButtonStyle.Primary || style == NButtonStyle.Ghost -> t.accent
        else -> t.text
    }
    val border = when (style) {
        NButtonStyle.Primary -> t.accent
        NButtonStyle.Secondary -> t.line.copy(alpha = 0.9f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(if (active) t.accentSoft else Color.Transparent, tween(180), label = "btnbg")
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(interaction, indication = ripple(color = t.accent), enabled = enabled, onClick = onClick)
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (icon != null) Icon(icon, contentDescription = text, modifier = Modifier.size(iconSize), tint = fg)
            if (text != null) Text(text, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = (fontSize + 2).sp)
            trailing?.invoke(this)
        }
    }
}

/** Square icon button (`.btn-icon`), 36-44 px. */
@Composable
fun NIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 16.dp,
    style: NButtonStyle = NButtonStyle.Secondary,
    contentDescription: String? = null,
) {
    val t = N
    val border = when (style) {
        NButtonStyle.Secondary -> t.line.copy(alpha = 0.9f)
        NButtonStyle.Primary -> t.accent
        else -> Color.Transparent
    }
    val fg = if (style == NButtonStyle.Secondary) t.text else t.accent
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).border(1.dp, border, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(iconSize)) }
}

/** Elevated surface: `background: surface; box-shadow: shadow-sm` (hairline edge). `lift` enables the hover raise. */
@Composable
fun NCard(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    color: Color = N.surface,
    elevation: Int = 1,
    lift: Boolean = false,
    padding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = N
    val shape = RoundedCornerShape(radius)
    val interaction = remember { MutableInteractionSource() }
    val base = if (lift) modifier.hoverLift(interaction) else modifier
    Column(
        base
            .then(if (elevation >= 2) Modifier.shadow(if (elevation >= 3) 22.dp else 8.dp, shape, ambientColor = t.shadow, spotColor = t.shadow) else Modifier)
            .clip(shape)
            .background(color)
            .border(1.dp, if (elevation >= 2) t.edgeStrong else t.edge, shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = ripple(color = t.accent), onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Small accent-tinted square with an icon — used for list leading icons and headers. */
@Composable
fun AccentTile(icon: ImageVector, size: Dp = 36.dp, iconSize: Dp = 18.dp, radius: Dp = 9.dp, muted: Boolean = false, modifier: Modifier = Modifier) {
    val t = N
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius))
            .background(if (muted) t.soft else t.accentSoft)
            .border(1.dp, if (muted) Color.Transparent else t.accentLine, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (muted) t.muted else t.accent, modifier = Modifier.size(iconSize)) }
}

/** Uppercase letter-spaced label in accent (`card-kicker`) or muted (section captions). */
@Composable
fun Kicker(text: String, accent: Boolean = true, modifier: Modifier = Modifier, size: Int = 10) {
    Text(text.uppercase(), color = if (accent) N.accent else N.muted, fontSize = size.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium, modifier = modifier, lineHeight = (size + 4).sp)
}

@Composable
fun Heading(text: String, size: Int = 26, modifier: Modifier = Modifier) {
    Text(text, color = N.text, fontSize = size.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp, lineHeight = (size + 4).sp, modifier = modifier)
}

@Composable
fun Muted(text: String, size: Int = 12, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, color = N.muted, fontSize = size.sp, lineHeight = (size + 5).sp, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** `.tag` pill. */
@Composable
fun Tag(text: String, outline: Boolean = true, modifier: Modifier = Modifier) {
    val t = N
    Text(
        text, fontSize = 10.sp, letterSpacing = 0.2.sp, lineHeight = 12.sp, color = if (outline) t.accent else t.text,
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .then(if (outline) Modifier.border(1.dp, t.accent, RoundedCornerShape(6.dp)) else Modifier.background(t.accentDeep))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
