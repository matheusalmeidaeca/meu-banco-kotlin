package com.meubanco.patrimonio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Backup
import com.meubanco.patrimonio.domain.*

@Composable
fun HistoricoScreen() {
    val s = state()
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf("todos") }
    var limit by remember { mutableIntStateOf(40) }
    var q by remember { mutableStateOf("") }
    var monthSel by remember { mutableStateOf("") } // "" = todos
    val all = remember(com.meubanco.patrimonio.data.Store.rev, filter, q, monthSel) {
        s.history.sortedByDescending { it.ts.take(10) }.filter { (filter == "todos" || it.type == filter) &&
            (q.isBlank() || it.label.contains(q.trim(), ignoreCase = true)) &&
            (monthSel.isEmpty() || it.ts.startsWith(monthSel)) }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) Ui.show(try { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(historyToCsv(all).toByteArray()) }; "CSV salvo" } catch (e: Exception) { "Falha ao salvar" })
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) Ui.show(if (Backup.exportStatementPdf(ctx, uri, s, all.take(400))) "PDF salvo" else "Falha ao gerar PDF")
    }
    val ym = ymKey(s.simDateISO)
    val st = s.monthlyStats[ym]

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            H2("Resumo de ${formatMonthLabel(ym)}")
            KV("Entradas", money(st?.income ?: 0.0), P.green)
            KV("Gastos", money(st?.spent ?: 0.0), P.red)
            KV("Investido", money(st?.invested ?: 0.0), P.brand)
            val cats = st?.cats?.filter { it.value > 0.005 }?.toList()?.sortedByDescending { it.second }.orEmpty()
            if (cats.isNotEmpty()) { Spacer8(); Muted("Para onde foi o dinheiro"); BarList(cats.take(8), P.gold) }
        }
        if (s.netWorthHistory.size >= 2) Card {
            H2("Patrimônio no tempo")
            LineChart(s.netWorthHistory.takeLast(36).map { it.value }, color = P.green)
            Muted("${formatMonthLabel(s.netWorthHistory.takeLast(36).first().ym)} → ${formatMonthLabel(s.netWorthHistory.last().ym)}")
        }
        H2("Extrato & histórico")
        ChipRow(HIST_TYPE_LABELS.map { it.key to it.value }, filter) { filter = it; limit = 40 }
        Field(q, { q = it; limit = 40 }, "🔎 Buscar no extrato (ex.: mercado, Pix)")
        val months = remember(com.meubanco.patrimonio.data.Store.rev) { s.history.map { it.ts.take(7) }.distinct().sortedDescending().take(12) }
        ChipRow(listOf("" to "Todos os meses") + months.map { it to formatMonthLabel(it) }, monthSel) { monthSel = it; limit = 40 }
        if (all.isNotEmpty()) {
            val inn = all.filter { it.amount > 0 }.sumOf { it.amount }; val out = all.filter { it.amount < 0 }.sumOf { it.amount }
            Card { KV("Entradas no filtro", money(inn), P.green); KV("Saídas no filtro", money(-out), P.red); Muted("${all.size} lançamento(s)") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("PDF", Modifier.weight(1f)) { pdf.launch("extrato-" + AppClock.todayISO() + ".pdf") }
            GhostButton("CSV (planilha)", Modifier.weight(1f)) { csv.launch("extrato-" + AppClock.todayISO() + ".csv") }
        }
        if (all.isEmpty()) Muted("Nada por aqui ainda.")
        var lastYm = ""
        all.take(limit).forEach { h ->
            val hy = h.ts.take(7)
            if (hy != lastYm) {
                lastYm = hy
                val mi = all.filter { it.ts.startsWith(hy) }
                Column(Modifier.padding(top = 6.dp)) {
                    H2(formatMonthLabel(hy))
                    Muted("Entradas ${money(mi.filter { it.amount > 0 }.sumOf { it.amount })} · Saídas ${money(-mi.filter { it.amount < 0 }.sumOf { it.amount })} · ${mi.size} lançamento(s)")
                }
            }
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("${HIST_TYPE_ICON[h.type] ?: "•"} ${h.label}", Modifier.weight(1f), style = T.small)
                    Spacer(Modifier.width(8.dp))
                    if (h.amount != 0.0) Txt(money(h.amount), color = if (h.amount > 0) P.green else P.red, bold = true, style = T.small)
                }
                Muted(h.ts.take(10).let { formatSimDate(it) })
            }
        }
        if (all.size > limit) GhostButton("Mostrar mais") { limit += 40 }
    }
}
