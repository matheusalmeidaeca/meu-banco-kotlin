package com.meubanco.patrimonio.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

private class SDraft(kind: String, amount: Double?, who: String, cat: String, val pix: PixInfo? = null) {
    var kind by mutableStateOf(kind); var amount by mutableStateOf(amount); var who by mutableStateOf(who); var cat by mutableStateOf(cat)
}

/** Lançamento inteligente: fale, escreva, cole um Pix copia e cola ou leia um QR Pix. Sempre com confirmação. */
@Composable
fun SmartScreen() {
    val ctx = LocalContext.current; val s = state()
    var text by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<SDraft?>(null) }
    var scan by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<String?>(null) }

    fun analyze(raw: String) {
        val t = raw.trim()
        val pix = parsePixBr(t)
        if (pix != null) { draft = SDraft("pix", pix.amount, pix.name, "", pix); return }
        val e = parseSmartSentence(t, s.freeSpendCategories)
        if (e == null) { draft = null; Ui.show("Não entendi. Diga o valor e o que foi, por exemplo: gastei 25,90 no mercado.") }
        else draft = SDraft(e.kind, e.amount, e.who, e.category)
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) { text = said; analyze(said) }
        }
    }

    LaunchedEffect(Ui.sharedText) { Ui.sharedText?.let { Ui.sharedText = null; text = it; analyze(it) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted("Fale, escreva ou leia um QR Pix. O app entende, mostra o que vai registrar e você confirma. Ele não move dinheiro real.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("🎤 Falar", Modifier.weight(1f)) {
                try {
                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR").putExtra(RecognizerIntent.EXTRA_PROMPT, "Ex.: gastei 25 reais no mercado"))
                } catch (e: Exception) { Ui.show("Este aparelho não tem reconhecimento de voz. Escreva a frase.") }
            }
            GhostButton("📷 Ler QR Pix", Modifier.weight(1f)) { scan = true }
        }
        Field(text, { text = it }, "Frase ou Pix copia e cola", singleLine = false)
        PrimaryButton("Entender", enabled = text.isNotBlank()) { analyze(text) }
        Muted("Exemplos: \"gastei 25,90 no mercado\", \"recebi 1.200 de João\", \"mandei 50 pix para Ana\".")

        draft?.let { d ->
            Card {
                if (d.pix != null) {
                    Txt(if (d.pix.valid) "✓ Código Pix válido (verificação conferida)" else "⚠ O código Pix não passou na verificação. Confira com quem gerou.", color = if (d.pix.valid) P.green else P.gold, bold = true)
                    Muted("Destinatário: ${d.pix.name}" + (if (d.pix.city.isNotBlank()) " · ${d.pix.city}" else ""))
                } else ChipRow(listOf("gasto" to "Gasto", "entrada" to "Entrada", "pix" to "Pix enviado"), d.kind) { d.kind = it }
                key(d.pix, d.amount == null) { NumField(d.amount, { d.amount = it }, "Valor", suffix = "R$") }
                if (d.pix == null) Field(d.who, { d.who = it }, if (d.kind == "entrada") "De quem" else if (d.kind == "pix") "Para quem" else "Onde (opcional)")
                if (d.kind == "gasto") ChipRow(s.freeSpendCategories.map { it to it }, d.cat) { d.cat = it }
                if (d.kind == "pix" && d.who.isNotBlank()) {
                    if (pixIsNewRecipient(s, d.who)) Txt("⚠️ Primeira vez enviando para ${d.who.trim()}. Confira o nome.", color = P.gold)
                    pixRiskFlags(s, d.who, d.amount ?: 0.0).forEach { Txt("⚠️ $it", color = P.gold) }
                }
                Muted("Saldo: ${formatBRL(s.cash)}")
                PrimaryButton(when (d.kind) { "entrada" -> "Registrar entrada"; "pix" -> "Registrar Pix enviado"; else -> "Registrar gasto" }, enabled = (d.amount ?: 0.0) > 0) {
                    val v = d.amount ?: 0.0
                    when (d.kind) {
                        "entrada" -> if (handle(Store.mutate { receivePix(it, d.who.ifBlank { "Entrada" }, v) })) { draft = null; text = "" }
                        "pix" -> {
                            val chk = checkSendPix(Store.state, d.who, v)
                            if (chk.confirm != null) ask = chk.confirm
                            else if (!chk.ok) { if (chk.msg.isNotBlank()) Ui.show(chk.msg) }
                            else if (handle(Store.mutate { executeSendPix(it, d.who.trim(), v) })) { draft = null; text = "" }
                        }
                        else -> if (handle(Store.mutate { spendFree(it, v, d.cat) })) { draft = null; text = "" }
                    }
                }
                GhostButton("Descartar", Modifier.fillMaxWidth()) { draft = null }
            }
        }
    }
    if (scan) BarcodeScannerDialog({ code -> scan = false; text = code; analyze(code) }, { scan = false })
    ask?.let { msg ->
        Confirm(msg, { ask = null; draft?.let { d -> if (handle(Store.mutate { st -> executeSendPix(st, d.who.trim(), d.amount ?: 0.0) })) { draft = null; text = "" } } }, { ask = null })
    }
}
