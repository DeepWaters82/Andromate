package com.example

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.example.modules.shortcuts.SettingsShortcuts
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Génère les icônes des raccourcis « icône d’appli » (une par page des paramètres) à partir
 * des icônes Material de [SettingsShortcuts] : premier plan teinté, calque monochrome (icônes
 * thématiques, packs d’icônes) et icône adaptative. Les fichiers ne sont réécrits que s’ils
 * changent ; ils sont versionnés avec le projet.
 */
class ShortcutIconGeneratorTest {

    private val res = File("src/main/res")

    @Test
    fun generateShortcutIcons() {
        if (!res.isDirectory) return
        SettingsShortcuts.pages.forEach { page ->
            val path = pathData(page.icon)
            assertTrue("Icône vide : ${page.id}", path.isNotBlank())
            val name = "ic_sc_${page.id}"
            write("drawable/${name}_fg.xml", vector(path, "@color/sc_fg"))
            write("drawable/${name}_mono.xml", vector(path, "#FFFFFFFF"))
            write(
                "drawable-anydpi-v26/$name.xml",
                """
                |<?xml version="1.0" encoding="utf-8"?>
                |<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                |    <background android:drawable="@color/sc_bg" />
                |    <foreground android:drawable="@drawable/${name}_fg" />
                |    <monochrome android:drawable="@drawable/${name}_mono" />
                |</adaptive-icon>
                |""".trimMargin()
            )
            write(
                "drawable/$name.xml",
                """
                |<?xml version="1.0" encoding="utf-8"?>
                |<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                |    <item>
                |        <shape android:shape="oval">
                |            <solid android:color="@color/sc_bg" />
                |        </shape>
                |    </item>
                |    <item android:drawable="@drawable/${name}_fg" />
                |</layer-list>
                |""".trimMargin()
            )
        }
    }

    /** Pictogramme 24 × 24 centré à 48 dp dans une icône adaptative de 108 dp. */
    private fun vector(path: String, color: String) = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<vector xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:width="108dp"
        |    android:height="108dp"
        |    android:viewportWidth="108"
        |    android:viewportHeight="108">
        |    <group android:translateX="30" android:translateY="30" android:scaleX="2" android:scaleY="2">
        |        <path android:fillColor="$color" android:pathData="$path" />
        |    </group>
        |</vector>
        |""".trimMargin()

    private fun write(relative: String, content: String) {
        val file = File(res, relative)
        file.parentFile?.mkdirs()
        if (!file.exists() || file.readText() != content) file.writeText(content)
    }

    private fun pathData(icon: ImageVector): String {
        val out = StringBuilder()
        fun visit(node: VectorNode) {
            when (node) {
                is VectorGroup -> node.forEach { visit(it) }
                is VectorPath -> node.pathData.forEach { out.append(command(it)).append(' ') }
            }
        }
        visit(icon.root)
        return out.toString().trim()
    }

    private fun f(v: Float): String {
        val s = "%.3f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    private fun command(n: PathNode): String = when (n) {
        is PathNode.Close -> "Z"
        is PathNode.MoveTo -> "M${f(n.x)},${f(n.y)}"
        is PathNode.RelativeMoveTo -> "m${f(n.dx)},${f(n.dy)}"
        is PathNode.LineTo -> "L${f(n.x)},${f(n.y)}"
        is PathNode.RelativeLineTo -> "l${f(n.dx)},${f(n.dy)}"
        is PathNode.HorizontalTo -> "H${f(n.x)}"
        is PathNode.RelativeHorizontalTo -> "h${f(n.dx)}"
        is PathNode.VerticalTo -> "V${f(n.y)}"
        is PathNode.RelativeVerticalTo -> "v${f(n.dy)}"
        is PathNode.CurveTo -> "C${f(n.x1)},${f(n.y1)} ${f(n.x2)},${f(n.y2)} ${f(n.x3)},${f(n.y3)}"
        is PathNode.RelativeCurveTo -> "c${f(n.dx1)},${f(n.dy1)} ${f(n.dx2)},${f(n.dy2)} ${f(n.dx3)},${f(n.dy3)}"
        is PathNode.ReflectiveCurveTo -> "S${f(n.x1)},${f(n.y1)} ${f(n.x2)},${f(n.y2)}"
        is PathNode.RelativeReflectiveCurveTo -> "s${f(n.dx1)},${f(n.dy1)} ${f(n.dx2)},${f(n.dy2)}"
        is PathNode.QuadTo -> "Q${f(n.x1)},${f(n.y1)} ${f(n.x2)},${f(n.y2)}"
        is PathNode.RelativeQuadTo -> "q${f(n.dx1)},${f(n.dy1)} ${f(n.dx2)},${f(n.dy2)}"
        is PathNode.ReflectiveQuadTo -> "T${f(n.x)},${f(n.y)}"
        is PathNode.RelativeReflectiveQuadTo -> "t${f(n.dx)},${f(n.dy)}"
        is PathNode.ArcTo -> "A${f(n.horizontalEllipseRadius)},${f(n.verticalEllipseRadius)} ${f(n.theta)} " +
            "${b(n.isMoreThanHalf)} ${b(n.isPositiveArc)} ${f(n.arcStartX)},${f(n.arcStartY)}"
        is PathNode.RelativeArcTo -> "a${f(n.horizontalEllipseRadius)},${f(n.verticalEllipseRadius)} ${f(n.theta)} " +
            "${b(n.isMoreThanHalf)} ${b(n.isPositiveArc)} ${f(n.arcStartDx)},${f(n.arcStartDy)}"
    }
}
