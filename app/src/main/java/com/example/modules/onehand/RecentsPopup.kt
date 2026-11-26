package com.example.modules.onehand

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Fenêtre réduite par l’écran Récents : les téléphones sans fenêtres libres pour les applis
 * (Nothing, Samsung…) proposent quand même une fenêtre contextuelle depuis le menu de l’appli
 * dans Récents. On ouvre l’appli, puis Récents, on touche son icône et on choisit l’option.
 * Seuls les éléments de l’écran Récents sont lus (nom de l’appli, menu).
 */
object RecentsPopup {

    private val FALLBACK_WORDS = listOf(
        "contextuelle", "pop-up", "popup", "fenêtre flottante", "floating", "freeform", "forme libre"
    )

    /** Mots de l’option « fenêtre flottante » : ressources du lanceur, puis noms connus. */
    private fun optionWords(service: AccessibilityService): List<String> =
        (SystemLabels.recentsPopupOptions(service) + FALLBACK_WORDS).distinct()

    private fun normalize(text: CharSequence?): String =
        text?.toString().orEmpty().lowercase().replace(' ', ' ').trim()

    private fun roots(service: AccessibilityService): List<AccessibilityNodeInfo> =
        runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())

    private fun walk(root: AccessibilityNodeInfo, limit: Int = 1500, visit: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val node = queue.removeFirst()
            seen++
            if (visit(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /**
     * Icône de l’appli au-dessus de sa carte dans Récents (ou la carte elle-même à défaut).
     * Recherche par le système (texte / identifiant) : l’arbre du lanceur contient aussi tout
     * l’écran d’accueil et le tiroir d’applis, trop long à parcourir.
     */
    private fun findTaskIcon(service: AccessibilityService, label: String, allowCard: Boolean): AccessibilityNodeInfo? {
        val wanted = normalize(label)
        for (root in roots(service)) {
            val task = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrDefault(emptyList())
                .filter { node ->
                    val desc = normalize(node.contentDescription)
                    desc == wanted || desc.startsWith("$wanted ") || desc.startsWith("$wanted,")
                }
                .maxByOrNull { node -> Rect().also { node.getBoundsInScreen(it) }.let { it.width() * it.height() } }
                ?: continue
            val taskBounds = Rect().also { task.getBoundsInScreen(it) }
            fun nearTop(b: Rect) = b.centerX() in taskBounds.left..taskBounds.right &&
                b.top >= taskBounds.top - b.height() && b.bottom <= taskBounds.top + b.height() * 3
            // Icône : identifiant « …:id/icon » (Launcher3 : Nothing, Pixel…).
            val pkg = root.packageName?.toString().orEmpty()
            val icon = runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/icon") }.getOrDefault(emptyList())
                .firstOrNull { node -> node.isClickable && nearTop(Rect().also { node.getBoundsInScreen(it) }) }
            // Sans identifiant : petit élément touchable en haut de la carte.
                ?: walk(task, limit = 400) { node ->
                    val b = Rect().also { node.getBoundsInScreen(it) }
                    node.isClickable && node != task && b.width() in 1..(taskBounds.width() / 3) &&
                        b.height() in 1..(taskBounds.height() / 5) && nearTop(b)
                }
            return icon ?: task.takeIf { allowCard }
        }
        return null
    }

    private fun findPopupOption(service: AccessibilityService): AccessibilityNodeInfo? {
        val words = optionWords(service)
        for (root in roots(service)) {
            for (word in words) {
                val node = runCatching { root.findAccessibilityNodeInfosByText(word) }.getOrDefault(emptyList())
                    .firstOrNull { n ->
                        val text = normalize(n.text).ifEmpty { normalize(n.contentDescription) }
                        text.isNotEmpty() && text.length < 60
                    }
                if (node != null) return node
            }
        }
        return null
    }

    private fun clickWithAncestors(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
            depth++
        }
        return false
    }

    /** Vrai toucher à l’écran au centre de [node] (certains lanceurs ignorent le clic d’accessibilité). */
    private suspend fun tap(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val b = Rect().also { node.getBoundsInScreen(it) }
        if (b.isEmpty) return false
        val path = android.graphics.Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val sent = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                done.complete(true)
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                done.complete(false)
            }
        }, null)
        if (!sent) return false
        return kotlinx.coroutines.withTimeoutOrNull(1000L) { done.await() } ?: false
    }

    private suspend fun <T> waitFor(timeoutMs: Long, step: Long = 110L, find: () -> T?): T? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            delay(step)
            find()?.let { return it }
        }
        return null
    }

    /** @return true si l’appli s’est ouverte en fenêtre contextuelle. */
    suspend fun open(service: AccessibilityService, packageName: String): Boolean {
        val label = AppLauncher.label(service, packageName)
        if (AppLauncher.launch(service, packageName, popup = false) != true) return false
        delay(750L)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        // On attend la fin de l’animation de Récents : l’icône n’est en place qu’ensuite.
        val icon = waitFor(2500L) { findTaskIcon(service, label, allowCard = false) }
            ?: findTaskIcon(service, label, allowCard = true)
        android.util.Log.w(
            "Andromate",
            "Récents : icône ${icon?.viewIdResourceName} ${icon?.contentDescription} · fenêtres " +
                roots(service).joinToString { it.packageName.toString() }
        )
        if (icon == null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            return false
        }
        if (!tap(service, icon)) clickWithAncestors(icon)
        var option = waitFor(1500L) { findPopupOption(service) }
        if (option == null) {
            // Certains lanceurs ouvrent le menu par un appui long sur la carte.
            icon.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            option = waitFor(1200L) { findPopupOption(service) }
        }
        if (option == null || !(tap(service, option) || clickWithAncestors(option))) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(200L)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            return false
        }
        delay(450L)
        return true
    }

    /** Résultat du diagnostic de Récents. */
    data class Probe(val iconFound: Boolean, val option: String?, val menu: List<String>)

    /**
     * Diagnostic sur l’appli au premier plan (Andromate) : ouvre Récents, touche son icône,
     * relève le menu et l’option de fenêtre flottante, puis referme sans rien choisir.
     */
    suspend fun probe(service: AccessibilityService, label: String): Probe {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        val icon = waitFor(2500L) { findTaskIcon(service, label, allowCard = false) }
        if (icon == null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            return Probe(iconFound = false, option = null, menu = emptyList())
        }
        if (!tap(service, icon)) clickWithAncestors(icon)
        delay(900L)
        val option = findPopupOption(service)?.let { (it.text ?: it.contentDescription)?.toString() }
        val launcher = SystemLabels.launcherPackage(service)
        val menu = roots(service).filter { it.packageName == launcher }.flatMap { root ->
            val texts = ArrayList<String>()
            walk(root, limit = 4000) { node ->
                val t = node.text?.toString()?.trim().orEmpty()
                if (node.isVisibleToUser && t.isNotEmpty() && t.length < 40 && !t.equals(label, ignoreCase = true)) texts += t
                false
            }
            texts
        }.distinct().take(8)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(250L)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return Probe(iconFound = true, option = option, menu = menu)
    }
}
