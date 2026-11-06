package com.davicolzer.zureader.zu

/** Modelo de pares de letras do inglês usado para escolher a orientação e resolver K/Q e U/V. */
internal object EnglishModel {
    /** Símbolo de fronteira de palavra. */
    const val BOUNDARY = ' '

    private fun index(c: Char): Int = if (c in 'A'..'Z') c - 'A' + 1 else 0

    /** log P([next] | [prev]) em milésimos de nat. */
    fun logProb(prev: Char, next: Char): Int =
        EnglishBigrams.logProb[index(prev) * EnglishBigrams.SIZE + index(next)]
}
