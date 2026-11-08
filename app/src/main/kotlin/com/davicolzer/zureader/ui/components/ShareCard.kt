package com.davicolzer.zureader.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt

/**
 * Compartilhar um item: a imagem com um **rodapé** que traz o nome, o texto (tradução, conteúdo
 * do QR) e a descrição — para o texto não se perder quando só a imagem for repassada — e o mesmo
 * texto na mensagem.
 */
object ShareCard {
    /** Texto da mensagem: nome, texto e descrição, um por parágrafo (os vazios saem). */
    fun message(name: String?, text: String?, description: String?): String =
        listOf(name, text, description).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.joinToString("\n\n")

    /**
     * A imagem com o rodapé embaixo (largura de pelo menos [MIN_WIDTH] px, para o texto ficar
     * legível em foto pequena). Sem nada para escrever, devolve a imagem como está.
     */
    fun withFooter(image: Bitmap, name: String?, text: String?, description: String?): Bitmap {
        val parts = listOf(name, text, description).map { it?.trim().orEmpty() }
        if (parts.all { it.isEmpty() }) return image
        val width = maxOf(image.width, MIN_WIDTH)
        val photoHeight = (image.height * width / image.width.toFloat()).roundToInt()
        val pad = width * 0.045f
        val textWidth = (width - 2 * pad).roundToInt()
        fun layout(s: String, size: Float, color: Int, bold: Boolean): StaticLayout? {
            if (s.isEmpty()) return null
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            return StaticLayout.Builder.obtain(s, 0, s.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .build()
        }
        val blocks = listOfNotNull(
            layout(parts[0], width * 0.05f, GOLD, bold = true),
            layout(parts[1], width * 0.045f, WHITE, bold = true),
            layout(parts[2], width * 0.036f, MUTED, bold = false),
            layout(APP_TAG, width * 0.028f, TEAL, bold = false),
        )
        val gap = width * 0.02f
        val footer = (2 * pad + blocks.sumOf { it.height } + gap * (blocks.size - 1)).roundToInt()
        val out = Bitmap.createBitmap(width, photoHeight + footer, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(INK)
        canvas.drawBitmap(Bitmap.createScaledBitmap(image, width, photoHeight, true), 0f, 0f, null)
        var y = photoHeight + pad
        for (block in blocks) {
            canvas.save()
            canvas.translate(pad, y)
            block.draw(canvas)
            canvas.restore()
            y += block.height + gap
        }
        return out
    }

    /** Abre o "compartilhar" do Android com a imagem (se houver) e o texto. */
    fun share(context: Context, image: Bitmap?, name: String?, text: String?, description: String?) {
        val message = message(name, text, description)
        val send = Intent(Intent.ACTION_SEND)
        if (image != null) {
            val card = withFooter(image, name, text, description)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "zu-reader.jpg")
            file.outputStream().use { card.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            send.setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            send.setType("text/plain")
        }
        if (message.isNotEmpty()) send.putExtra(Intent.EXTRA_TEXT, message)
        context.startActivity(Intent.createChooser(send, null))
    }

    /** Compartilha um vídeo (o arquivo, do cache ou da coleção) com o nome e a descrição na mensagem. */
    fun shareVideo(context: Context, video: File, name: String?, description: String?) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", video)
        val send = Intent(Intent.ACTION_SEND)
            .setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        message(name, null, description).takeIf { it.isNotEmpty() }?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        context.startActivity(Intent.createChooser(send, null))
    }

    private const val MIN_WIDTH = 1080
    private const val APP_TAG = "Zu Reader"
    private const val INK = 0xFF14122A.toInt()
    private const val WHITE = 0xFFFFF8EC.toInt()
    private const val MUTED = 0xFFB7B0CC.toInt()
    private const val GOLD = 0xFFF5B700.toInt()
    private const val TEAL = 0xFF4FC3C9.toInt()
}
