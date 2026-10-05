package app.ironlog.personal.ui.train

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface

/** Draws the 4:5 workout summary image with Skia; the same layout as the Android card. */
fun renderShareCardSkia(data: ShareCardData, template: CardTemplate): ImageBitmap {
    val width = 1080
    val height = 1350
    val surface = Surface.makeRasterN32Premul(width, height)
    val canvas = surface.canvas
    val volt = Color.makeRGB(0xD9, 0xFF, 0x4F)
    val accent = template == CardTemplate.ACCENT
    val background = if (accent) volt else Color.BLACK
    val ink = if (accent) Color.BLACK else Color.WHITE
    val muted = if (accent) Color.makeARGB(170, 0, 0, 0) else Color.makeARGB(160, 255, 255, 255)
    val highlight = if (accent) Color.BLACK else volt
    canvas.clear(background)

    val regular: Typeface = FontMgr.default.matchFamilyStyle(null, FontStyle.NORMAL) ?: FontMgr.default.legacyMakeTypeface("", FontStyle.NORMAL)!!
    val heavy: Typeface = FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD) ?: regular
    fun font(size: Float, bold: Boolean = true) = Font(if (bold) heavy else regular, size)
    fun paint(color: Int) = Paint().apply { this.color = color; isAntiAlias = true }
    fun text(value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = true) =
        canvas.drawString(value, x, y, font(size, bold), paint(color))
    fun width(value: String, size: Float, bold: Boolean = true) = font(size, bold).measureTextWidth(value)
    fun ellipsize(value: String, size: Float, bold: Boolean, max: Float): String {
        if (width(value, size, bold) <= max) return value
        var end = value.length
        while (end > 0 && width(value.substring(0, end) + "…", size, bold) > max) end--
        return value.substring(0, end) + "…"
    }

    val margin = 80f
    text("IRONLOG", margin, 130f, 34f, highlight)
    text(data.date.uppercase(), margin, 185f, 30f, muted, bold = false)

    // Title, wrapped to at most two lines.
    var y = 310f
    val lines = mutableListOf<String>()
    var line = ""
    data.title.uppercase().split(' ').forEach { word ->
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (width(candidate, 92f) <= width - 2 * margin) line = candidate
        else {
            if (line.isNotEmpty()) lines += line
            line = word
        }
    }
    if (line.isNotEmpty()) lines += line
    lines.take(2).forEach {
        text(it, margin, y, 92f, ink)
        y += 100f
    }

    // Stats row
    y += 30f
    val columns = listOf("TIME" to data.duration, "VOLUME" to data.volume, "SETS" to data.sets)
    val columnWidth = (width - 2 * margin) / columns.size
    columns.forEachIndexed { index, (label, value) ->
        val x = margin + index * columnWidth
        text(label, x, y, 28f, muted)
        text(value, x, y + 70f, 58f, ink)
    }
    y += 140f
    canvas.drawRect(Rect.makeLTRB(margin, y, width - margin, y + 4f), paint(highlight))
    y += 70f

    if (data.bests.isNotEmpty()) {
        text("PERSONAL BESTS", margin, y, 28f, muted)
        y += 55f
        data.bests.take(3).forEach { best ->
            val label = "★ $best"
            val w = (width(label, 34f) + 48f).coerceAtMost(width - 2 * margin)
            canvas.drawRRect(RRect.makeLTRB(margin, y - 42f, margin + w, y + 16f, 29f), paint(highlight))
            text(ellipsize(label, 34f, true, width - 2 * margin - 48f), margin + 24f, y, 34f, if (accent) volt else Color.BLACK)
            y += 78f
        }
        y += 20f
    }

    text("EXERCISES", margin, y, 28f, muted)
    y += 60f
    for ((index, entry) in data.lines.withIndex()) {
        val (name, best) = entry
        if (y > height - 140f) {
            text("+${data.lines.size - index} more", margin, y, 32f, muted, bold = false)
            break
        }
        val bestWidth = width(best, 38f)
        text(ellipsize(name, 38f, false, width - 2 * margin - bestWidth - 30f), margin, y, 38f, ink, bold = false)
        text(best, width - margin - bestWidth, y, 38f, ink)
        y += 62f
    }
    return surface.makeImageSnapshot().toComposeImageBitmap()
}
