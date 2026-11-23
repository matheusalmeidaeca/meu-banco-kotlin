package com.meubanco.patrimonio.domain

import java.time.LocalDate

/* ============================================================
   Laboratório de caixinhas e investimentos
   Dia Zero, custo de esperar, piloto automático, aporte guiado, teste de estresse e concentração.
   Tudo é simulação para você se organizar: não é recomendação de investimento.
   ============================================================ */

/* ---------- Dia Zero: quanto tempo a reserva sustenta o seu custo de vida ---------- */

class RunwayLevel(val label: String, val months: Double, val reached: Boolean)
class Runway(val essential: Double, val balance: Double, val months: Double?, val zeroDateISO: String?, val levels: List<RunwayLevel>)

fun emergencyReserve(s: AppState): Reserve? = s.reserves.firstOrNull { it.name.contains("emerg", ignoreCase = true) } ?: s.reserves.firstOrNull()

fun essentialMonthlyCost(s: AppState) = fixedExpensesTotal(s) + subscriptionsMonthlyTotal(s) + s.debts.sumOf { it.minPayment }

fun runway(s: AppState): Runway {
    val essential = round2(essentialMonthlyCost(s)); val bal = emergencyReserve(s)?.balance ?: 0.0
    val months = if (essential > 0) bal / essential else null
    val zero = months?.takeIf { it < 1200 }?.let { LocalDate.parse(AppClock.todayISO()).plusDays(Math.round(it * 30.4)).toString() }
    val lv = listOf("1 semana" to 0.25, "1 mês" to 1.0, "3 meses" to 3.0, "6 meses" to 6.0).map { (l, m) -> RunwayLevel(l, m, months != null && months >= m) }
    return Runway(essential, round2(bal), months, zero, lv)
}

/* ---------- Metas com prazo: aporte necessário e custo de esperar ---------- */

/** Aporte mensal para chegar a [target] em [n] meses (taxa anual em %). Null se o prazo já passou. */
fun requiredMonthly(saved: Double, target: Double, n: Int, annualPct: Double): Double? {
    if (n <= 0) return null
    val i = annualPct / 100 / 12
    val missing = if (i > 0) target - saved * Math.pow(1 + i, n.toDouble()) else target - saved
    if (missing <= 0) return 0.0
    return round2(if (i > 0) missing * i / (Math.pow(1 + i, n.toDouble()) - 1) else missing / n)
}

class GoalPlan(val goal: Goal, val monthsLeft: Int, val required: Double, val ifWait3: Double?)

fun goalPlans(s: AppState): List<GoalPlan> = s.goals.filter { !it.completed && it.deadlineYM != null && it.target > 0 }.mapNotNull { g ->
    val n = monthsBetweenYM(ymKey(AppClock.todayISO()), g.deadlineYM!!)
    val r = requiredMonthly(g.saved, g.target, n, g.rate) ?: return@mapNotNull null
    GoalPlan(g, n, r, requiredMonthly(g.saved, g.target, n - 3, g.rate))
}

/* ---------- Piloto automático: arredondar gastos e guardar parte do que entra ---------- */

/** Move [amount] da conta para uma caixinha ("reserve:ID" ou "goal:ID"). Devolve quanto saiu de fato. */
fun moveToCaixinha(s: AppState, optId: String, amount: Double): Double {
    val parts = optId.split(":"); val id = parts.getOrNull(1)?.toIntOrNull() ?: return 0.0
    if (amount <= 0.0) return 0.0
    val before = s.cash
    when (parts[0]) { "reserve" -> addExtraToReserve(s, id, amount); "goal" -> addExtraToGoal(s, id, amount); else -> return 0.0 }
    return round2(before - s.cash)
}

fun autoRoundUp(s: AppState, spent: Double): Double {
    val step = s.settings.autoRoundStep
    if (step <= 0 || s.settings.autoRoundTarget.isBlank() || spent <= 0) return 0.0
    val delta = round2(Math.ceil(spent / step - 1e-9) * step - spent)
    if (delta < 0.01) return 0.0
    return moveToCaixinha(s, s.settings.autoRoundTarget, delta)
}

fun autoSaveOnIncome(s: AppState, received: Double): Double {
    val pct = s.settings.autoSavePct
    if (pct <= 0 || s.settings.autoSaveTarget.isBlank() || received <= 0) return 0.0
    return moveToCaixinha(s, s.settings.autoSaveTarget, round2(received * pct / 100))
}

/* ---------- Aporte guiado: onde o próximo aporte aproxima sua carteira da distribuição escolhida ---------- */

val ALLOC_PRESETS = mapOf(
    "conservador" to mapOf("rf" to 60.0, "acao" to 10.0, "tijolo" to 15.0, "papel" to 15.0),
    "moderado" to mapOf("rf" to 35.0, "acao" to 30.0, "tijolo" to 20.0, "papel" to 15.0),
    "arrojado" to mapOf("rf" to 15.0, "acao" to 50.0, "tijolo" to 20.0, "papel" to 15.0),
)
val CLASS_LABEL = mapOf("rf" to "Renda fixa (caixinhas)", "acao" to "Ações", "tijolo" to "FIIs de tijolo", "papel" to "FIIs de papel")

