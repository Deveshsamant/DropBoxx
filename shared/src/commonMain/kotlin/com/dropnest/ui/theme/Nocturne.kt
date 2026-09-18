package com.dropnest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dropnest.domain.ThemeMode

/**
 * Nocturne design-system tokens (from the Claude Design prototype `DropNest.dc.html`).
 * Everything in the UI reads these through [LocalNocturne]; Material components get a
 * colour scheme derived from the same values so dialogs, switches etc. match.
 */
data class NocturneTokens(
    val dark: Boolean,
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val soft: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentLine: Color,
    val accentDeep: Color,
    val ok: Color,
    val bad: Color,
    /** Hairline ring around elevated surfaces (shadow-sm/md/lg all start with a 1px edge). */
    val edge: Color,
    val edgeStrong: Color,
    /** Ambient shadow colour for md/lg elevation. */
    val shadow: Color,
)

val NocturneDark = NocturneTokens(
    dark = true,
    bg = Color(0xFF161826), bg2 = Color(0xFF1B1D2C), surface = Color(0xFF232532), surface2 = Color(0xFF2A2D3C),
    text = Color(0xFFE9E9ED), muted = Color(0xFF9397AB), line = Color(0xFF3F424D), soft = Color(0x14E9E9ED),
    accent = Color(0xFF9184D9), accentSoft = Color(0x249184D9), accentLine = Color(0x579184D9), accentDeep = Color(0xFF423A6A),
    ok = Color(0xFF8FB59A), bad = Color(0xFFE0A3B4),
    edge = Color(0xFF3F424D), edgeStrong = Color(0xFF595D6C), shadow = Color(0x80000000),
)

val NocturneLight = NocturneTokens(
    dark = false,
    bg = Color(0xFFECEEFB), bg2 = Color(0xFFE4E7F5), surface = Color(0xFFF8F9FF), surface2 = Color(0xFFEEF0FB),
    text = Color(0xFF292B31), muted = Color(0xFF595D6C), line = Color(0xFFCFD3E5), soft = Color(0x12292B31),
    accent = Color(0xFF6D61B4), accentSoft = Color(0x1F6D61B4), accentLine = Color(0x4D6D61B4), accentDeep = Color(0xFFB5ABFC),
    ok = Color(0xFF4F7A5C), bad = Color(0xFFA24D62),
    edge = Color(0xFFCFD3E5), edgeStrong = Color(0xFFD8DCEC), shadow = Color(0x1A292B31),
)

val LocalNocturne = staticCompositionLocalOf { NocturneDark }

/** Shorthand used everywhere: `val t = N`. */
val N: NocturneTokens
    @Composable get() = LocalNocturne.current

@Composable
fun DropNestTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK
    val t = if (dark) NocturneDark else NocturneLight
    val scheme = if (dark) darkColorScheme(
        primary = t.accent, onPrimary = t.bg, primaryContainer = t.accentDeep, onPrimaryContainer = t.text,
        secondary = t.ok, onSecondary = t.bg, background = t.bg, onBackground = t.text,
        surface = t.surface, onSurface = t.text, surfaceVariant = t.surface2, onSurfaceVariant = t.muted,
        outline = t.line, error = t.bad, onError = t.bg, surfaceContainer = t.surface, surfaceContainerHigh = t.surface2,
    ) else lightColorScheme(
        primary = t.accent, onPrimary = Color.White, primaryContainer = t.accentSoft.compositeOverWhite(), onPrimaryContainer = t.text,
        secondary = t.ok, onSecondary = Color.White, background = t.bg, onBackground = t.text,
        surface = t.surface, onSurface = t.text, surfaceVariant = t.surface2, onSurfaceVariant = t.muted,
        outline = t.line, error = t.bad, onError = Color.White, surfaceContainer = t.surface, surfaceContainerHigh = t.surface2,
    )
    CompositionLocalProvider(LocalNocturne provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun Color.compositeOverWhite(): Color = Color(
    red = red * alpha + (1 - alpha), green = green * alpha + (1 - alpha), blue = blue * alpha + (1 - alpha), alpha = 1f,
)
