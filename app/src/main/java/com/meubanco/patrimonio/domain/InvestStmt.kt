package com.meubanco.patrimonio.domain

/* ============================================================
   Extrato: movimentos internos x dinheiro que realmente entra e sai
   Transferir para a carteira, guardar em caixinha e comprar cotas movem o SEU dinheiro de um lugar
   para outro. Contar tudo como saída contava o mesmo dinheiro duas vezes.
   ============================================================ */

/** Movimentos entre as suas próprias contas, caixinhas e carteira. */
val INTERNAL_TYPES = setOf("transferencia", "aporte", "cdb", "meta", "compra", "venda", "saque")
/** Dos internos, os que tiram (ou devolvem) dinheiro da conta principal para guardar. */
private val SAVING_TYPES = setOf("transferencia", "aporte", "cdb", "meta", "saque")

fun isInternalMove(h: HistoryEntry) = h.type in INTERNAL_TYPES

class StmtTotals(val inflow: Double, val outflow: Double, val saved: Double)

fun statementTotals(list: List<HistoryEntry>): StmtTotals {
    val real = list.filter { !isInternalMove(it) }
    return StmtTotals(
        round2(real.filter { it.amount > 0 }.sumOf { it.amount }),
        round2(-real.filter { it.amount < 0 }.sumOf { it.amount }),
        round2(-list.filter { it.type in SAVING_TYPES }.sumOf { it.amount }),
    )
}

/** Uma linha do extrato do CAIXA da carteira de investimentos (valor visto de dentro da carteira). */
class InvRow(val ts: String, val label: String, val amount: Double, val type: String)

fun investmentStatement(s: AppState): List<InvRow> {
    val types = setOf("transferencia", "aporte", "saque", "compra", "venda", "dividendo", "imposto")
    return s.history.filter { it.type in types && it.amount != 0.0 }
        .filter { it.type != "imposto" || it.label.contains("DARF") }
        .sortedByDescending { it.ts.take(10) }
        .map { h ->
            // transferir/aportar/sacar mudam de lado: o que SAI da conta principal ENTRA no caixa da carteira
            val a = if (h.type == "transferencia" || h.type == "aporte" || h.type == "saque") -h.amount else h.amount
            InvRow(h.ts, h.label, round2(a), h.type)
        }
}

/* ---------- Patrimônio investido no tempo ---------- */

class InvPoint(val ym: String, val value: Double)

/** Valor investido (cotas, caixa da carteira, caixinhas e metas) mês a mês, com o ponto de hoje no fim. */
fun investedSeries(s: AppState): List<InvPoint> {
    val ym = ymKey(s.simDateISO)
    val now = round2(portfolioValue(s) + s.leftover + reservesTotalBalance(s) + goalsTotalSaved(s))
    val past = s.netWorthHistory.filter { it.invested != null && it.ym != ym }.map { InvPoint(it.ym, it.invested!!) }
    return (past + InvPoint(ym, now)).sortedBy { it.ym }.takeLast(36)
}

/** Variação em % em relação ao primeiro ponto com valor positivo. */
fun investedPercent(series: List<InvPoint>): List<Double> {
    val base = series.firstOrNull { it.value > 0.0 }?.value ?: return series.map { 0.0 }
    return series.map { round2((it.value / base - 1) * 100) }
}
