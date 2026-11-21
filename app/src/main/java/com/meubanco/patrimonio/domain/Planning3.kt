package com.meubanco.patrimonio.domain

import java.time.LocalDate

/* ============================================================
   v54 — metas de investimento: aporte médio e dividendos reais,
   juros compostos, meta de patrimônio e de renda passiva
   ============================================================ */

/** Registra aportes líquidos à carteira e dividendos por mês (sem o limite de 500 lançamentos do histórico). */
fun recordPortfolioFlow(h: HistoryEntry, s: AppState) {
    if (h.ts.length < 7 || h.amount == 0.0) return
    val ym = h.ts.substring(0, 7)
    when (h.type) {
        "aporte", "transferencia" -> s.contribByMonth[ym] = round2((s.contribByMonth[ym] ?: 0.0) - h.amount)
        "dividendo" -> if (h.amount > 0) s.divByMonth[ym] = round2((s.divByMonth[ym] ?: 0.0) + h.amount)
    }
}

/** Dados salvos antes da v54: reconstrói os totais mensais a partir do histórico disponível. */
fun rebuildFlowsIfNeeded(m: AppState) {
    if (m.flowsMigrated) return
    if (m.contribByMonth.isEmpty() && m.divByMonth.isEmpty()) m.history.forEach { recordPortfolioFlow(it, m) }
    m.flowsMigrated = true
}

fun prevYM(ym: String): String { val d = LocalDate.parse("$ym-01").minusMonths(1); return "%04d-%02d".format(d.year, d.monthValue) }
fun nextYM(ym: String): String { val d = LocalDate.parse("$ym-01").plusMonths(1); return "%04d-%02d".format(d.year, d.monthValue) }

/** Primeiro mês com aporte ou dividendo e último mês fechado (ou o atual, se ainda não há mês fechado). */
private fun flowRange(s: AppState): List<String> {
    val first = (s.contribByMonth.filter { it.value > 0.005 }.keys + s.divByMonth.filter { it.value > 0.005 }.keys).minOrNull() ?: return emptyList()
    val cur = ymKey(s.simDateISO)
    val end = if (first >= cur) cur else prevYM(cur)
    val out = mutableListOf<String>(); var m = first
    while (m <= end && out.size < 600) { out.add(m); m = nextYM(m) }
    return out
}

class ContribStats(val months: Int, val monthsWithContribution: Int, val total: Double, val avg: Double, val firstYM: String)

/** Média de aporte desde o primeiro aporte até o último mês fechado. Meses sem aporte entram como zero. */
fun contributionStats(s: AppState): ContribStats? {
    val first = s.contribByMonth.filter { it.value > 0.005 }.keys.minOrNull() ?: return null
    val range = flowRange(s).filter { it >= first }
    if (range.isEmpty()) return null
    val vals = range.map { s.contribByMonth[it] ?: 0.0 }
    val total = vals.sum()
    return ContribStats(range.size, vals.count { it > 0.005 }, round2(total), round2(Math.max(0.0, total / range.size)), first)
}

class DivStats(val months: Int, val total: Double, val avgMonthly: Double, val annualYieldPct: Double, val fromHistory: Boolean)

/**
 * Rendimento médio em dividendos. Usa todo o histórico, inclusive meses sem dividendo:
 * soma dos dividendos ÷ soma do capital já aportado no início de cada mês.
 * Sem histórico suficiente, cai para o rendimento atual dos ativos.
 */
fun dividendStats(s: AppState): DivStats {
    val range = flowRange(s)
    var cum = 0.0; var num = 0.0; var den = 0.0; var total = 0.0
    for (ym in range) {
        val base = cum
        val d = s.divByMonth[ym] ?: 0.0
        total += d
        if (base > 0.005) { num += d; den += base }
        cum += s.contribByMonth[ym] ?: 0.0
    }
    if (den > 0.005 && num > 0.0) {
        return DivStats(range.size, round2(total), round2(if (range.isEmpty()) 0.0 else total / range.size), num / den * 12 * 100, true)
    }
    val pv = portfolioValue(s)
    val fallback = if (pv > 0) monthlyDividendIncome(s) / pv * 12 * 100 else 0.0
    return DivStats(range.size, round2(total), round2(if (range.isEmpty()) 0.0 else total / range.size), fallback, false)
}

class InvestParams(
    val start: Double, val monthly: Double, val growthPct: Double, val yieldPct: Double,
    val reinvest: Boolean = true, val inflationPct: Double = 0.0, val contribGrowthPct: Double = 0.0,
)

class InvestSim(
    val months: Int?, val balance: Double, val contributed: Double, val gains: Double,
    val dividendsTotal: Double, val monthlyIncome: Double, val yearly: List<Double>,
)

fun inflationFactor(p: InvestParams, month: Int) = Math.pow(1 + p.inflationPct / 100, month / 12.0)

