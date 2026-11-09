package com.davicolzer.zureader.ui.components

/**
 * Edição com cursor, como num texto: [cursor] é onde o próximo item entra (0 = antes do primeiro,
 * [items].size = no fim) e [selected] é um item escolhido para trocar ou apagar.
 * Usado na sequência de comandos e no texto de "Escrever à mão".
 */
data class CursorEdit<T>(val items: List<T>, val cursor: Int = items.size, val selected: Int? = null) {
    /** Digita [item]: troca o selecionado ou entra no cursor; o cursor vai para depois dele. */
    fun type(item: T): CursorEdit<T> = typeAll(listOf(item))

    /** Digita vários itens de uma vez (ex.: um espaço), no lugar do selecionado ou no cursor. */
    fun typeAll(new: List<T>): CursorEdit<T> {
        val at = selected ?: cursor
        val removed = if (selected != null) 1 else 0
        val list = items.subList(0, at) + new + items.subList(at + removed, items.size)
        return CursorEdit(list, at + new.size)
    }

    /** Apaga o selecionado ou, sem seleção, o item antes do cursor. */
    fun delete(): CursorEdit<T> = when {
        selected != null -> CursorEdit(items.filterIndexed { i, _ -> i != selected }, selected)
        cursor > 0 -> CursorEdit(items.filterIndexed { i, _ -> i != cursor - 1 }, cursor - 1)
        else -> this
    }

    /** Cursor para a esquerda (com seleção: para antes do selecionado). */
    fun left(): CursorEdit<T> = CursorEdit(items, if (selected != null) selected else (cursor - 1).coerceAtLeast(0))

    /** Cursor para a direita (com seleção: para depois do selecionado). */
    fun right(): CursorEdit<T> = CursorEdit(items, if (selected != null) selected + 1 else (cursor + 1).coerceAtMost(items.size))

    /** Toca no item [index]: seleciona (tocar de novo tira a seleção, com o cursor depois dele). */
    fun select(index: Int): CursorEdit<T> =
        if (selected == index) CursorEdit(items, index + 1) else CursorEdit(items, index + 1, index)

    /** Põe o cursor em [position] (sem seleção). */
    fun moveTo(position: Int): CursorEdit<T> = CursorEdit(items, position.coerceIn(0, items.size))

    fun clear(): CursorEdit<T> = CursorEdit(emptyList())
}
