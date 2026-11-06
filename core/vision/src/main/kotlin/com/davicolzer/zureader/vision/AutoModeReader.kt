package com.davicolzer.zureader.vision

import com.davicolzer.zureader.zu.ReadingMode
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Algo que lê o texto Zu de uma imagem (um modo só, ou vários ao mesmo tempo). */
interface ImageTextReader {
    /**
     * @param deskew corrigir inclinação.
     * @param thorough testar todas as máscaras. Desligar (com [deskew] desligado) deixa a leitura
     * bem mais rápida, para o visor ao vivo da câmera.
     */
    fun read(image: GrayImage, deskew: Boolean = true, thorough: Boolean = true): ImageReading

    /** Imagem colorida: se a leitura em cinza sair fraca, testa também versões de cor. */
    fun read(image: RgbImage, deskew: Boolean = true, thorough: Boolean = true): ImageReading
}

/** O que o usuário escolhe ler: automático (escrita, números ou os dois misturados), só escrita ou só números. */
enum class ModeChoice(val mode: ReadingMode?) {
    AUTO(null),
    TEXT(ReadingMode.TEXT),
    NUMBERS(ReadingMode.NUMBERS),
}

/**
 * Lê a mesma imagem em vários modos ao mesmo tempo (uma thread por modo, então o tempo é o do
 * modo mais lento, não a soma) e fica com a leitura de [ImageReading.quality] maior. Em empate,
 * vale a ordem de [readers] (a escrita vem primeiro).
 */
class AutoModeReader(private val readers: List<ImageTextReader>) : ImageTextReader {
    init {
        require(readers.isNotEmpty())
    }

    override fun read(image: GrayImage, deskew: Boolean, thorough: Boolean): ImageReading =
        best { it.read(image, deskew, thorough) }

    override fun read(image: RgbImage, deskew: Boolean, thorough: Boolean): ImageReading =
        best { it.read(image, deskew, thorough) }

    private fun best(read: (ImageTextReader) -> ImageReading): ImageReading {
        val readings = if (readers.size == 1) {
            listOf(read(readers[0]))
        } else {
            pool.invokeAll(readers.map { r -> Callable { read(r) } }).map { it.get() }
        }
        // maxBy fica com o primeiro em caso de empate (a escrita vem primeiro)
        val (mixed, single) = readings.partition { it.mode == ReadingMode.MIXED }
        val best = single.maxByOrNull { it.quality } ?: return readings.maxBy { it.quality }
        // o misto só ganha quando tem letras e números de verdade e uma nota claramente maior:
        // numa imagem só de texto ele acharia um "número" de lixo aqui e ali
        val text = single.firstOrNull { it.mode == ReadingMode.TEXT }
        val real = mixed.filter { m ->
            val symbols = m.reading.glyphs.map { it.letter }
            val letters = symbols.filter { it in 'A'..'Z' }
            val numbers = m.reading.glyphs.filter { ReadingMode.isNumber(it.letter) }
            // conferência cruzada: na mesma orientação, as letras da leitura mista têm de aparecer,
            // na mesma ordem, na leitura só de letras (lá os números viram letras soltas). Se o misto
            // trocou letras, o "número" é lixo (ex.: a borda quadrada de uma placa de neon parece o 0)
            val lettersAgree = text == null || text.reading.rotation != m.reading.rotation ||
                isSubsequence(letters, text.reading.glyphs.map { it.letter })
            // numa frase mista, letras e números ficam no mesmo bloco (mesmo tamanho de glifo)
            val sameBlock = m.blocks.any { b -> b.glyphs.any { ReadingMode.isNumber(it.letter) } && b.glyphs.any { it.letter in 'A'..'Z' } }
            // e as letras também confiantes: senão a nota maior vem de lixo lido como letra
            val letterConfidence = m.reading.glyphs.filter { it.letter in 'A'..'Z' }.map { it.confidence }.average()
            letters.isNotEmpty() && numbers.isNotEmpty() && sameBlock && lettersAgree && letterConfidence >= MIXED_MIN_LETTER_CONFIDENCE &&
                numbers.all { it.confidence >= MIXED_MIN_CONFIDENCE } && m.quality > best.quality + MIXED_MARGIN
        }
        return real.maxByOrNull { it.quality } ?: best
    }

    private fun isSubsequence(part: List<Char>, whole: List<Char>): Boolean {
        var i = 0
        for (c in whole) if (i < part.size && part[i] == c) i++
        return i == part.size
    }

    companion object {
        /** Confiança mínima de cada número para a leitura mista valer. */
        const val MIXED_MIN_CONFIDENCE = 0.95f

        /** Confiança média mínima das letras para a leitura mista valer. */
        const val MIXED_MIN_LETTER_CONFIDENCE = 0.8

        /** Quanto a nota da leitura mista precisa superar a de um modo só (≈ 1 glifo confiante). */
        const val MIXED_MARGIN = 0.5f

        /** Threads de fundo (daemon) compartilhadas, uma por modo. */
        private val pool: ExecutorService = Executors.newFixedThreadPool(ReadingMode.entries.size) { task ->
            Thread(task, "zu-auto-mode").apply { isDaemon = true }
        }
    }
}
