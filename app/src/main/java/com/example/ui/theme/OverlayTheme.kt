package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * Couleurs des panneaux affichés hors de l’appli (menu d’outils, presse-papier, bulles,
 * gestes, popup Bluetooth) : exactement le thème choisi dans l’appli — couleurs du système
 * (Material You) ou palette, clair ou sombre — pour un rendu uniforme partout.
 */
object OverlayTheme {

    fun isDark(context: Context, settings: ThemeSettings): Boolean = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    fun scheme(context: Context, settings: ThemeSettings): ColorScheme {
        val dark = isDark(context, settings)
        return if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            paletteColorScheme(settings.fallbackPalette, dark)
        }
    }

    /** Couleurs prêtes à l’emploi pour les vues Android classiques. */
    data class Colors(
        val dark: Boolean,
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val secondary: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val surfaceContainerHighest: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outline: Int,
        val error: Int
    )

    fun colors(context: Context, settings: ThemeSettings): Colors {
        val s = scheme(context, settings)
        return Colors(
            dark = isDark(context, settings),
            primary = s.primary.toArgb(),
            onPrimary = s.onPrimary.toArgb(),
            primaryContainer = s.primaryContainer.toArgb(),
            onPrimaryContainer = s.onPrimaryContainer.toArgb(),
            secondary = s.secondary.toArgb(),
            secondaryContainer = s.secondaryContainer.toArgb(),
            onSecondaryContainer = s.onSecondaryContainer.toArgb(),
            surface = s.surface.toArgb(),
            surfaceContainer = s.surfaceContainer.toArgb(),
            surfaceContainerHigh = s.surfaceContainerHigh.toArgb(),
            surfaceContainerHighest = s.surfaceContainerHighest.toArgb(),
            onSurface = s.onSurface.toArgb(),
            onSurfaceVariant = s.onSurfaceVariant.toArgb(),
            outline = s.outline.toArgb(),
            error = s.error.toArgb()
        )
    }
}
