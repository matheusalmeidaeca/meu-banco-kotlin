package com.meubanco.patrimonio.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.meubanco.patrimonio.domain.*
import java.io.File
import java.io.OutputStream

object Backup {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
    fun cloudFolder(ctx: Context): Uri? = prefs(ctx).getString("tree", null)?.let { Uri.parse(it) }
    fun cloudFolderName(ctx: Context): String? = cloudFolder(ctx)?.let { androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, it)?.name }
    fun cloudLast(ctx: Context): String? = prefs(ctx).getString("last", null)

    fun setCloudFolder(ctx: Context, uri: Uri?) {
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            prefs(ctx).edit().putString("tree", uri.toString()).apply()
        } else prefs(ctx).edit().remove("tree").apply()
    }

    /** Grava (ou sobrescreve) meu-banco-backup.json na pasta escolhida — pode ser uma pasta do Google Drive, Dropbox, OneDrive etc. */
    fun writeCloud(ctx: Context): Boolean = try {
        val tree = cloudFolder(ctx) ?: return false
        val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, tree) ?: return false
        val name = "meu-banco-backup.json"
        val f = dir.findFile(name) ?: dir.createFile("application/json", name) ?: return false
        ctx.contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(encodeState(Store.state, true).toByteArray()) }
        prefs(ctx).edit().putString("last", java.time.Instant.ofEpochMilli(AppClock.nowMs()).toString()).apply()
        true
    } catch (e: Exception) { false }

    fun suggestedName(): String = "meu-banco-backup-" + AppClock.todayISO() + ".json"

    fun export(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(encodeState(Store.state, true).toByteArray()) }
        Store.mutate { it.lastBackupAt = java.time.Instant.ofEpochMilli(AppClock.nowMs()).toString() }
        true
    } catch (e: Exception) { false }

    /** Importa um backup (também aceita o .json exportado pelo app em HTML). */
    fun import(ctx: Context, uri: Uri): Boolean = try {
        val txt = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
        val st = decodeState(txt)
        Store.replace(st)
        true
    } catch (e: Exception) { false }

    /** Cópia automática diária dentro da pasta privada do app (mantém as 7 últimas). */
    fun autoLocalCopy(ctx: Context) {
        try {
            val dir = File(ctx.filesDir, "backups").apply { mkdirs() }
            File(dir, "auto-" + AppClock.todayISO() + ".json").writeText(encodeState(Store.state))
            dir.listFiles()?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    /** Extrato em PDF (substitui window.print do HTML). */
    fun exportStatementPdf(ctx: Context, uri: Uri, s: AppState, entries: List<HistoryEntry>): Boolean = try {
        val doc = PdfDocument()
        val pw = 595; val ph = 842; val m = 36f
        val title = Paint().apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint().apply { textSize = 9.5f }
        val bold = Paint().apply { textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
        var c = page.canvas
        var y = m + 16
        c.drawText("Extrato — Admoney", m, y, title); y += 18
        c.drawText((s.settings.displayName.ifBlank { "Titular" }) + " · gerado em " + formatSimDate(s.simDateISO), m, y, body); y += 22
        for (e in entries) {
            if (y > ph - m) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create()); c = page.canvas; y = m + 10
            }
            val d = e.ts.take(10)
            c.drawText(d, m, y, body)
            val lbl = if (e.label.length > 62) e.label.take(60) + "…" else e.label
            c.drawText(lbl, m + 62, y, body)
            val v = formatBRL(e.amount)
            c.drawText(v, pw - m - bold.measureText(v), y, bold)
            y += 14
        }
        doc.finishPage(page)
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { doc.writeTo(it) }
        doc.close()
        true
    } catch (e: Exception) { false }
}
