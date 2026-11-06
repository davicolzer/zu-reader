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

/** O que o usuário escolhe ler: automático (escrita ou números), só escrita ou só números. */
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
        // maxBy fica com o primeiro em caso de empate
        return readings.maxBy { it.quality }
    }

    private companion object {
        /** Threads de fundo (daemon) compartilhadas, uma por modo. */
        val pool: ExecutorService = Executors.newFixedThreadPool(ReadingMode.entries.size) { task ->
            Thread(task, "zu-auto-mode").apply { isDaemon = true }
        }
    }
}
