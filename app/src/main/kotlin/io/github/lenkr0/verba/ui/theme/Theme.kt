package io.github.lenkr0.verba.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import io.github.lenkr0.verba.settings.ThemeMode

/** True in E-ink mode: screens skip animations and use borders instead of shades. */
val LocalEInkMode = staticCompositionLocalOf { false }

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerbaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    eInk: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        eInk -> eInkColors(darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = if (eInk) EInkTypography else VerbaTypography) {
        CompositionLocalProvider(
            LocalEInkMode provides eInk,
            // Ripples are animations, and e-ink panels leave ghosts of them
            LocalRippleConfiguration provides if (eInk) null else RippleConfiguration(),
            LocalMinimumInteractiveComponentSize provides if (eInk) 56.dp else 48.dp,
            content = content,
        )
    }
}
