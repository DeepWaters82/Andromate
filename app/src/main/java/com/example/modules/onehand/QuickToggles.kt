package com.example.modules.onehand

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Interrupteurs du menu d’outils.
 * - Ne pas déranger, silencieux, vibreur : API directes (accès « Ne pas déranger » requis).
 * - Wi-Fi, Bluetooth, données, point d’accès, mode avion : Android interdit aux applis de les
 *   basculer ; on appuie donc sur la tuile correspondante du volet de réglages rapides, que
 *   l’on ouvre puis referme aussitôt. Seules ces tuiles sont lues, rien d’autre à l’écran.
 */
object QuickToggles {

    private const val SYSTEM_UI = "com.android.systemui"

    /** État actuel de l’interrupteur, ou null s’il est inconnu. */
    fun state(context: Context, tool: GestureAction): Boolean? = runCatching {
        when (tool) {
            GestureAction.FLASHLIGHT -> com.example.modules.automate.TorchMonitor.isOn
            GestureAction.CAFFEINE -> com.example.modules.caffeine.CaffeineKeepAwakeService.isRunning
            GestureAction.ROTATION ->
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
            GestureAction.WIFI ->
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled
            GestureAction.BLUETOOTH ->
                context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled
            GestureAction.MOBILE_DATA -> Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1 &&
                Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 0
            GestureAction.HOTSPOT -> hotspotOn(context)
            GestureAction.AIRPLANE -> Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            GestureAction.DND -> context.getSystemService(NotificationManager::class.java)
                ?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL }
            GestureAction.SILENT -> audio(context)?.ringerMode == AudioManager.RINGER_MODE_SILENT
            GestureAction.VIBRATE -> audio(context)?.ringerMode == AudioManager.RINGER_MODE_VIBRATE
            else -> null
        }
    }.getOrNull()

    /** Nom du réseau Wi-Fi ou des appareils Bluetooth connectés, sinon null. */
    fun detail(context: Context, tool: GestureAction): String? = runCatching {
        when (tool) {
            GestureAction.WIFI -> {
                if (state(context, tool) != true) return@runCatching null
                if (!com.example.modules.automate.AutomateModule.isWifiTransportActive(context)) return@runCatching null
                ConnectionNames.currentWifiName(context) ?: "Connecté"
            }
            GestureAction.BLUETOOTH -> {
                if (state(context, tool) != true) return@runCatching null
                connectedBluetoothNames(context).joinToString(", ").ifBlank { null }
            }
            else -> null
        }
    }.getOrNull()

    /** Appareils Bluetooth connectés (voir [ConnectionNames]). */
    fun connectedBluetoothNames(context: Context): List<String> = ConnectionNames.connectedBluetoothNames(context)

    /** Wi-Fi connecté mais nom inconnu (pas de localisation) : il faudra le lire sur la tuile. */
    fun wifiNameMissing(context: Context): Boolean =
        state(context, GestureAction.WIFI) == true &&
            com.example.modules.automate.AutomateModule.isWifiTransportActive(context) &&
            ConnectionNames.currentWifiName(context) == null

    private fun hotspotOn(context: Context): Boolean? = runCatching {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as Boolean
    }.getOrNull()

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun hasPolicyAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun openPolicySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Bascule Ne pas déranger, silencieux ou vibreur. Renvoie le nouvel état, ou null si l’accès
     * « Ne pas déranger » manque (l’écran de réglage est alors ouvert).
     */
    fun toggleDirect(context: Context, tool: GestureAction): Boolean? {
        if (!hasPolicyAccess(context)) {
            openPolicySettings(context)
            return null
        }
        return runCatching {
            when (tool) {
                GestureAction.DND -> {
                    val nm = context.getSystemService(NotificationManager::class.java)!!
                    val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                    nm.setInterruptionFilter(
                        if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    )
                    !on
                }
                GestureAction.SILENT, GestureAction.VIBRATE -> {
                    val am = audio(context)!!
                    val target = if (tool == GestureAction.SILENT) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_VIBRATE
                    if (am.ringerMode == target) {
                        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                        false
                    } else {
                        am.ringerMode = target
                        true
                    }
                }
                else -> null
            }
        }.getOrNull()
    }

    /** Écran de réglage quand la tuile est introuvable (volet personnalisé, autre marque…). */
    fun openSettingsFor(context: Context, tool: GestureAction) {
        val action = when (tool) {
            GestureAction.WIFI -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            GestureAction.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            GestureAction.MOBILE_DATA ->
                if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_DATA_ROAMING_SETTINGS
            GestureAction.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            else -> Settings.ACTION_WIRELESS_SETTINGS
        }
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Libellés de la tuile : ceux du volet système du téléphone (langue et fabricant), puis
     * les noms connus. Wi-Fi et données : aussi la tuile « Internet » d’Android 12+.
     */
    private fun labelsFor(context: Context, tool: GestureAction): List<String> {
        val internet = if (tool == GestureAction.WIFI || tool == GestureAction.MOBILE_DATA) {
            SystemLabels.internetTileLabels(context)
        } else {
            emptyList()
        }
        return (SystemLabels.tileLabels(context, tool) + fallbackLabels(tool) + internet).distinct()
    }

    /** Libellés de l’interrupteur dans la fenêtre ouverte par la tuile (sans « Internet »). */
    private fun dialogLabelsFor(context: Context, tool: GestureAction): List<String> =
        (SystemLabels.tileLabels(context, tool) + fallbackLabels(tool)).distinct()

    // Noms connus (Samsung FR, AOSP FR/EN), comparés en minuscules sans ponctuation.
    private fun fallbackLabels(tool: GestureAction): List<String> = when (tool) {
        GestureAction.WIFI -> listOf("wi-fi", "wifi", "wlan")
        GestureAction.BLUETOOTH -> listOf("bluetooth")
        GestureAction.MOBILE_DATA -> listOf("données mobiles", "mobile data", "données cellulaires")
        GestureAction.HOTSPOT -> listOf(
            "pt accès mobile", "point d'accès", "point d’accès", "partage de connexion", "hotspot", "mobile hotspot"
        )
        GestureAction.AIRPLANE -> listOf("mode hors ligne", "mode avion", "airplane mode", "flight mode")
        else -> emptyList()
    }

    private fun normalize(raw: CharSequence?): String =
        raw?.toString().orEmpty().lowercase().replace('\n', ' ').replace(',', ' ').replace(Regex("""\s+"""), " ").trim()

    private fun describe(node: AccessibilityNodeInfo): String {
        val own = node.contentDescription ?: node.text
        if (!own.isNullOrBlank()) return normalize(own)
        val parts = ArrayList<CharSequence>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            (child.text ?: child.contentDescription)?.let { parts += it }
        }
        return normalize(parts.joinToString(" "))
    }

    private fun systemUiRoots(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val roots = runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
            .filter { it.packageName == SYSTEM_UI }
        if (roots.isNotEmpty()) return roots
        return listOfNotNull(service.rootInActiveWindow?.takeIf { it.packageName == SYSTEM_UI })
    }

    private fun findTile(service: AccessibilityService, labels: List<String>): AccessibilityNodeInfo? {
        for (root in systemUiRoots(service)) {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var visited = 0
            while (queue.isNotEmpty() && visited < 1500) {
                val node = queue.removeFirst()
                visited++
                if (node.isClickable && node.isVisibleToUser) {
                    val text = describe(node)
                    if (labels.any { text.startsWith(it) }) return node
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun tileState(node: AccessibilityNodeInfo): Boolean? {
        if (node.isCheckable) return node.isChecked
        val raw = buildString {
            append(node.contentDescription ?: "")
            if (Build.VERSION.SDK_INT >= 30) append(",").append(node.stateDescription ?: "")
        }
        val tokens = raw.split(',', '\n').map { it.trim().lowercase() }
        return when {
            tokens.any { it == "désactivé" || it == "off" || it == "éteint" || it == "désactivée" } -> false
            tokens.any { it == "activé" || it == "on" || it == "allumé" || it == "activée" } -> true
            else -> null
        }
    }

    private fun scrollPager(service: AccessibilityService): Boolean {
        for (root in systemUiRoots(service)) {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    /** Nom du réseau affiché sur la tuile Wi-Fi (« Wi-Fi, Activé, MonRéseau »), sinon null. */
    private fun ssidFromTile(node: AccessibilityNodeInfo): String? {
        val ignored = setOf(
            "wi-fi", "wifi", "wlan", "internet", "activé", "désactivé", "activée", "désactivée", "on", "off",
            "touche", "bouton", "connecté", "déconnecté", "non connecté", "aucun réseau", "recherche…", "recherche..."
        )
        val raw = listOfNotNull(node.contentDescription, node.text).joinToString(",")
        return raw.split(',', '\n')
            .map { it.trim().trim('‎', '‏') }
            .firstOrNull { token ->
                val n = token.lowercase()
                n.isNotEmpty() && n !in ignored && !n.startsWith("signal") && !n.startsWith("appuyez") &&
                    !n.startsWith("intitulé") && !n.contains("deux fois")
            }
    }

    /** Lit le nom du réseau sur la tuile Wi-Fi si le volet est ouvert, et le mémorise. */
    private fun captureWifiName(service: AccessibilityService) {
        val tile = findTile(service, labelsFor(service, GestureAction.WIFI)) ?: return
        ssidFromTile(tile)?.let { ConnectionNames.rememberWifiName(service, it) }
    }

    /**
     * Ouvre le volet le temps de lire le nom du Wi-Fi sur sa tuile (sans localisation,
     * Android ne le donne pas autrement), puis le referme. L’écran est figé par l’appelant.
     */
    suspend fun peekWifiName(service: AccessibilityService): String? {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        var name: String? = null
        val deadline = SystemClock.uptimeMillis() + 1500L
        while (name == null && SystemClock.uptimeMillis() < deadline) {
            delay(90L)
            name = findTile(service, labelsFor(service, GestureAction.WIFI))?.let { ssidFromTile(it) }
        }
        closeShade(service)
        name?.let { ConnectionNames.rememberWifiName(service, it) }
        return name
    }

    /**
     * Samsung ouvre la liste des appareils Bluetooth juste après l’activation depuis le volet :
     * on la referme (« Effectué ») pour rester discret. Rien d’autre n’est touché.
     */
    private suspend fun dismissBluetoothPicker(service: AccessibilityService) {
        val deadline = SystemClock.uptimeMillis() + 1800L
        while (SystemClock.uptimeMillis() < deadline) {
            delay(150L)
            val roots = runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
            val dialog = roots.firstOrNull { root ->
                root.packageName == "com.android.settings" &&
                    root.findAccessibilityNodeInfosByViewId("com.android.settings:id/bluetooth_scan_dialog").isNotEmpty()
            } ?: continue
            val done = dialog.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()
            if (done == null || !done.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            delay(200L)
            return
        }
    }

    /**
     * Diagnostic : ouvre le volet, cherche les tuiles des interrupteurs (pages suivantes
     * comprises) et renvoie leur libellé lu, ou null si introuvable. Le volet est refermé.
     */
    suspend fun probeTiles(service: AccessibilityService): Map<GestureAction, String?> {
        val tools = listOf(GestureAction.WIFI, GestureAction.BLUETOOTH, GestureAction.MOBILE_DATA, GestureAction.HOTSPOT, GestureAction.AIRPLANE)
        val found = LinkedHashMap<GestureAction, String?>().apply { tools.forEach { put(it, null) } }
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        delay(900L)
        var pages = 0
        while (true) {
            tools.filter { found[it] == null }.forEach { tool ->
                findTile(service, labelsFor(service, tool))?.let { found[tool] = describe(it).take(60) }
            }
            if (found.values.all { it != null } || pages >= 3 || !scrollPager(service)) break
            pages++
            delay(400L)
        }
        closeShade(service)
        return found
    }

    private fun closeShade(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    /**
     * Ouvre les réglages rapides, appuie sur la tuile de [tool] puis referme le volet.
     * Renvoie le nouvel état, ou null si la tuile n’a pas été trouvée.
     */
    suspend fun toggleTile(service: AccessibilityService, tool: GestureAction): Boolean? {
        val labels = labelsFor(service, tool).takeIf { it.isNotEmpty() } ?: return null
        val dialogLabels = dialogLabelsFor(service, tool)
        val before = state(service, tool)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        var tile: AccessibilityNodeInfo? = null
        val openDeadline = SystemClock.uptimeMillis() + 1500L
        while (tile == null && SystemClock.uptimeMillis() < openDeadline) {
            delay(90L)
            tile = findTile(service, labels)
        }
        var pages = 0
        while (tile == null && pages < 3 && scrollPager(service)) {
            pages++
            delay(380L)
            tile = findTile(service, labels)
        }
        if (tile == null) {
            closeShade(service)
            return null
        }
        captureWifiName(service)
        val tileBefore = tileState(tile) ?: before
        val tileWindow = tile.windowId
        tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var after = waitForChange(service, tool, before, tileBefore, 1300L) {
            findTile(service, labels)?.let { tileState(it) }
        }
        var dialogShown = false
        if (after == null) {
            // Android 13+ (Pixel, Nothing…) : la tuile ouvre une fenêtre (Bluetooth, Internet)
            // qui a son propre interrupteur : on l’actionne, puis on referme la fenêtre.
            val switch = findDialogSwitch(service, dialogLabels, tileWindow)
            if (switch != null) {
                dialogShown = true
                val switchBefore = switch.isChecked
                switch.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                after = waitForChange(service, tool, before, switchBefore, 2200L) {
                    findDialogSwitch(service, dialogLabels, tileWindow)?.isChecked
                }
            }
        }
        if (dialogShown) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(180L)
        }
        closeShade(service)
        if (tool == GestureAction.BLUETOOTH && (after ?: state(service, tool)) == true) dismissBluetoothPicker(service)
        if (after != null) return after
        delay(250L)
        // Rien n’a bougé à coup sûr : on renvoie l’état réel (tuile grisée, refus…).
        state(service, tool)?.let { return it }
        return tileBefore?.not() ?: true
    }

    /**
     * Attend que l’état change : d’après [check] (tuile ou interrupteur) ou d’après Android.
     * Renvoie le nouvel état, ou null si rien n’a changé dans le délai.
     */
    private suspend fun waitForChange(
        service: AccessibilityService,
        tool: GestureAction,
        before: Boolean?,
        reference: Boolean?,
        timeoutMs: Long,
        check: () -> Boolean?
    ): Boolean? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            delay(110L)
            val seen = check()
            if (seen != null && reference != null && seen != reference) return seen
            val real = state(service, tool)
            if (real != null && before != null && real != before) return real
        }
        return null
    }

    /** Interrupteur de la fenêtre ouverte par la tuile, repéré par le libellé de sa ligne. */
    private fun findDialogSwitch(
        service: AccessibilityService,
        labels: List<String>,
        tileWindow: Int
    ): AccessibilityNodeInfo? {
        for (root in systemUiRoots(service)) {
            if (root.windowId == tileWindow) continue
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var visited = 0
            while (queue.isNotEmpty() && visited < 800) {
                val node = queue.removeFirst()
                visited++
                if (node.isCheckable && node.isEnabled) {
                    val own = describe(node)
                    val row = node.parent?.let { collectTexts(it, 3) }.orEmpty()
                    val label = own.ifBlank { row }
                    if (labels.any { label.startsWith(it) || row.contains(it) }) return node
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun collectTexts(node: AccessibilityNodeInfo, depth: Int): String {
        val parts = ArrayList<CharSequence>()
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            n.text?.takeIf { it.isNotBlank() }?.let { parts += it }
            if (d <= 0) return
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d - 1) }
        }
        walk(node, depth)
        return normalize(parts.joinToString(" "))
    }
}
