package com.meubanco.patrimonio.domain

/* ============================================================
   Cérebro financeiro: nota de saúde, próximo melhor passo, cobranças repetidas,
   possíveis assinaturas escondidas e simulação "e se eu cortar?".
   Tudo calculado no aparelho, sem enviar nada para fora.
   ============================================================ */

class ScorePart(val label: String, val pts: Double, val max: Double, val tip: String)
class HealthScore(val total: Int, val parts: List<ScorePart>, val nextStep: String)

fun healthScore(s: AppState): HealthScore {
    val h = financialHealth(s)
    val parts = h.parts.map { ScorePart(it.title, it.points.toDouble(), it.max.toDouble(), it.tip) }
    val worst = parts.minByOrNull { it.pts / it.max }
    val next = if (worst == null || worst.pts >= worst.max - 0.01) "Tudo em ordem. Mantenha o ritmo e revise a cada mês." else "Foco agora: ${worst.label}. ${worst.tip}"
    return HealthScore(h.score, parts, next)
}

/** Pix para pessoas e aportes/resgates em carteiras não são cobranças de comércio. */
private fun looksInternal(label: String): Boolean {
    val p = label.lowercase()
    return listOf("pix", "carteira", "aporte", "resgate", "cofrinho", "transfer", "enviado ", "recebido").any { p.contains(it) }
}

/* ---------- Cobranças em dobro ---------- */

class DupCharge(val label: String, val amount: Double, val dateA: String, val dateB: String)

fun duplicateCharges(s: AppState, lookbackDays: Int = 30): List<DupCharge> {
    val from = addDaysISO(AppClock.todayISO(), -lookbackDays)
    val spend = s.history.filter { it.amount < 0 && it.type in setOf("gastolivre", "cartao") && !looksInternal(it.label) && it.ts.length >= 10 && it.ts.substring(0, 10) >= from }
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
    val spend = s.history.filter { it.amount < 0 && it.type in setOf("gastolivre", "cartao") && !looksInternal(it.label) && it.ts.length >= 10 }
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