fun currentAllocation(s: AppState): Map<String, Double> {
    val m = linkedMapOf("rf" to s.reserves.sumOf { it.balance }, "acao" to 0.0, "tijolo" to 0.0, "papel" to 0.0)
    s.assets.forEach { a -> if (a.quantity > 0 && m.containsKey(a.category)) m[a.category] = m[a.category]!! + a.quantity * a.price }
    return m
}

class AporteSugestao(val cls: String, val label: String, val amount: Double, val currentPct: Double, val targetPct: Double, val hint: String?)

fun rebalanceAdvice(s: AppState, aporte: Double, preset: String = s.settings.allocPreset): List<AporteSugestao> {
    if (aporte <= 0) return emptyList()
    val target = ALLOC_PRESETS[preset] ?: ALLOC_PRESETS["moderado"]!!
    val cur = currentAllocation(s); val total = cur.values.sum(); val after = total + aporte
    val deficit = target.mapValues { (k, p) -> Math.max(0.0, after * p / 100 - (cur[k] ?: 0.0)) }
    val sumDef = deficit.values.sum()
    val amounts = target.mapValues { (k, p) ->
        if (sumDef <= 0) aporte * p / 100
        else if (aporte <= sumDef) aporte * (deficit[k] ?: 0.0) / sumDef
        else (deficit[k] ?: 0.0) + (aporte - sumDef) * p / 100
    }
    return amounts.filter { it.value >= 0.01 }.map { (k, v) ->
        val hint = if (k == "rf") null else s.assets.filter { it.category == k && it.price > 0 }.minByOrNull { it.quantity * it.price }?.let { a ->
            val q = Math.floor(v / a.price).toInt(); if (q >= 1) "ideia: ${a.ticker} (~$q cota(s))" else null
        }
        AporteSugestao(k, CLASS_LABEL[k] ?: k, round2(v), if (total > 0) (cur[k] ?: 0.0) / total * 100 else 0.0, target[k] ?: 0.0, hint)
    }.sortedByDescending { it.amount }
}

/* ---------- Teste de estresse da carteira ---------- */

class StressScenario(val label: String, val equityPct: Double, val fiiPct: Double, val dividendCutPct: Double)
class StressResult(val label: String, val before: Double, val after: Double, val delta: Double, val incomeBefore: Double, val incomeAfter: Double, val fiCoverageAfterPct: Double?)

val STRESS_SCENARIOS = listOf(
    StressScenario("Correção leve", -10.0, -8.0, 0.0),
    StressScenario("Queda forte", -25.0, -20.0, 10.0),
    StressScenario("Crise severa", -40.0, -30.0, 25.0),
)

fun stressTest(s: AppState): List<StressResult> {
    val held = s.assets.filter { it.quantity > 0 && it.price > 0 }
    val before = held.sumOf { it.quantity * it.price }; val income = monthlyDividendIncome(s)
    val target = fiStatus(s).target
    return STRESS_SCENARIOS.map { sc ->
        val after = held.sumOf { a -> a.quantity * a.price * (1 + (if (a.category == "acao") sc.equityPct else sc.fiiPct) / 100) }
        val inc = income * (1 - sc.dividendCutPct / 100)
        StressResult(sc.label, round2(before), round2(after), round2(after - before), round2(income), round2(inc), if (target > 0) inc / target * 100 else null)
    }
}

/* ---------- Concentração e diversificação ---------- */

class Concentration(val score: Int, val topAsset: String, val topAssetPct: Double, val topClassPct: Double, val count: Int, val alerts: List<String>)

fun concentration(s: AppState): Concentration? {
    val held = s.assets.filter { it.quantity > 0 && it.price > 0 }
    val total = held.sumOf { it.quantity * it.price }
    if (total <= 0) return null
    val top = held.maxByOrNull { it.quantity * it.price }!!
    val topPct = top.quantity * top.price / total * 100
    val byClass = held.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.quantity * it.price } / total * 100 }
    val topClass = byClass.values.max()
    var score = 100.0; val alerts = mutableListOf<String>()
    if (topPct > 30) { score -= (topPct - 30) * 1.2; alerts += "${top.ticker} pesa ${Math.round(topPct)}% da carteira. Um único ativo concentra bastante risco." }
    if (topClass > 70) { score -= (topClass - 70); alerts += "Uma só classe responde por ${Math.round(topClass)}% da carteira." }
    if (held.size < 3) { score -= 15; alerts += "Poucos ativos (${held.size}). Mais ativos distribuem melhor o risco." }
    if (alerts.isEmpty()) alerts += "Boa distribuição entre ativos e classes."
    return Concentration(Math.max(0.0, Math.min(100.0, score)).toInt(), top.ticker, topPct, topClass, held.size, alerts)
}
