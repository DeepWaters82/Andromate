package com.example.modules.automate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.drawable.IconCompat
import com.example.core.preferences.ModuYouPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Modes façon One UI : un seul mode actif à la fois. Activer un mode arrête l’autre
 * (ses réglages sont rétablis) ; arrêter un mode rétablit l’état d’avant.
 * « Actif » = un instantané est enregistré pour ce mode.
 */
object ModeController {
    private val lock = Mutex()

    suspend fun activate(context: Context, mode: AutomationRoutine, detail: String) {
        val appContext = context.applicationContext
        val prefs = ModuYouPreferences.getInstance(appContext)
        lock.withLock {
            val config = prefs.automateConfigFlow.first()
            if (config.snapshots.containsKey(mode.id)) return
            config.routines
                .filter { it.isMode && it.id != mode.id && config.snapshots.containsKey(it.id) }
                .forEach { other ->
                    runCatching { AutomateActionExecutor.restoreRoutine(appContext, other, "Mode ${mode.name}") }
                    clearSnapshot(prefs, other.id)
                }
            AutomateActionExecutor.executeRoutine(appContext, mode.copy(restoreOnEnd = true), detail)
        }
        AutomateController.syncMonitoringService(appContext, prefs.automateConfigFlow.first())
    }

    suspend fun deactivate(context: Context, mode: AutomationRoutine, reason: String) {
        val appContext = context.applicationContext
        val prefs = ModuYouPreferences.getInstance(appContext)
        lock.withLock {
            runCatching { AutomateActionExecutor.restoreRoutine(appContext, mode, reason) }
            clearSnapshot(prefs, mode.id)
        }
        AutomateController.syncMonitoringService(appContext, prefs.automateConfigFlow.first())
    }

    suspend fun deactivateActive(context: Context, reason: String) {
        val config = ModuYouPreferences.getInstance(context.applicationContext).automateConfigFlow.first()
        config.activeMode?.let { deactivate(context, it, reason) }
    }

    /** Appui sur la carte d’un mode : l’active, ou l’arrête s’il est déjà actif. */
    suspend fun toggle(context: Context, modeId: String) {
        val config = ModuYouPreferences.getInstance(context.applicationContext).automateConfigFlow.first()
        val mode = config.routines.firstOrNull { it.id == modeId && it.isMode } ?: return
        if (config.snapshots.containsKey(mode.id)) {
            deactivate(context, mode, "Arrêté manuellement")
        } else {
            activate(context, mode, "Activé manuellement")
        }
    }

    /** Filet de sécurité : un mode arrêté ne doit jamais rester marqué actif. */
    private suspend fun clearSnapshot(prefs: ModuYouPreferences, id: String) {
        if (prefs.automateConfigFlow.first().snapshots.containsKey(id)) {
            prefs.updateAutomateConfig { it.copy(snapshots = it.snapshots - id) }
        }
    }
}

/** Dessine l’icône Material d’un mode en bitmap blanc pour la barre d’état. */
object ModeIconRenderer {
    private val cache = HashMap<String, Bitmap>()

    fun iconFor(context: Context, mode: AutomationRoutine): IconCompat? = runCatching {
        val key = mode.iconKey.ifBlank { "_default" }
        val bitmap = synchronized(cache) {
            cache.getOrPut(key) { render(context, RoutineStyle.iconFor(mode)) }
        }
        IconCompat.createWithBitmap(bitmap)
    }.getOrNull()

    private fun render(context: Context, vector: ImageVector): Bitmap {
        val px = (24 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(24)
        return renderTinted(vector, px, android.graphics.Color.WHITE)
    }

    /** Icône Material en bitmap d’une couleur (pour les vues Android classiques). */
    fun renderTinted(vector: ImageVector, sizePx: Int, color: Int): Bitmap {
        val px = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(px / vector.viewportWidth, px / vector.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        drawGroup(canvas, vector.root, paint)
        return bitmap
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, paint)
                is VectorPath -> {
                    val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                        android.graphics.Path.FillType.EVEN_ODD
                    } else {
                        android.graphics.Path.FillType.WINDING
                    }
                    paint.alpha = (node.fillAlpha * android.graphics.Color.alpha(paint.color)).roundToInt().coerceIn(0, 255)
                    canvas.drawPath(path, paint)
                }
            }
        }
        canvas.restore()
    }
}
