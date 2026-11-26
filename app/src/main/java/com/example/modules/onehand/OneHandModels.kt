package com.example.modules.onehand

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
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
        description = "Écran précédent",
        icon = Icons.AutoMirrored.Rounded.ArrowBack
    ),
    HOME(
        label = "Écran d'accueil",
        shortLabel = "Accueil",
        description = "Écran d’accueil",
        icon = Icons.Rounded.Home
    ),
    RECENTS(
        label = "Récents",
        shortLabel = "Récents",
        description = "Applis récentes",
        icon = Icons.Rounded.ViewCarousel
    ),
    NOTIFICATIONS(
        label = "Notifications",
        shortLabel = "Notifs",
        description = "Volet de notifications",
        icon = Icons.Rounded.Notifications
    ),
    QUICK_SETTINGS(
        label = "Paramètres rapides",
        shortLabel = "Réglages",
        description = "Volet des réglages",
        icon = Icons.Rounded.Tune
    ),
    CLIPBOARD(
        label = "Presse-papier récent",
        shortLabel = "Copies",
        description = "Dernières copies",
        icon = Icons.Rounded.ContentPaste
    ),
    SCREENSHOT(
        label = "Capture d'écran",
        shortLabel = "Capture",
        description = "Android 9+",
        icon = Icons.Rounded.Screenshot,
        requiresApi28 = true
    ),
    LOCK_SCREEN(
        label = "Verrouiller l'écran",
        shortLabel = "Verrou",
        description = "Android 9 et plus",
        icon = Icons.Rounded.Lock,
        requiresApi28 = true
    ),
    POWER_MENU(
        label = "Menu d'alimentation",
        shortLabel = "Alim.",
        description = "Arrêt, redémarrage",
        icon = Icons.Rounded.PowerSettingsNew
    ),
    FLASHLIGHT(
        label = "Lampe torche",
        shortLabel = "Torche",
        description = "Flash on/off",
        icon = Icons.Rounded.FlashlightOn
    ),
    TOOLS_MENU(
        label = "Menu d’outils",
        shortLabel = "Outils",
        description = "Tous les outils",
        icon = Icons.Rounded.Apps
    ),
    PREVIOUS_APP(
        label = "Appli précédente",
        shortLabel = "Bascule",
        description = "Dernière appli",
        icon = Icons.Rounded.SwapHoriz
    ),
    SPLIT_SCREEN(
        label = "Écran partagé",
        shortLabel = "Partagé",
        description = "Deux applis",
        icon = Icons.Rounded.VerticalSplit
    ),
    PLAY_PAUSE(
        label = "Lecture / pause",
        shortLabel = "Lecture",
        description = "Média en cours",
        icon = Icons.Rounded.PlayArrow
    ),
    NEXT_TRACK(
        label = "Titre suivant",
        shortLabel = "Suivant",
        description = "Média",
        icon = Icons.Rounded.SkipNext
    ),
    PREVIOUS_TRACK(
        label = "Titre précédent",
        shortLabel = "Préc.",
        description = "Média",
        icon = Icons.Rounded.SkipPrevious
    ),
    VOLUME_PANEL(
        label = "Volume",
        shortLabel = "Volume",
        description = "Curseur du volume",
        icon = Icons.AutoMirrored.Rounded.VolumeUp
    ),
    CAFFEINE(
        label = "Caféine",
        shortLabel = "Caféine",
        description = "Écran toujours allumé",
        icon = Icons.Rounded.Coffee
    ),
    ROTATION(
        label = "Rotation auto",
        shortLabel = "Rotation",
        description = "On/off",
        icon = Icons.Rounded.ScreenRotation
    ),
    WIFI(
        label = "Wi-Fi",
        shortLabel = "Wi-Fi",
        description = "On/off",
        icon = Icons.Rounded.Wifi
    ),
    BLUETOOTH(
        label = "Bluetooth",
        shortLabel = "Bluetooth",
        description = "On/off",
        icon = Icons.Rounded.Bluetooth
    ),
    MOBILE_DATA(
        label = "Données mobiles",
        shortLabel = "Données",
        description = "On/off",
        icon = Icons.Rounded.SwapVert
    ),
    HOTSPOT(
        label = "Point d’accès",
        shortLabel = "Partage",
        description = "Point d’accès mobile",
        icon = Icons.Rounded.WifiTethering
    ),
    AIRPLANE(
        label = "Mode avion",
        shortLabel = "Avion",
        description = "Hors ligne",
        icon = Icons.Rounded.AirplanemodeActive
    ),
    DND(
        label = "Ne pas déranger",
        shortLabel = "NPD",
        description = "On/off",
        icon = Icons.Rounded.DoNotDisturbOn
    ),
    SILENT(
        label = "Mode silencieux",
        shortLabel = "Silence",
        description = "Sonnerie coupée",
        icon = Icons.Rounded.NotificationsOff
    ),
    VIBRATE(
        label = "Mode vibreur",
        shortLabel = "Vibreur",
        description = "Sonnerie en vibreur",
        icon = Icons.Rounded.Vibration
    ),
    NONE(
        label = "Aucune action",
        shortLabel = "Aucun",
        description = "Désactivé",
        icon = Icons.Rounded.Block
    );

    fun isSupportedOnDevice(): Boolean {
        return !requiresApi28 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    /** Contrôles audio : regroupés dans la zone média du menu d’outils. */
    val isMedia: Boolean get() = this == PLAY_PAUSE || this == NEXT_TRACK || this == PREVIOUS_TRACK

    /** Peut figurer dans la grille du menu d’outils (la navigation reste aux gestes). */
    val isToolCandidate: Boolean get() = this !in NOT_TOOLS

    /** Interrupteur : son bouton montre l’état (allumé / éteint). */
    val isToggle: Boolean get() = this in TOGGLES

    /** Basculé via la tuile du volet de réglages rapides (pas d’API publique). */
    val usesQuickTile: Boolean get() = this == WIFI || this == BLUETOOTH || this == MOBILE_DATA ||
        this == HOTSPOT || this == AIRPLANE

    companion object {
        private val NOT_TOOLS = setOf(NONE, TOOLS_MENU, RECENTS, PREVIOUS_APP, HOME, BACK, SPLIT_SCREEN, VOLUME_PANEL)
        private val TOGGLES = setOf(
            FLASHLIGHT, CAFFEINE, ROTATION, WIFI, BLUETOOTH, MOBILE_DATA, HOTSPOT, AIRPLANE, DND, SILENT, VIBRATE
        )
    }
}

