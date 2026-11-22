package com.meubanco.patrimonio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
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
    fun gRun(token: String) {
        scope.launch {
            gBusy = true
            Ui.show(GoogleDrive.loginSync(ctx, token))
            gBusy = false; gTick++
        }
    }
    val gLauncher = remember { mutableStateOf<androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>?>(null) }
    gLauncher.value = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val tk = GoogleDrive.tokenFromIntent(ctx, r.data)
        if (tk != null) gRun(tk) else Ui.show("Login cancelado")
    }
    fun gStart() {
        scope.launch {
            gBusy = true
            try {
                val a = GoogleDrive.authorize(ctx)
                if (a.token != null) { gBusy = false; gRun(a.token) }
                else { gBusy = false; gLauncher.value?.launch(androidx.activity.result.IntentSenderRequest.Builder(a.pending!!.intentSender).build()) }
            } catch (e: Exception) { gBusy = false; Ui.show("Login Google indisponível (${e.message ?: "erro"})") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        key(gTick) {
            val mail = GoogleDrive.email(ctx)
            if (mail == null) {
                Muted("Entre com sua conta Google. Seus dados ficam salvos sozinhos numa pasta escondida do seu Google Drive e voltam automaticamente se você reinstalar o app. Nenhum dado passa por servidor nosso.")
                PrimaryButton(if (gBusy) "Aguarde…" else "Entrar com Google", enabled = !gBusy) { gStart() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    GoogleAvatar(52.dp)
                    Column {
                        Txt(GoogleDrive.name(ctx) ?: "Conta Google", bold = true)
                        Muted(mail)
                    }
                }
                Muted("Backup automático ligado · último envio: ${GoogleDrive.lastUpload(ctx)?.take(16)?.replace('T', ' ') ?: "ainda não enviado"}")
                GhostButton("Sair da conta", Modifier.fillMaxWidth(), danger = true) { GoogleDrive.signOut(ctx); gTick++ }
            }
        }
    }
}

/** Foto da conta Google (ou a inicial do nome, se ainda não baixou). */
@Composable
fun GoogleAvatar(size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(GoogleDrive.email(ctx)) {
        try { android.graphics.BitmapFactory.decodeFile(GoogleDrive.photoFile(ctx).path)?.asImageBitmap() } catch (_: Exception) { null }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
}
