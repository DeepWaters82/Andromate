package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AccentPalette(
    val label: String,
    val previewColor: Color,
    val secondaryPreviewColor: Color
) {
    COSMIC_MINT(
        label = "Cosmique & Menthe",
        previewColor = Color(0xFF1E4FD6),
        secondaryPreviewColor = Color(0xFF10B981)
    ),
    INDIGO(
        label = "Indigo Nébuleuse",
        previewColor = Color(0xFF4F46E5),
        secondaryPreviewColor = Color(0xFF38BDF8)
    ),
    SAGE(
        label = "Menthe Boréale",
        previewColor = Color(0xFF0E8A6D),
        secondaryPreviewColor = Color(0xFF3B82F6)
    ),
    CORAL(
        label = "Aurore Corail",
        previewColor = Color(0xFFC84B31),
        secondaryPreviewColor = Color(0xFF4A6FA5)
    )
}

enum class ThemeMode(val label: String) {
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre")
}

data class ThemeSettings(
    val dynamicColor: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fallbackPalette: AccentPalette = AccentPalette.COSMIC_MINT
)

private val CosmicMintLight = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E4FF),
    onPrimaryContainer = Color(0xFF071B54),
    secondary = Color(0xFF0D8A6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDF7E2),
    onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8F3FF),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F7FD),
    onBackground = Color(0xFF121826),
    surface = Color(0xFFF4F7FD),
    onSurface = Color(0xFF121826),
    surfaceVariant = Color(0xFFDCE3F1),
    onSurfaceVariant = Color(0xFF414A5E),
    outline = Color(0xFF707B93),
    outlineVariant = Color(0xFFBFC8DC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF2FA),
    surfaceContainer = Color(0xFFE7EDF8),
    surfaceContainerHigh = Color(0xFFE0E7F4),
    surfaceContainerHighest = Color(0xFFD9E1F0)
)

private val CosmicMintDark = darkColorScheme(
    primary = Color(0xFFA8C7FF),
    onPrimary = Color(0xFF06256E),
    primaryContainer = Color(0xFF11399E),
    onPrimaryContainer = Color(0xFFD8E4FF),
    secondary = Color(0xFF6EE7B7),
    onSecondary = Color(0xFF003828),
    secondaryContainer = Color(0xFF00513C),
    onSecondaryContainer = Color(0xFFBDF7E2),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF003546),
    tertiaryContainer = Color(0xFF004D64),
    onTertiaryContainer = Color(0xFFC8F3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B101D),
    onBackground = Color(0xFFE0E6F5),
    surface = Color(0xFF0B101D),
    onSurface = Color(0xFFE0E6F5),
    surfaceVariant = Color(0xFF3E475C),
    onSurfaceVariant = Color(0xFFBFC8DC),
    outline = Color(0xFF8893AA),
    outlineVariant = Color(0xFF3E475C),
    surfaceContainerLowest = Color(0xFF070B14),
    surfaceContainerLow = Color(0xFF111829),
    surfaceContainer = Color(0xFF151D30),
    surfaceContainerHigh = Color(0xFF1E283E),
    surfaceContainerHighest = Color(0xFF28334D)
)

private val IndigoLight = lightColorScheme(
    primary = Color(0xFF4338CA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF0A006B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4EEFF),
    onSecondaryContainer = Color(0xFF001E30),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191B26),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191B26),
    surfaceVariant = Color(0xFFE1E2F0),
    onSurfaceVariant = Color(0xFF444654),
    outline = Color(0xFF757685),
    outlineVariant = Color(0xFFC5C6D6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FC),
    surfaceContainer = Color(0xFFECEEF8),
    surfaceContainerHigh = Color(0xFFE6E8F4),
    surfaceContainerHighest = Color(0xFFE0E2EE)
)

private val IndigoDark = darkColorScheme(
    primary = Color(0xFFC0C1FF),
    onPrimary = Color(0xFF1700A5),
    primaryContainer = Color(0xFF2B1DB2),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF00497D),
    onSecondaryContainer = Color(0xFFD4EEFF),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF11121B),
    onBackground = Color(0xFFE2E2EE),
    surface = Color(0xFF11121B),
    onSurface = Color(0xFFE2E2EE),
    surfaceVariant = Color(0xFF444654),
    onSurfaceVariant = Color(0xFFC5C6D6),
    outline = Color(0xFF8F90A0),
    outlineVariant = Color(0xFF444654),
    surfaceContainerLowest = Color(0xFF0C0D14),
    surfaceContainerLow = Color(0xFF191B26),
    surfaceContainer = Color(0xFF1D1F2B),
    surfaceContainerHigh = Color(0xFF272936),
    surfaceContainerHighest = Color(0xFF323442)
)