/** Simula mês a mês: valorização + dividendos (reinvestidos ou não) + aporte. stop(mês, patrimônio, renda mensal). */
fun simulateInvest(p: InvestParams, maxMonths: Int = 1200, stop: (Int, Double, Double) -> Boolean): InvestSim {
    val g = Math.pow(1 + p.growthPct / 100, 1.0 / 12) - 1
    val y = p.yieldPct / 100 / 12
    var bal = p.start; var contributed = 0.0; var divs = 0.0
    val yearly = mutableListOf(bal)
    if (stop(0, bal, bal * y)) return InvestSim(0, bal, 0.0, 0.0, 0.0, bal * y, yearly)
    var m = 0
    while (m < maxMonths) {
        m++
        val div = bal * y
        divs += div
        val c = p.monthly * Math.pow(1 + p.contribGrowthPct / 100, ((m - 1) / 12).toDouble())
        bal = bal * (1 + g) + (if (p.reinvest) div else 0.0) + c
        contributed += c
        if (m % 12 == 0) yearly.add(bal)
        if (stop(m, bal, bal * y)) {
            if (m % 12 != 0) yearly.add(bal)
            return InvestSim(m, round2(bal), round2(contributed), round2(bal - p.start - contributed), round2(divs), round2(bal * y), yearly)
        }
    }
    return InvestSim(null, round2(bal), round2(contributed), round2(bal - p.start - contributed), round2(divs), round2(bal * y), yearly)
}

fun patrimonyGoal(p: InvestParams, target: Double, inflate: Boolean) =
    simulateInvest(p) { m, b, _ -> b >= target * (if (inflate) inflationFactor(p, m) else 1.0) }

fun incomeGoal(p: InvestParams, targetMonthly: Double, inflate: Boolean) =
    simulateInvest(p) { m, _, inc -> inc >= targetMonthly * (if (inflate) inflationFactor(p, m) else 1.0) }

/** Patrimônio necessário para os dividendos pagarem a renda desejada. */
fun requiredPatrimony(targetMonthly: Double, yieldPct: Double): Double? =
    if (yieldPct > 0) round2(targetMonthly / (yieldPct / 100 / 12)) else null

fun monthsLabel(m: Int): String {
    val a = m / 12; val r = m % 12
    return when {
        m == 0 -> "agora"
        a == 0 -> "$r mês(es)"
        r == 0 -> "$a ano(s)"
        else -> "$a ano(s) e $r mês(es)"
    }
}

class GrowthInfo(val pct: Double, val months: Int, val assets: Int, val clamped: Boolean)

/**
 * Valorização anual do preço dos seus ativos, vinda do histórico do arquivo de cotações.
 * Média ponderada pelo valor de cada posição (sem posição, média simples dos ativos com dado).
 * Limitada a -10%…+15% ao ano por prudência: retornos passados muito altos não se repetem sempre.
 */
fun portfolioGrowth(s: AppState): GrowthInfo? {
    val held = s.assets.filter { it.quantity > 0 && it.price > 0 && it.valAnnualPct != null }
    val pool = if (held.isNotEmpty()) held else s.assets.filter { it.valAnnualPct != null }
    if (pool.isEmpty()) return null
    val w = pool.map { if (held.isNotEmpty()) it.quantity * it.price else 1.0 }
    val raw = pool.indices.sumOf { w[it] * pool[it].valAnnualPct!! } / w.sum()
    val pct = clamp(raw, -10.0, 15.0)
    return GrowthInfo(round2(pct), pool.minOf { it.valMonths ?: 0 }, pool.size, pct != raw)
}

/** IPCA acumulado em 12 meses (BrasilAPI); sem dado, 4,5%. O valor digitado por você tem prioridade. */
fun inflationPct(s: AppState) = s.investInflationOverride ?: s.market.ipcaPct ?: 4.5

fun growthBasePct(s: AppState) = s.investGrowthPct ?: portfolioGrowth(s)?.pct ?: s.market.simGrowthPct

/** Parâmetros efetivos: dados reais do histórico, com ajuste manual quando você preferir. growthDelta é usado nos cenários. */
fun investParams(s: AppState, growthDelta: Double = 0.0, extraMonthly: Double = 0.0, withInflation: Boolean = true): InvestParams {
    val auto = contributionStats(s)?.avg ?: (if (s.recurringInvestEnabled) s.monthlyContribution else 0.0)
    val monthly = s.investContribOverride ?: auto
    val yld = s.investYieldOverride ?: dividendStats(s).annualYieldPct
    val growth = Math.max(-50.0, growthBasePct(s) + growthDelta)
    return InvestParams(portfolioValue(s) + s.leftover, monthly + extraMonthly, growth, yld, s.investReinvest,
        if (withInflation) inflationPct(s) else 0.0, s.investContribGrowthPct)
}
