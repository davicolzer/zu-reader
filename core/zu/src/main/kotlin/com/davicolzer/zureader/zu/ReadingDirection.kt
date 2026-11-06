package com.davicolzer.zureader.zu

/** Ordem em que os glifos são lidos. */
enum class ReadingDirection {
    /** Padrão do Zu: colunas de cima para baixo, da direita para a esquerda. */
    VERTICAL_RIGHT_TO_LEFT,

    /** Linhas da esquerda para a direita, de cima para baixo. */
    HORIZONTAL_LEFT_TO_RIGHT,
}