/** Taille d’un bouton du menu : une case, pilule horizontale ou verticale de deux cases. */
enum class ToolSpan(val label: String, val columns: Int, val rows: Int) {
    SINGLE("1 case", 1, 1),
    WIDE("Pilule horizontale", 2, 1),
    TALL("Pilule verticale", 1, 2);

    fun next(): ToolSpan = entries[(ordinal + 1) % entries.size]
}

/** Icône de l’appli audio : celle de l’appli, Material You (monochrome) ou un pack installé. */
object SourceIconStyle {
    const val APP = ""
    const val THEMED = "@themed"
}

enum class ToolIconSize(val label: String, val bubbleDp: Float, val iconDp: Float) {
    SMALL("Petit", 44f, 20f),
    MEDIUM("Moyen", 52f, 24f),
    LARGE("Grand", 64f, 30f)
}

/** Forme des boutons du menu d’outils (comme le panneau de raccourcis). */
enum class ToolShape(val label: String) {
    CIRCLE("Rond"),
    SQUIRCLE("Carré arrondi"),
    PILL("Pastille")
}

/** Élément de la grille du menu d’outils : un outil ou une application. */
sealed class ToolItem {
    abstract val key: String

    data class Action(val action: GestureAction) : ToolItem() {
        override val key: String get() = action.name
    }

    data class App(val app: AppShortcut) : ToolItem() {
        override val key: String get() = "app:${app.packageName}"
    }
}

