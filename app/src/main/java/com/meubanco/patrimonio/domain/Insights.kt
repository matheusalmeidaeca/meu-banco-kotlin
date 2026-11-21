package com.meubanco.patrimonio.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class Insight(val ico: String, val t: String, val d: String, val goLabel: String? = null, val goTab: String? = null)

private fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b).toInt()

/** Dicas do assistente (só abre quando a pessoa toca). */
fun computeInsights(s: AppState): List<Insight> {
    val out = mutableListOf<Insight>()
    val sim = isoToDate(s.simDateISO)
    if (s.card.enabled) {
        allCards(s).forEach { cd ->
            val due = cd.pendingDueYM
            if (cd.pendingAmount > 0 && due != null) {
                val dueDate = LocalDate.of(due.substring(0, 4).toInt(), due.substring(5, 7).toInt(), Math.min(cd.dueDay, 28))
                val days = daysBetween(sim, dueDate)
                if (days <= 5) out.add(
                    Insight("💳", "Fatura chegando",
                        "A fatura do ${cd.name.ifEmpty { "cartão" }} (${formatBRL(cd.pendingAmount)}) " + (if (days <= 0) "vence hoje ou já venceu." else "vence em $days dia(s)."),
                        "Ver", "conta"),
                )
            }
        }
    }
    val fixed = s.expenses.sumOf { it.amount }
    if (fixed > 0 && s.cash < fixed) out.add(
        Insight("⚠️", "Saldo abaixo dos gastos fixos", "Seu saldo (${formatBRL(s.cash)}) é menor que os gastos fixos do mês (${formatBRL(fixed)}).", "Gastos", "gastos"),
    )
    val limit = s.freeSpendMonthlyLimit
    if (limit > 0) {
        val spent = freeSpendThisMonth(s)
        if (spent / limit * 100 >= s.settings.notifyFreeSpendPct) out.add(
            Insight("🎉", "Gasto livre no limite", "Você já usou ${Math.round(spent / limit * 100)}% do limite de gasto livre deste mês.", "Gastos", "gastos"),
        )
    }
    if (s.income.incomeType == "empregado") {
        val todayISO = AppClock.todayISO()
        val today = AppClock.today()
        if (today.jsDay() == 0) {
            if (!s.sundaysWorked.contains(todayISO)) out.add(Insight("📅", "Hoje é domingo", "Se você trabalhou, marque o domingo na aba Ponto.", "Ponto", "ponto"))
        } else if (s.timeClock.none { it.date == todayISO } && java.time.LocalTime.now().hour >= 6) {
            out.add(Insight("🕐", "Ponto de hoje", "Você ainda não registrou o ponto de hoje.", "Bater ponto", "ponto"))
        }
    }
    pendingDarfs(s).forEach { r ->
        val days = daysBetween(sim, isoToDate(r.dueISO))
        out.add(
            Insight("🏛", "DARF a pagar",
                "IR de ${formatMonthLabel(r.ym)}: ${formatBRL(r.tax)}" + (if (days < 0) " — vencido há ${-days} dia(s)." else " — vence em $days dia(s)."),
                "Ver", "carteira"),
        )
    }
    if (s.assets.any { it.quantity > 0 } && (s.investorProfile == null || profileExpired(s))) out.add(
        Insight("🧭", "Perfil de investidor",
            if (s.investorProfile == null) "Faça o teste de perfil pra receber alertas de adequação nas compras." else "Seu perfil de investidor venceu — refaça o teste.",
            "Fazer teste", "carteira"),
    )
    val cheapest = s.assets.fold(Double.POSITIVE_INFINITY) { m, a -> if (a.price > 0 && a.price < m) a.price else m }
    if (s.leftover >= cheapest) out.add(
        Insight("📈", "Dinheiro parado", "${formatBRL(s.leftover)} na conta de investimentos já dá pra comprar cotas.", "Investir", "carteira"),
    )
    val owned = s.assets.filter { it.quantity > 0 }
    if (owned.isNotEmpty()) {
        val div = owned.sumOf { it.quantity * it.price * (it.yieldPct / 100) }
        if (div > 0) out.add(Insight("💰", "Dividendos estimados", "Seus ativos devem render cerca de ${formatBRL(div)} por mês."))
    }
    s.reserves.forEach { r ->
        if (r.targetValue > 0) {
            val p = r.balance / r.targetValue * 100
            if (p >= s.settings.notifyGoalPct && p < 100) out.add(Insight("🏦", "Quase lá!", "A caixinha \"${r.name}\" está em ${Math.round(p)}% da meta."))
        }
    }
    if (!s.settings.autoBackupEnabled) {
        val days = s.lastBackupAt?.let { daysSinceBackup(it) }
        if (days == null || days >= s.settings.notifyBackupDays) out.add(
            Insight("💾", "Faça um backup",
                if (days == null) "Você ainda não exportou nenhum backup." else "Faz $days dias desde o último backup.",
                "Configurações", "config"),
        )
    }
    out.addAll(budgetInsights(s)); out.addAll(fiInsights(s)); out.addAll(debtInsights(s))
    return out
}

