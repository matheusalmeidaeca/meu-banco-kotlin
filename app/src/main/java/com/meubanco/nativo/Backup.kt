package com.meubanco.nativo

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Abre o app de e-mail com o backup anexado, já endereçado ao e-mail guardado nas configurações.
 * O envio em si é feito pelo usuário (toque em "Enviar" no Gmail).
 */
fun shareBackupByEmail(ctx: Context, store: Store): Boolean {
    return try {
        val email = store.state?.sub("settings")?.str("backupEmail") ?: ""
        val dir = File(ctx.cacheDir, "backups")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "backup-meu-banco-${todayISO()}.json")
        f.writeText(store.exportText())
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND)
        i.type = "application/json"
        if (email.isNotBlank()) i.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        i.putExtra(Intent.EXTRA_SUBJECT, "Backup Meu Banco — ${ddmmyyyy(todayISO())}")
        i.putExtra(
            Intent.EXTRA_TEXT,
            "Backup do app Meu Banco. Para restaurar: abra este anexo no celular e escolha \"Meu Banco\".",
        )
        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(i, "Enviar backup por e-mail")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
        store.markBackup()
        true
    } catch (e: Exception) {
        false
    }
}
