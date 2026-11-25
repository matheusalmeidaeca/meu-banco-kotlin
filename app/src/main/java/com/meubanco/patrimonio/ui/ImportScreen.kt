package com.meubanco.patrimonio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

private fun readText(ctx: android.content.Context, uri: android.net.Uri): String {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
    val utf = String(bytes, Charsets.UTF_8)
    return if (utf.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf
}

/** Importar extrato: escolha o arquivo OFX ou CSV exportado do banco, revise e confirme. */
@Composable
fun ImportScreen() {
    val ctx = LocalContext.current; val s = state()
    var rows by remember { mutableStateOf<List<BankRow>>(emptyList()) }
    var invert by remember { mutableStateOf(false) }
    var updateCash by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<BankItem>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Int?>(null) }
    var ask by remember { mutableStateOf(false) }
    var cardMode by remember { mutableStateOf(false) }
    fun rebuild() { items = prepareImport(Store.state, if (cardMode) cardPurchases(rows) else rows, invert && !cardMode) }
    fun load(text: String) {
        val parsed = try { parseBankFile(text) } catch (e: Exception) { emptyList() }
        if (parsed.isEmpty()) Ui.show("Não consegui ler lançamentos nesse arquivo. Use o extrato em OFX ou CSV.")
        else { rows = parsed; rebuild(); open = null }
    }
    LaunchedEffect(Ui.sharedStatement) { Ui.sharedStatement?.let { Ui.sharedStatement = null; load(it) } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            load(try { readText(ctx, uri) } catch (e: Exception) { "" })
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card {
            Txt("Sem Open Finance e sem permissões especiais", bold = true)
            Muted("No app do seu banco, exporte o extrato em OFX ou CSV e escolha o arquivo aqui, ou use Compartilhar → Admoney direto no app do banco. O Admoney categoriza sozinho, aprende com suas correções e nunca importa o mesmo lançamento duas vezes.")
            PrimaryButton(if (items.isEmpty()) "Escolher arquivo do extrato" else "Escolher outro arquivo") { pick.launch(arrayOf("*/*")) }
        }
        if (items.isNotEmpty()) {
            val use = items.filter { it.include && !it.duplicate }
            val exp = use.filter { it.isExpense }; val inc = use.filter { !it.isExpense }
            Card {
                H2("Revisão")
                Muted("${items.size} lançamentos lidos · ${items.count { it.duplicate }} já importados antes (ignorados)")
                Txt("${exp.size} gastos: ${formatBRL(exp.sumOf { -it.row.amount })}", bold = true)
                Txt("${inc.size} entradas: ${formatBRL(inc.sumOf { it.row.amount })}", bold = true)
                ChipRow(listOf("0" to "Conta corrente", "1" to "Fatura do cartão"), if (cardMode) "1" else "0") { v -> cardMode = v == "1"; rebuild() }
                if (cardMode) Muted("As compras entram na fatura aberta do cartão ativo (${activeCard(s).name}). Pagamentos e estornos são ignorados. Importe só a fatura atual.")
                Muted("Os sinais estão trocados (gastos aparecendo como entradas)?")
                ChipRow(listOf("0" to "Está certo", "1" to "Inverter sinais"), if (invert) "1" else "0") { v -> invert = v == "1"; rebuild() }
                if (!cardMode) ChipRow(listOf("1" to "Atualizar o saldo da conta", "0" to "Só registrar no histórico"), if (updateCash) "1" else "0") { updateCash = it == "1" }
                PrimaryButton("Importar ${use.size} lançamentos", enabled = use.isNotEmpty()) { ask = true }
            }
            items.take(40).forEachIndexed { i, it ->
                Card(onClick = { open = if (open == i) null else i }) {
                    Txt((if (it.duplicate) "⏭ " else if (!it.include) "🚫 " else if (it.isExpense) "🔻 " else "🔺 ") + it.row.desc, bold = true)
                    Muted("${it.row.date} · ${formatBRL(it.row.amount)}" + if (it.isExpense && !it.duplicate) " · ${it.category}" else if (it.duplicate) " · já importado" else "")
                    if (open == i && !it.duplicate) {
                        if (it.isExpense) ChipRow((s.freeSpendCategories + listOf("Mercado", "Alimentação", "Transporte", "Saúde", "Assinaturas", "Contas")).distinct().map { c -> c to c }, it.category) { c -> setCategoryForMerchant(items, it, c); tick++ }
                        GhostButton(if (it.include) "Não importar este" else "Importar este") { it.include = !it.include; tick++ }
                    }
                }
            }
            if (items.size > 40) Muted("Mostrando os 40 primeiros para revisão. Os demais ${items.size - 40} entram com as categorias sugeridas.")
        }
        HealthCard(s)
    }
    if (ask) Confirm("Importar ${items.count { it.include && !it.duplicate }} lançamentos?" + if (updateCash) " O saldo da conta será ajustado." else "", {
        ask = false
        if (cardMode) {
            val r = Store.mutate { st -> applyCardImport(st, activeCard(st), items) }
            Ui.show("Fatura: ${r.added} compras (${formatBRL(r.total)}). Limite livre ${formatBRL(r.available)}. ${r.skipped} ignoradas.")
        } else {
            val r = Store.mutate { st -> applyImport(st, items, updateCash) }
            Ui.show("Importado: ${r.expenses} gastos e ${r.incomes} entradas. ${r.skipped} ignorados.")
        }
        items = emptyList(); rows = emptyList()
    }, { ask = false }, "Importar")
}

@Composable
private fun HealthCard(s: AppState) {
    val issues = dataHealth(s)
    Card {
        H2("🛡 Saúde dos dados")
        if (issues.isEmpty()) Txt("✅ Tudo certo: nenhum problema encontrado nos seus dados.")
        else issues.forEach { Txt((if (it.severe) "❗ " else "⚠️ ") + it.text, color = if (it.severe) P.red else P.gold) }
        Muted("O app confere saldos, caixinhas, metas, ativos e regras automáticas. Faça backup com frequência em Configurações.")
    }
}