fun daysSinceBackup(iso: String): Int? = try {
    val t = java.time.Instant.parse(iso).toEpochMilli()
    Math.floor((AppClock.nowMs() - t) / 86400000.0).toInt()
} catch (e: Exception) { null }

/* ---------- Celebrações ---------- */
class Celebration(val emoji: String, val title: String, val text: String)

val NETWORTH_MILESTONES = listOf(1000, 5000, 10000, 25000, 50000, 100000, 250000, 500000, 1000000)

/** Retorna as comemorações novas (e marca como já mostradas). */
fun checkCelebrations(s: AppState): List<Celebration> {
    if (s.onboardingCompleted != true || !s.settings.celebrationsEnabled) return emptyList()
    val out = mutableListOf<Celebration>()
    fun queue(id: String, emoji: String, title: String, text: String) {
        if (s.celebrated[id] == true) return
        s.celebrated[id] = true
        out.add(Celebration(emoji, title, text))
    }
    val now = AppClock.today()
    val bd = s.profile.birthDateISO
    if (!bd.isNullOrEmpty()) {
        val b = isoToDate(bd)
        if (b.monthValue == now.monthValue && b.dayOfMonth == now.dayOfMonth) {
            val nm = if (s.settings.displayName.isNotEmpty()) ", " + s.settings.displayName else ""
            val idade = ageFromISO(bd)
            queue("birthday-${now.year}", "🎂", "Parabéns$nm!",
                if (idade != null) "Hoje você completa $idade anos. Que o ano novo venha com saúde e boas conquistas financeiras!"
                else "Feliz aniversário! Que o ano novo venha com saúde e boas conquistas financeiras!")
        }
    }
    s.goals.forEach { g -> if (g.completed) queue("goal-${g.id}", "🏆", "Meta concluída!", "Você completou a meta \"${g.name}\". Que orgulho!") }
    s.reserves.forEach { r ->
        if (r.targetValue > 0 && r.balance >= r.targetValue)
            queue("reserve-${r.id}-${fmtNum(r.targetValue)}", "🎯", "Meta batida!", "A caixinha \"${r.name}\" chegou a ${formatBRL(r.targetValue)}.")
    }
    val net = totalNetWorth(s)
    val reached = NETWORTH_MILESTONES.filter { net >= it }
    if (s.celebrated["nwBaseline"] != true) {
        s.celebrated["nwBaseline"] = true
        reached.forEach { s.celebrated["nw-$it"] = true }
    } else {
        val fresh = reached.filter { s.celebrated["nw-$it"] != true }
        if (fresh.isNotEmpty()) {
            val top = fresh.last()
            fresh.forEach { s.celebrated["nw-$it"] = true }
            queue("nw-show-$top", "🚀", "Novo marco!", "Seu patrimônio passou de ${formatBRL(top.toDouble())}. Continue assim!")
        }
    }
    return out
}

private fun fmtNum(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
