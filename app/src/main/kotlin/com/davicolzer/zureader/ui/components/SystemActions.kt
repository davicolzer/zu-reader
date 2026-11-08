package com.davicolzer.zureader.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Copia o texto para a área de transferência. */
fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Zu Reader", text))
}

/** Abre o link no navegador (sem navegador, não faz nada). */
fun openLink(context: Context, link: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
}