private val CoralLight = lightColorScheme(
    primary = Color(0xFFB43B24),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0700),
    secondary = Color(0xFF77574F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD2),
    onSecondaryContainer = Color(0xFF2C1510),
    tertiary = Color(0xFF435E91),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E2FF),
    onTertiaryContainer = Color(0xFF001A41),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DDD8),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0ED),
    surfaceContainer = Color(0xFFFCEAE5),
    surfaceContainerHigh = Color(0xFFF6E4E0),
    surfaceContainerHighest = Color(0xFFF0DEDA)
)

private val CoralDark = darkColorScheme(
    primary = Color(0xFFFFB4A3),
    onPrimary = Color(0xFF631202),
    primaryContainer = Color(0xFF8E240E),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE7BDB3),
    onSecondary = Color(0xFF442A24),
    secondaryContainer = Color(0xFF5D3F39),
    onSecondaryContainer = Color(0xFFFFDAD2),
    tertiary = Color(0xFFADC6FF),
    onTertiary = Color(0xFF102F60),
    tertiaryContainer = Color(0xFF2B4678),
    onTertiaryContainer = Color(0xFFD8E2FF),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF0DEDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF0DEDA),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D3230)
)

private val SageLight = lightColorScheme(
    primary = Color(0xFF0E8A6D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3F7DF),
    onPrimaryContainer = Color(0xFF002118),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E5FF),
    onSecondaryContainer = Color(0xFF001B47),
    tertiary = Color(0xFF4D6357),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFE9D8),
    onTertiaryContainer = Color(0xFF0A2016),
    background = Color(0xFFF3FBF7),
    onBackground = Color(0xFF141E1A),
    surface = Color(0xFFF3FBF7),
    onSurface = Color(0xFF141E1A),
    surfaceVariant = Color(0xFFD9E6DF),
    onSurfaceVariant = Color(0xFF3E4A44),
    outline = Color(0xFF6E7A73),
    outlineVariant = Color(0xFFBDCAC3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF6F1),
    surfaceContainer = Color(0xFFE7F1EB),
    surfaceContainerHigh = Color(0xFFE0EBE5),
    surfaceContainerHighest = Color(0xFFDAE5DF)
)

private val SageDark = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFB3F7DF),
    secondary = Color(0xFFAEC7FF),
    onSecondary = Color(0xFF002D6F),
    secondaryContainer = Color(0xFF0E439B),
    onSecondaryContainer = Color(0xFFD8E5FF),
    tertiary = Color(0xFFB3CCBC),
    onTertiary = Color(0xFF1F352A),
    tertiaryContainer = Color(0xFF354B3F),
    onTertiaryContainer = Color(0xFFCFE9D8),
    background = Color(0xFF0C1411),
    onBackground = Color(0xFFDAE5DF),
    surface = Color(0xFF0C1411),
    onSurface = Color(0xFFDAE5DF),
    surfaceVariant = Color(0xFF3E4A44),
    onSurfaceVariant = Color(0xFFBDCAC3),
    outline = Color(0xFF88948D),
    outlineVariant = Color(0xFF3E4A44),
    surfaceContainerLowest = Color(0xFF080F0C),
    surfaceContainerLow = Color(0xFF141E1A),
    surfaceContainer = Color(0xFF18231E),
    surfaceContainerHigh = Color(0xFF222D28),
    surfaceContainerHighest = Color(0xFF2D3833)
)

fun paletteColorScheme(palette: AccentPalette, darkTheme: Boolean): ColorScheme =
    when (palette) {
        AccentPalette.COSMIC_MINT -> if (darkTheme) CosmicMintDark else CosmicMintLight
        AccentPalette.INDIGO -> if (darkTheme) IndigoDark else IndigoLight
        AccentPalette.CORAL -> if (darkTheme) CoralDark else CoralLight
        AccentPalette.SAGE -> if (darkTheme) SageDark else SageLight
    }
