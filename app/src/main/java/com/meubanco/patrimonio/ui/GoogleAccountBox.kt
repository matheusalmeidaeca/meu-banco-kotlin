package com.meubanco.patrimonio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.GoogleDrive
import kotlinx.coroutines.launch

/** Login Google + backup no Drive. Usado no cadastro (boas-vindas) e em Configurações. */
@Composable
fun GoogleAccountBox() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var gTick by remember { mutableIntStateOf(0) }
    var gBusy by remember { mutableStateOf(false) }
    var gRestore by remember { mutableStateOf(false) }
    var gAction by remember { mutableStateOf("login") } // o que fazer depois do consentimento
    fun gRun(token: String, action: String) {
        scope.launch {
            gBusy = true
            when (action) {
                "login" -> {
                    val mail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { GoogleDrive.whoAmI(token) }
                    if (mail != null) { GoogleDrive.saveEmail(ctx, mail); Ui.show("Conectado: $mail") } else Ui.show("Não consegui confirmar a conta")
                }
                "backup" -> Ui.show(GoogleDrive.backupNow(ctx, token))
                "restore" -> Ui.show(GoogleDrive.restoreNow(token))
            }
            gBusy = false; gTick++
        }
    }
    val gLauncher = remember { mutableStateOf<androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>?>(null) }
    gLauncher.value = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val tk = GoogleDrive.tokenFromIntent(ctx, r.data)
        if (tk != null) gRun(tk, gAction) else Ui.show("Login cancelado")
    }
    fun gStart(action: String) {
        scope.launch {
            gAction = action; gBusy = true
            try {
                val a = GoogleDrive.authorize(ctx)
                if (a.token != null) { gBusy = false; gRun(a.token, action) }
                else { gBusy = false; gLauncher.value?.launch(androidx.activity.result.IntentSenderRequest.Builder(a.pending!!.intentSender).build()) }
            } catch (e: Exception) { gBusy = false; Ui.show("Login Google indisponível (${e.message ?: "erro"})") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            key(gTick) {
                val mail = GoogleDrive.email(ctx)
                if (mail == null) {
                    Muted("Entre com sua conta Google para guardar o backup numa pasta escondida do seu próprio Google Drive. Só este app enxerga essa pasta. Nenhum dado passa por servidor nosso.")
                    PrimaryButton(if (gBusy) "Aguarde…" else "Entrar com Google", enabled = !gBusy) { gStart("login") }
                } else {
                    Muted("Conectado: $mail · último envio: ${GoogleDrive.lastUpload(ctx)?.take(16)?.replace('T', ' ') ?: "—"}")
                    Muted("O backup é enviado sozinho todo dia (se a cópia automática estiver ligada).")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Salvar agora", Modifier.weight(1f), enabled = !gBusy) { gStart("backup") }
                        GhostButton("Restaurar", Modifier.weight(1f), enabled = !gBusy) { gRestore = true }
                    }
                    GhostButton("Sair da conta", Modifier.fillMaxWidth(), danger = true) { GoogleDrive.signOut(ctx); gTick++ }
                }
                if (gRestore) { Muted("Restaurar substitui os dados atuais pelos do backup no Drive."); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Cancelar", Modifier.weight(1f)) { gRestore = false }
                    PrimaryButton("Restaurar", Modifier.weight(1f)) { gRestore = false; gStart("restore") }
                } }
            }
    }
}
