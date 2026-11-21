package com.meubanco.patrimonio.domain

import java.time.LocalDate

/* ===================== calculadoras: empréstimo, negociação, investimentos ===================== */

/** Parcela fixa (tabela Price). ratePct = juros ao mês. */
fun pmt(pv: Double, ratePct: Double, n: Int): Double {
    if (n <= 0) return pv
    val i = ratePct / 100
    return if (i <= 0.0) pv / n else pv * i / (1 - Math.pow(1 + i, -n.toDouble()))
}

class LoanOffer(val payment: Double, val total: Double, val interest: Double, val annualPct: Double)

fun loanOffer(amount: Double, ratePct: Double, n: Int): LoanOffer {
    val p = pmt(amount, ratePct, n); val total = p * n
    return LoanOffer(round2(p), round2(total), round2(total - amount), (Math.pow(1 + ratePct / 100, 12.0) - 1) * 100)
}

class Negotiation(val keepTotal: Double, val cashOffer: Double, val savings: Double, val discountPct: Double)

/** Compara continuar pagando as parcelas restantes com quitar à vista pelo valor oferecido. */
fun negotiation(balance: Double, ratePct: Double, monthsLeft: Int, cashOffer: Double): Negotiation {
    val keep = pmt(balance, ratePct, monthsLeft) * monthsLeft
    return Negotiation(round2(keep), round2(cashOffer), round2(keep - cashOffer), if (balance > 0) (1 - cashOffer / balance) * 100 else 0.0)
}

class InvestRow(val name: String, val annualPct: Double, val gross: Double, val tax: Double, val net: Double)

fun irRateForDays(days: Int) = when { days <= 180 -> 0.225; days <= 360 -> 0.20; days <= 720 -> 0.175; else -> 0.15 }

/** Rendimento anual da poupança: 0,5% a.m. + TR (ignorada) se Selic > 8,5%; senão 70% da Selic. */
fun poupancaAnnualPct(selicPct: Double) = if (selicPct > 8.5) (Math.pow(1.005, 12.0) - 1) * 100 else selicPct * 0.7

/** Estimativa simples (sem TR, sem taxa de custódia, taxa constante). Valores em termos nominais. */
fun compareInvest(amount: Double, months: Int, selicPct: Double, cdbPctCdi: Double = 100.0): List<InvestRow> {
    val cdi = Math.max(0.0, selicPct - 0.10)
    val rows = mutableListOf<InvestRow>()
    fun add(name: String, annual: Double, taxed: Boolean) {
        val gross = amount * Math.pow(1 + annual / 100, months / 12.0)
        val gain = gross - amount
        val tax = if (taxed) gain * irRateForDays((months * 365 / 12.0).toInt()) else 0.0
        rows.add(InvestRow(name, annual, round2(gross), round2(tax), round2(gross - tax)))
    }
    add("Poupança", poupancaAnnualPct(selicPct), false)
    add("Tesouro Selic", selicPct, true)
    add("CDB ${"%.0f".format(cdbPctCdi)}% do CDI", cdi * cdbPctCdi / 100, true)
    return rows.sortedByDescending { it.net }
}

/* ===================== renda variável ===================== */

class VariableIncome(val months: Int, val avg: Double, val min: Double, val max: Double, val baseline: Double, val swingPct: Double)

/** Usa os meses já fechados (antes do atual) com entrada registrada. null se houver menos de 2. */
fun variableIncome(s: AppState): VariableIncome? {
    val cur = ymKey(s.simDateISO)
    val vals = s.monthlyStats.filter { it.key < cur && it.value.income > 0.005 }.toList().sortedBy { it.first }.takeLast(6).map { it.second.income }
    if (vals.size < 2) return null
    val avg = vals.average()
    val low3 = vals.sorted().take(3).average()
    return VariableIncome(vals.size, round2(avg), round2(vals.min()), round2(vals.max()), round2(low3), if (avg > 0) (vals.max() - vals.min()) / avg else 0.0)
}

/* ===================== metas com prazo ===================== */

fun addMonthsYM(todayISO: String, n: Int): String { val d = LocalDate.parse(todayISO).plusMonths(n.toLong()); return "%04d-%02d".format(d.year, d.monthValue) }

fun monthsBetweenYM(fromYM: String, toYM: String): Int =
    (toYM.substring(0, 4).toInt() - fromYM.substring(0, 4).toInt()) * 12 + (toYM.substring(5, 7).toInt() - fromYM.substring(5, 7).toInt())

/** Aporte mensal necessário para atingir a meta no prazo (null se não há prazo, já concluída ou prazo vencido). */
fun goalMonthlyNeeded(g: Goal, todayISO: String): Double? {
    val dl = g.deadlineYM ?: return null
    if (g.completed) return null
    val n = monthsBetweenYM(ymKey(todayISO), dl)
    if (n <= 0) return null
    val i = g.rate / 100 / 12
    val missing = if (i > 0) g.target - g.saved * Math.pow(1 + i, n.toDouble()) else g.target - g.saved
    if (missing <= 0) return 0.0
    val need = if (i > 0) missing * i / (Math.pow(1 + i, n.toDouble()) - 1) else missing / n
    return Math.ceil(need * 100) / 100
}

