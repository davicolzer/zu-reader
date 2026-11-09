package com.davicolzer.zureader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calcula [compute] fora da thread principal (ex.: desenhar sobre uma foto inteira) e devolve o
 * resultado quando fica pronto; até lá, null. Refaz quando alguma das [keys] muda.
 */
@Composable
fun <T : Any> rememberInBackground(vararg keys: Any?, compute: () -> T): T? =
    produceState<T?>(null, *keys) { value = withContext(Dispatchers.Default) { compute() } }.value
