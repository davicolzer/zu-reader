package com.davicolzer.zureader.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.davicolzer.zureader.data.backup.DriveApi
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * Acesso ao Google Drive **com a conta que já está no celular**, sem login no app: na primeira
 * vez o Android mostra a tela do Google (escolher a conta e "Permitir"); depois, o acesso vem
 * direto. Pede só a pasta de dados do app (`drive.appdata`): o app não vê os arquivos da pessoa.
 * O app não guarda senha nem token: o Google Play Services cuida disso; o token só é usado na hora.
 *
 * Devolve uma função: chame com o que fazer com o token (ex.: fazer o backup).
 */
@Composable
fun rememberDriveAccess(onFailed: () -> Unit): ((String) -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = pending
        pending = null
        val token = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken
        }.getOrNull()
        if (result.resultCode == Activity.RESULT_OK && token != null && action != null) action(token) else onFailed()
    }
    return { action ->
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveApi.SCOPE))).build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                val intent = result.pendingIntent
                when {
                    // primeira vez (ou acesso revogado): a tela do Google pede a permissão
                    result.hasResolution() && intent != null -> {
                        pending = action
                        launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    }
                    result.accessToken != null -> action(result.accessToken!!)
                    else -> onFailed()
                }
            }
            .addOnFailureListener { onFailed() }
    }
}
