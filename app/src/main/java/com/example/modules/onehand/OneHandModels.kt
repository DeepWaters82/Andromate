package com.example.modules.onehand

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.ui.graphics.vector.ImageVector

enum class HandleSide(val label: String, val shortLabel: String) {
    LEFT("Poignée gauche", "Gauche"),
    RIGHT("Poignée droite", "Droite")
}

enum class SwipeDirection(val label: String) {
    HORIZONTAL("Horizontal"),
    DIAGONAL_UP("Diagonal haut"),
    DIAGONAL_DOWN("Diagonal bas")
}

enum class SwipeLength(val label: String) {
    SHORT("Glissement court"),
    LONG("Glissement long")
}

enum class GestureAction(
    val label: String,
    val shortLabel: String,
    val description: String,
    val icon: ImageVector,
    val requiresApi28: Boolean = false
) {
    BACK(
        label = "Retour",
        shortLabel = "Retour",
        description = "Revenir à l'écran ou à l'application précédente",
        icon = Icons.AutoMirrored.Rounded.ArrowBack
    ),
    HOME(
        label = "Écran d'accueil",
        shortLabel = "Accueil",
        description = "Retourner directement au lanceur d'applications",
        icon = Icons.Rounded.Home
    ),
    RECENTS(
        label = "Applications récentes",
        shortLabel = "Récents",
        description = "Ouvrir le sélecteur de tâches récentes",
        icon = Icons.Rounded.ViewCarousel
    ),
    NOTIFICATIONS(
        label = "Panneau de notifications",
        shortLabel = "Notifs",
        description = "Dérouler le tiroir de notifications",
        icon = Icons.Rounded.Notifications
    ),
    QUICK_SETTINGS(
        label = "Paramètres rapides",
        shortLabel = "Raccourcis",
        description = "Ouvrir les tuiles de réglages rapides",
        icon = Icons.Rounded.Tune
    ),
    CLIPBOARD(
        label = "Presse-papier récent",
        shortLabel = "Presse-pap.",
        description = "Afficher les derniers éléments du presse-papier pour copier en 1 tap",
        icon = Icons.Rounded.ContentPaste
    ),
    SCREENSHOT(
        label = "Capture d'écran",
        shortLabel = "Capture",
        description = "Prendre une capture d'écran instantanée (Android 9+)",
        icon = Icons.Rounded.Screenshot,
        requiresApi28 = true
    ),
    LOCK_SCREEN(
        label = "Verrouiller l'écran",
        shortLabel = "Verrou",
        description = "Éteindre et verrouiller l'appareil (Android 9+)",
        icon = Icons.Rounded.Lock,
        requiresApi28 = true
    ),
    POWER_MENU(
        label = "Menu d'alimentation",
        shortLabel = "Alim.",
        description = "Afficher les options d'arrêt et de redémarrage",
        icon = Icons.Rounded.PowerSettingsNew
    ),
    FLASHLIGHT(
        label = "Lampe torche",
        shortLabel = "Torche",
        description = "Allumer ou éteindre le flash de l'appareil",
        icon = Icons.Rounded.FlashlightOn
    ),
    NONE(
        label = "Aucune action",
        shortLabel = "Aucun",
        description = "Ne rien déclencher sur cette direction",
        icon = Icons.Rounded.Block
    );

    fun isSupportedOnDevice(): Boolean {
        return !requiresApi28 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
}

enum class HandleColorStyle(val label: String) {
    DYNAMIC_PRIMARY("Primaire"),
    DYNAMIC_SECONDARY("Secondaire"),
    DYNAMIC_TERTIARY("Tertiaire"),
    MINIMAL_NEUTRAL("Discret")
}

data class HandleConfig(
    val enabled: Boolean = true,
    val verticalPositionPercent: Float = 0.62f,
    val heightPercent: Float = 0.28f,
    val widthDp: Float = 30f,
    val opacity: Float = 0.52f,
    val colorStyle: HandleColorStyle = HandleColorStyle.DYNAMIC_PRIMARY,
    val shortHorizontal: GestureAction = GestureAction.BACK,
    val shortDiagonalUp: GestureAction = GestureAction.RECENTS,
    val shortDiagonalDown: GestureAction = GestureAction.NOTIFICATIONS,
    val longHorizontal: GestureAction = GestureAction.HOME,
    val longDiagonalUp: GestureAction = GestureAction.QUICK_SETTINGS,
    val longDiagonalDown: GestureAction = GestureAction.CLIPBOARD
) {
    fun actionFor(direction: SwipeDirection, length: SwipeLength): GestureAction {
        return when (length) {
            SwipeLength.SHORT -> when (direction) {
                SwipeDirection.HORIZONTAL -> shortHorizontal
                SwipeDirection.DIAGONAL_UP -> shortDiagonalUp
                SwipeDirection.DIAGONAL_DOWN -> shortDiagonalDown
            }
            SwipeLength.LONG -> when (direction) {
                SwipeDirection.HORIZONTAL -> longHorizontal
                SwipeDirection.DIAGONAL_UP -> longDiagonalUp
                SwipeDirection.DIAGONAL_DOWN -> longDiagonalDown
            }
        }
    }

    fun withAction(
        direction: SwipeDirection,
        length: SwipeLength,
        action: GestureAction
    ): HandleConfig {
        return when (length) {
            SwipeLength.SHORT -> when (direction) {
                SwipeDirection.HORIZONTAL -> copy(shortHorizontal = action)
                SwipeDirection.DIAGONAL_UP -> copy(shortDiagonalUp = action)
                SwipeDirection.DIAGONAL_DOWN -> copy(shortDiagonalDown = action)
            }
            SwipeLength.LONG -> when (direction) {
                SwipeDirection.HORIZONTAL -> copy(longHorizontal = action)
                SwipeDirection.DIAGONAL_UP -> copy(longDiagonalUp = action)
                SwipeDirection.DIAGONAL_DOWN -> copy(longDiagonalDown = action)
            }
        }
    }
}

data class OneHandConfig(
    val masterEnabled: Boolean = true,
    val longSwipeEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val disableInLandscape: Boolean = true,
    val preventGestureDuplication: Boolean = false,
    val showGestureLabel: Boolean = false,
    val triggerDistanceDp: Float = 54f,
    val longTriggerDistanceDp: Float = 126f,
    val leftHandle: HandleConfig = HandleConfig(),
    val rightHandle: HandleConfig = HandleConfig()
) {
    fun handleFor(side: HandleSide): HandleConfig =
        if (side == HandleSide.LEFT) leftHandle else rightHandle

    fun withHandle(side: HandleSide, handle: HandleConfig): OneHandConfig =
        if (side == HandleSide.LEFT) copy(leftHandle = handle) else copy(rightHandle = handle)

    fun withDuplicatedGesturesFrom(sourceSide: HandleSide): OneHandConfig {
        val source = handleFor(sourceSide)
        val targetSide = if (sourceSide == HandleSide.LEFT) HandleSide.RIGHT else HandleSide.LEFT
        val syncedTarget = handleFor(targetSide).copy(
            shortHorizontal = source.shortHorizontal,
            shortDiagonalUp = source.shortDiagonalUp,
            shortDiagonalDown = source.shortDiagonalDown,
            longHorizontal = source.longHorizontal,
            longDiagonalUp = source.longDiagonalUp,
            longDiagonalDown = source.longDiagonalDown
        )
        return withHandle(targetSide, syncedTarget)
    }
}
