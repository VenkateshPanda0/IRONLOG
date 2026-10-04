package app.ironlog.personal.ui.train

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

enum class CardTemplate(val label: String) {
    MONO("Mono"),
    ACCENT("Volt"),
}

data class ShareCardData(
    val title: String,
    val date: String,
    val duration: String,
    val volume: String,
    val sets: String,
    val bests: List<String>,
    val lines: List<Pair<String, String>>,
)

/** Draws a 4:5 workout summary image suitable for stories and feeds. */
fun renderShareCard(data: ShareCardData, template: CardTemplate): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val volt = Color.rgb(0xD9, 0xFF, 0x4F)
    val background = if (template == CardTemplate.ACCENT) volt else Color.BLACK
    val ink = if (template == CardTemplate.ACCENT) Color.BLACK else Color.WHITE
    val muted = if (template == CardTemplate.ACCENT) Color.argb(170, 0, 0, 0) else Color.argb(160, 255, 255, 255)
    val highlight = if (template == CardTemplate.ACCENT) Color.BLACK else volt
    canvas.drawColor(background)

    val heavy = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    fun paint(size: Float, color: Int, bold: Boolean = true, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) heavy else Typeface.DEFAULT
            letterSpacing = spacing
        }

    val margin = 80f
    canvas.drawText("IRONLOG", margin, 130f, paint(34f, highlight, spacing = 0.3f))
    canvas.drawText(data.date.uppercase(), margin, 185f, paint(30f, muted, bold = false, spacing = 0.1f))

    // Title, wrapped to at most two lines.
    val titlePaint = paint(92f, ink, spacing = -0.02f)
    var y = 310f
    wrap(data.title.uppercase(), titlePaint, width - 2 * margin).take(2).forEach { line ->
        canvas.drawText(line, margin, y, titlePaint)
        y += 100f
    }

    // Stats row
    y += 30f
    val columns = listOf("TIME" to data.duration, "VOLUME" to data.volume, "SETS" to data.sets)
    val columnWidth = (width - 2 * margin) / columns.size
    columns.forEachIndexed { index, (label, value) ->
        val x = margin + index * columnWidth
        canvas.drawText(label, x, y, paint(28f, muted, spacing = 0.15f))
        canvas.drawText(value, x, y + 70f, paint(58f, ink))
    }
    y += 140f
    canvas.drawRect(margin, y, width - margin, y + 4f, Paint().apply { color = highlight })
    y += 70f

    if (data.bests.isNotEmpty()) {
        canvas.drawText("PERSONAL BESTS", margin, y, paint(28f, muted, spacing = 0.15f))
        y += 55f
        data.bests.take(3).forEach { best ->
            val pill = paint(34f, if (template == CardTemplate.ACCENT) volt else Color.BLACK)
            val text = "★ $best"
            val w = pill.measureText(text) + 48f
            canvas.drawRoundRect(RectF(margin, y - 42f, margin + w.coerceAtMost(width - 2 * margin), y + 16f), 29f, 29f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = highlight })
            canvas.drawText(ellipsize(text, pill, width - 2 * margin - 48f), margin + 24f, y, pill)
            y += 78f
        }
        y += 20f
    }

    canvas.drawText("EXERCISES", margin, y, paint(28f, muted, spacing = 0.15f))
    y += 60f
    val namePaint = paint(38f, ink, bold = false)
    val setPaint = paint(38f, ink)
    val remaining = data.lines.size
    data.lines.forEachIndexed { index, (name, best) ->
        if (y > height - 140f) {
            canvas.drawText("+${remaining - index} more", margin, y, paint(32f, muted, bold = false))
            return@forEachIndexed
        }
        val bestWidth = setPaint.measureText(best)
        canvas.drawText(ellipsize(name, namePaint, width - 2 * margin - bestWidth - 30f), margin, y, namePaint)
        canvas.drawText(best, width - margin - bestWidth, y, setPaint)
        y += 62f
    }
    return bitmap
}

/** Writes the card to app cache and opens the system share sheet. */
fun shareCard(context: Context, bitmap: Bitmap, sessionId: Long) {
    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, "workout_$sessionId.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, "Share workout"))
}

private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    text.split(' ').forEach { word ->
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) <= maxWidth) line = candidate
        else {
            if (line.isNotEmpty()) lines += line
            line = word
        }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end) + "…"
}
