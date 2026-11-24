package com.meubanco.patrimonio.domain

/* ============================================================
   Cérebro financeiro: nota de saúde, próximo melhor passo, cobranças repetidas,
   possíveis assinaturas escondidas e simulação "e se eu cortar?".
   Tudo calculado no aparelho, sem enviar nada para fora.
   ============================================================ */

class ScorePart(val label: String, val pts: Double, val max: Double, val tip: String)
class HealthScore(val total: Int, val parts: List<ScorePart>, val nextStep: String)

fun healthScore(s: AppState): HealthScore {
    val income = try { estimatedMonthlyIncome(s) } catch (e: Exception) { 0.0 }
    val essential = essentialMonthlyCost(s)
    val parts = mutableListOf<ScorePart>()

    val months = runway(s).months ?: 0.0
    parts += ScorePart("Reserva de emergência", Math.min(30.0, months / 6.0 * 30.0), 30.0,
        if (months >= 6) "Reserva completa." else "Sua reserva cobre ${Math.round(months * 10) / 10.0} meses. Caminho até 6 meses: guarde na reserva antes de investir.")

    val debtTotal = s.debts.sumOf { it.balance }
    val debtPts = if (debtTotal <= 0.0) 20.0 else if (income > 0) Math.max(0.0, 20.0 * (1 - Math.min(1.0, debtTotal / (income * 6)))) else 0.0
    parts += ScorePart("Dívidas", debtPts, 20.0, if (debtTotal <= 0) "Sem dívidas." else "Você deve ${formatBRL(debtTotal)}. Quitar primeiro as de juros mais altos.")

    val ratio = if (income > 0) essential / income else null
    val fixPts = if (ratio == null) 0.0 else if (ratio <= 0.5) 20.0 else Math.max(0.0, 20.0 * (1 - (ratio - 0.5) / 0.5))
    parts += ScorePart("Custo fixo na renda", fixPts, 20.0, if (ratio == null) "Informe sua renda para medir." else "Seus custos fixos usam ${Math.round(ratio * 100)}% da renda. O ideal é até 50%.")

    val conc = concentration(s)
    parts += ScorePart("Diversificação", (conc?.score ?: 0) * 0.15, 15.0, if (conc == null) "Sem investimentos ainda. Comece pequeno e distribuído." else conc.alerts.first())

    var habit = 0.0
    if (s.reserves.any { it.monthlyContribution > 0 }) habit += 5
    if (s.goals.any { !it.completed && it.monthly > 0 }) habit += 5
    if ((s.settings.autoRoundStep > 0 && s.settings.autoRoundTarget.isNotBlank()) || (s.settings.autoSavePct > 0 && s.settings.autoSaveTarget.isNotBlank())) habit += 5
    parts += ScorePart("Hábito de guardar", habit, 15.0, if (habit >= 15) "Você guarda de forma automática e constante." else "Ative aportes mensais e o piloto automático (Laboratório) para guardar sem pensar.")

    val total = Math.round(parts.sumOf { it.pts }).toInt().coerceIn(0, 100)
    val worst = parts.minByOrNull { it.pts / it.max }
    val next = if (worst == null || worst.pts >= worst.max - 0.01) "Tudo em ordem. Mantenha o ritmo e revise a cada mês." else "Foco agora: ${worst.label}. ${worst.tip}"
    return HealthScore(total, parts, next)
}

/* ---------- Cobranças em dobro ---------- */

class DupCharge(val label: String, val amount: Double, val dateA: String, val dateB: String)

fun duplicateCharges(s: AppState, lookbackDays: Int = 30): List<DupCharge> {
    val from = addDaysISO(AppClock.todayISO(), -lookbackDays)
    val spend = s.history.filter { it.amount < 0 && it.type in setOf("gastolivre", "cartao", "pix") && it.ts.length >= 10 && it.ts.substring(0, 10) >= from }
    val out = mutableListOf<DupCharge>()
    spend.groupBy { merchantKey(it.label.substringAfter("—").substringAfter("-")) + "|" + Math.round(it.amount * 100) }.forEach { (k, v) ->
        if (k.startsWith("|") || v.size < 2) return@forEach
        val d = v.map { it.ts.substring(0, 10) }.sorted()
        for (i in 1 until d.size) if (Math.abs(daysBetweenISO(d[i - 1], d[i])) <= 3) { out += DupCharge(v.first().label, -v.first().amount, d[i - 1], d[i]); break }
    }
    return out
}

/* ---------- Possíveis assinaturas escondidas ---------- */

class Recurring(val name: String, val avg: Double, val months: Int, val day: Int)

fun recurringCharges(s: AppState): List<Recurring> {
    val known = s.subscriptions.map { merchantKey(it.name) }.toSet()
    val spend = s.history.filter { it.amount < 0 && it.type in setOf("gastolivre", "cartao") && it.ts.length >= 10 }
    return spend.groupBy { merchantKey(it.label.substringAfter("—").substringAfter(":")) }.mapNotNull { (k, v) ->
        if (k.isBlank() || k in known) return@mapNotNull null
        val byMonth = v.groupBy { it.ts.substring(0, 7) }
        if (byMonth.size < 2) return@mapNotNull null
        val monthly = byMonth.values.map { m -> m.sumOf { -it.amount } }
        val avg = monthly.average()
        if (monthly.any { Math.abs(it - avg) > avg * 0.15 }) return@mapNotNull null
        Recurring(k.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }, round2(avg), byMonth.size, v.maxByOrNull { it.ts }!!.ts.substring(8, 10).toIntOrNull() ?: 1)
    }.sortedByDescending { it.avg }
}

/* ---------- E se eu cortar? ---------- */

/** Quanto vira cortar [monthly] por mês durante [years] anos, investindo a [annualPct]% ao ano. */
fun whatIfCut(monthly: Double, years: Int, annualPct: Double): Double {
    if (monthly <= 0 || years <= 0) return 0.0
    val n = years * 12; val i = annualPct / 100 / 12
    return round2(if (i > 0) monthly * (Math.pow(1 + i, n.toDouble()) - 1) / i else monthly * n)
}