/** Réglages du menu d’outils : outils et ordre, forme, taille, colonnes, libellés, zone audio. */
data class ToolsMenuConfig(
    val hiddenTools: Set<GestureAction> = emptySet(),
    val iconSize: ToolIconSize = ToolIconSize.MEDIUM,
    val showLabels: Boolean = true,
    val showMediaZone: Boolean = true,
    /**
     * Ordre choisi, par clé d’élément ([ToolItem.key] : nom de l’outil ou « app:paquet ») ;
     * les éléments absents (nouveaux) sont ajoutés à la fin. Par défaut : Wi-Fi et Bluetooth en tête.
     */
    val order: List<String> = listOf(GestureAction.WIFI.name, GestureAction.BLUETOOTH.name),
    val shape: ToolShape = ToolShape.CIRCLE,
    val columns: Int = 4,
    /** Taille par clé d’élément. Par défaut, Wi-Fi et Bluetooth sont des pilules. */
    val spans: Map<String, ToolSpan> = mapOf(GestureAction.WIFI.name to ToolSpan.WIDE, GestureAction.BLUETOOTH.name to ToolSpan.WIDE),
    /** [SourceIconStyle.APP], [SourceIconStyle.THEMED] ou le paquet d’un pack d’icônes. */
    val sourceIcon: String = SourceIconStyle.APP,
    /** Applications ajoutées au menu : des outils comme les autres (place, taille). */
    val apps: List<AppShortcut> = emptyList()
) {
    fun spanOf(key: String): ToolSpan = spans[key] ?: ToolSpan.SINGLE
    fun spanOf(tool: GestureAction): ToolSpan = spanOf(tool.name)

    /**
     * Ordre complet après déplacement dans le menu : les éléments visibles prennent le nouvel
     * ordre, les outils masqués gardent leur place.
     */
    fun withVisibleOrder(visible: List<String>): ToolsMenuConfig {
        val queue = ArrayDeque(visible)
        val merged = orderedItems().map { item ->
            if (item.key !in visible) item.key else queue.removeFirstOrNull() ?: item.key
        }
        return copy(order = merged)
    }

    fun encodeSpans(): String = spans.filterValues { it != ToolSpan.SINGLE }
        .entries.joinToString(",") { "${it.key}=${it.value.name}" }

    /** Tous les éléments proposables (outils puis applis), dans l’ordre choisi, masqués compris. */
    fun orderedItems(): List<ToolItem> {
        val candidates: List<ToolItem> = GestureAction.entries.filter {
            it.isToolCandidate && it.isSupportedOnDevice() && !(showMediaZone && it.isMedia)
        }.map { ToolItem.Action(it) } + apps.map { ToolItem.App(it) }
        val byKey = candidates.associateBy { it.key }
        val known = order.mapNotNull { byKey[it] }.distinctBy { it.key }
        val knownKeys = known.map { it.key }.toSet()
        return known + candidates.filter { it.key !in knownKeys }
    }

    /** Outils seuls, dans l’ordre (masqués compris). */
    fun orderedCandidates(): List<GestureAction> = orderedItems().mapNotNull { (it as? ToolItem.Action)?.action }

    /** Éléments de la grille (outils non masqués et applis). */
    fun gridItems(): List<ToolItem> = orderedItems().filter { it !is ToolItem.Action || it.action !in hiddenTools }

    fun encodeHidden(): String = hiddenTools.joinToString(",") { it.name }
    fun encodeOrder(): String = order.joinToString(",")

    companion object {
        fun decodeHidden(raw: String?): Set<GestureAction> = decodeList(raw).toSet()

        fun decodeOrder(raw: String?): List<String> =
            raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /** « clé=TAILLE » (et l’ancien format « NOM:TAILLE »). */
        fun decodeSpans(raw: String?): Map<String, ToolSpan> =
            raw.orEmpty().split(',').mapNotNull { pair ->
                val cut = maxOf(pair.lastIndexOf('='), pair.lastIndexOf(':'))
                if (cut <= 0) return@mapNotNull null
                val key = pair.substring(0, cut).trim()
                val size = ToolSpan.entries.firstOrNull { it.name == pair.substring(cut + 1).trim() } ?: return@mapNotNull null
                key to size
            }.toMap()

        fun decodeList(raw: String?): List<GestureAction> =
            raw.orEmpty().split(',').mapNotNull { name ->
                GestureAction.entries.firstOrNull { it.name == name.trim() }
            }.distinct()
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
    /** Activé : toute modification d’une poignée s’applique aussi à l’autre (gestes, taille, position, couleur). */
    val syncHandles: Boolean = true,
    val showGestureLabel: Boolean = false,
    /** Icône de l’action dans la flèche une fois le geste validé (sinon flèche seule, comme Android). */
    val showGestureIcon: Boolean = true,
    val triggerDistanceDp: Float = 54f,
    val longTriggerDistanceDp: Float = 126f,
    val leftHandle: HandleConfig = HandleConfig(),
    val rightHandle: HandleConfig = HandleConfig(),
    val tools: ToolsMenuConfig = ToolsMenuConfig()
) {
    fun handleFor(side: HandleSide): HandleConfig =
        if (side == HandleSide.LEFT) leftHandle else rightHandle

    fun withHandle(side: HandleSide, handle: HandleConfig): OneHandConfig =
        if (side == HandleSide.LEFT) copy(leftHandle = handle) else copy(rightHandle = handle)

    /**
     * Recopie toute la poignée [sourceSide] sur l’autre côté, sauf son interrupteur
     * marche/arrêt : on peut garder une seule poignée visible tout en restant synchronisé.
     */
    fun withSyncedHandles(sourceSide: HandleSide): OneHandConfig {
        val source = handleFor(sourceSide)
        val targetSide = if (sourceSide == HandleSide.LEFT) HandleSide.RIGHT else HandleSide.LEFT
        return withHandle(targetSide, source.copy(enabled = handleFor(targetSide).enabled))
    }

    /** Modifie une poignée et, si la synchronisation est active, l’autre aussi. */
    fun updatingHandle(side: HandleSide, transform: (HandleConfig) -> HandleConfig): OneHandConfig {
        val updated = withHandle(side, transform(handleFor(side)))
        return if (syncHandles) updated.withSyncedHandles(side) else updated
    }
}