/* ===================== alerta de Pix fora do padrão ===================== */

fun pixRiskFlags(s: AppState, name: String, amount: Double, hour: Int = java.time.LocalTime.now().hour): List<String> {
    val flags = mutableListOf<String>()
    if (amount <= 0) return flags
    if (s.cash > 0 && amount >= s.cash * 0.8) flags.add("Esse Pix usa mais de 80% do seu saldo.")
    val past = s.history.filter { it.type == "pix" && it.label.startsWith("Pix enviado para ") }.map { Math.abs(it.amount) }.sorted()
    if (past.size >= 3) {
        val med = past[past.size / 2]
        if (med > 0 && amount >= med * 4) flags.add("Valor bem maior que o seu Pix típico (cerca de ${formatBRL(med)}).")
    }
    if (hour in 0..4) flags.add("Pix de madrugada: golpistas contam com pressa e cansaço. Confirme com calma.")
    return flags
}

/* ===================== dividir contas (casal / família) ===================== */

/** Positivo: o(a) parceiro(a) deve a você. Negativo: você deve. Considera só contas em aberto. */
fun sharedBalance(s: AppState): Double = round2(s.sharedBills.filter { !it.settled }.sumOf {
    if (it.paidByMe) it.amount * (1 - it.mySharePct / 100) else -it.amount * it.mySharePct / 100
})

fun sharedShareText(s: AppState): String {
    val sb = StringBuilder("Contas divididas\n")
    s.sharedBills.filter { !it.settled }.forEach {
        sb.append("• ${it.name}: ${formatBRL(it.amount)} (pago por ${if (it.paidByMe) "mim" else s.partnerName}, minha parte ${"%.0f".format(it.mySharePct)}%)\n")
    }
    val b = sharedBalance(s)
    sb.append(if (b > 0) "${s.partnerName} me deve ${formatBRL(b)}" else if (b < 0) "Eu devo ${formatBRL(-b)} para ${s.partnerName}" else "Estamos quites")
    return sb.toString()
}

/* ===================== resumo para o Imposto de Renda ===================== */

class IrSummary(
    val year: Int, val income: Double, val spent: Double, val health: Double, val education: Double,
    val dividends: Double, val dividendsByTicker: List<Pair<String, Double>>, val assets: List<Pair<String, Double>>, val debts: Double,
)

fun irSummary(s: AppState, year: Int): IrSummary {
    val prefix = "%04d-".format(year)
    val ms = s.monthlyStats.filter { it.key.startsWith(prefix) }.values
    val byTicker = linkedMapOf<String, Double>()
    s.dividendBreakdown.filter { it.key.startsWith(prefix) }.values.forEach { m -> m.forEach { (t, v) -> byTicker[t] = (byTicker[t] ?: 0.0) + v } }
    val assets = listOf(
        "Conta corrente" to s.cash, "Caixinhas" to reservesTotalBalance(s), "Metas" to goalsTotalSaved(s),
        "Carteira de investimentos" to portfolioValue(s) + s.leftover, "FGTS" to s.fgts.balance,
    ).filter { it.second > 0.005 }.map { it.first to round2(it.second) }
    return IrSummary(year, round2(ms.sumOf { it.income }), round2(ms.sumOf { it.spent }),
        round2(ms.sumOf { it.cats["Saúde"] ?: 0.0 }), round2(ms.sumOf { it.cats["Educação"] ?: 0.0 }),
        round2(byTicker.values.sum()), byTicker.toList().sortedByDescending { it.second }.map { it.first to round2(it.second) }, assets, round2(debtTotal(s)))
}

fun irShareText(r: IrSummary): String {
    val sb = StringBuilder("Resumo para o IR ${r.year} (organização pessoal, não é declaração)\n")
    sb.append("Entradas registradas: ${formatBRL(r.income)}\nGastos registrados: ${formatBRL(r.spent)}\n")
    sb.append("Saúde: ${formatBRL(r.health)}\nEducação: ${formatBRL(r.education)}\nDividendos recebidos: ${formatBRL(r.dividends)}\n")
    r.dividendsByTicker.forEach { sb.append("  ${it.first}: ${formatBRL(it.second)}\n") }
    sb.append("Bens e direitos hoje:\n"); r.assets.forEach { sb.append("  ${it.first}: ${formatBRL(it.second)}\n") }
    if (r.debts > 0) sb.append("Dívidas: ${formatBRL(r.debts)}\n")
    sb.append("Confira os valores oficiais nos informes de rendimentos e as regras vigentes na Receita Federal.")
    return sb.toString()
}
