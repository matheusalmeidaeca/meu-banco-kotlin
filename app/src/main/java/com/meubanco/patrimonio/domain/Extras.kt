package com.meubanco.patrimonio.domain

import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/* ============ PIN forte (PBKDF2) — ainda aceita o hash simples do app em HTML ============ */

private const val PIN_ITER = 120_000

fun hashPin(pin: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val h = pbkdf2(pin, salt)
    val b64 = Base64.getEncoder()
    return "v2:" + b64.encodeToString(salt) + ":" + b64.encodeToString(h)
}

fun verifyPin(pin: String, stored: String?): Boolean {
    if (stored == null) return false
    if (!stored.startsWith("v2:")) return simpleHash(pin) == stored
    val parts = stored.split(":")
    if (parts.size != 3) return false
    return try {
        val d = Base64.getDecoder()
        val salt = d.decode(parts[1]); val expected = d.decode(parts[2])
        java.security.MessageDigest.isEqual(pbkdf2(pin, salt), expected)
    } catch (e: Exception) { false }
}

private fun pbkdf2(pin: String, salt: ByteArray): ByteArray =
    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, PIN_ITER, 256)).encoded

/* ============ Assinaturas / gastos recorrentes ============ */

fun processSubscriptions(s: AppState) {
    if (s.subscriptions.isEmpty()) return
    val d = isoToDate(s.simDateISO)
    val ym = ymKey(s.simDateISO)
    for (sub in s.subscriptions) {
        if (!sub.active || sub.lastChargedYM == ym) continue
        val day = Math.min(clampI(sub.day, 1, 28), d.lengthOfMonth())
        if (d.dayOfMonth < day) continue
        val actual = round2(Math.min(sub.amount, Math.max(0.0, s.cash)))
        sub.lastChargedYM = ym
        if (actual <= 0) {
            addHistory(s, "aviso", "Assinatura ${sub.name} não foi paga: sem saldo", 0.0)
            continue
        }
        s.cash = round2(s.cash - actual)
        addHistory(s, "gastolivre", "Assinatura — ${sub.name}", -actual, category = sub.category.ifBlank { "Outro" })
        if (actual < sub.amount - 0.01) addHistory(s, "aviso", "Assinatura ${sub.name}: pago só ${formatBRL(actual)} de ${formatBRL(sub.amount)}", 0.0)
    }
}

fun addSubscription(s: AppState, name: String, amount: Double, day: Int, category: String) {
    s.subscriptions.add(Subscription(s.nextSubscriptionId++, name.trim(), amount, clampI(day, 1, 28), category))
}

fun subscriptionsMonthlyTotal(s: AppState) = s.subscriptions.filter { it.active }.sumOf { it.amount }

/* ============ Orçamento por categoria ============ */

class BudgetLine(val category: String, val spent: Double, val limit: Double) {
    val frac get() = if (limit > 0) spent / limit else 0.0
}

fun budgetCategories(s: AppState): List<String> {
    val set = linkedSetOf<String>()
    EXPENSE_CATEGORIES.forEach { set.add(it) }
    s.freeSpendCategories.forEach { set.add(it) }
    s.monthlyStats[ymKey(s.simDateISO)]?.cats?.keys?.forEach { set.add(it) }
    s.budgets.keys.forEach { set.add(it) }
    return set.toList()
}

fun budgetLines(s: AppState): List<BudgetLine> {
    val cats = s.monthlyStats[ymKey(s.simDateISO)]?.cats ?: emptyMap()
    return s.budgets.filter { it.value > 0 }.map { (c, lim) -> BudgetLine(c, cats[c] ?: 0.0, lim) }.sortedByDescending { it.frac }
}

fun budgetInsights(s: AppState): List<Insight> =
    budgetLines(s).filter { it.frac >= s.settings.notifyFreeSpendPct / 100.0 }.map {
        if (it.frac >= 1.0) Insight("🚨", "Orçamento estourado: ${it.category}", "Gastou ${formatBRL(it.spent)} de ${formatBRL(it.limit)} neste mês.")
        else Insight("⚠️", "Orçamento quase no limite: ${it.category}", "Já usou ${formatBRL(it.spent)} de ${formatBRL(it.limit)} (${Math.round(it.frac * 100)}%).")
    }

/** Categorias que acabaram de passar do limite e ainda não foram avisadas (para notificação). */
fun newBudgetAlerts(s: AppState): List<BudgetLine> {
    val ym = ymKey(s.simDateISO)
    val out = mutableListOf<BudgetLine>()
    for (l in budgetLines(s)) {
        if (l.frac < s.settings.notifyFreeSpendPct / 100.0) continue
        val key = "$ym|${l.category}"
        if (s.budgetAlerted[key] == true) continue
        s.budgetAlerted[key] = true
        out.add(l)
    }
    return out
}

/* ============ Projeção (roda o próprio motor numa cópia) ============ */

fun cloneState(s: AppState): AppState = decodeState(encodeState(s))

class ProjEvent(val dateISO: String, val type: String, val label: String, val amount: Double)

class Projection(
    val days: Int, val endISO: String, val startCash: Double, val endCash: Double,
    val minCash: Double, val minISO: String, val events: List<ProjEvent>,
)

/** Simula `days` dias à frente sem alterar o estado real. */
fun project(s: AppState, days: Int): Projection {
    val c = cloneState(s)
    val before = c.history.size
    val firstTs = c.history.firstOrNull()
    var minCash = c.cash; var minISO = c.simDateISO
    val seen = c.history.size
    repeat(days) {
        simulateOneDay(c)
        if (c.cash < minCash) { minCash = c.cash; minISO = c.simDateISO }
    }
    // entradas novas = as que estão antes das antigas (a lista é do mais novo ao mais velho, com teto de 500)
    val newCount = Math.max(0, c.history.size - seen).let { if (c.history.size >= 500) c.history.indexOfFirst { it === firstTs }.let { i -> if (i < 0) 500 else i } else it }
    val evs = c.history.take(newCount).filter { it.type != "aviso" && it.type != "conquista" && it.amount != 0.0 }
        .map { ProjEvent(it.ts, it.type, it.label, it.amount) }.sortedBy { it.dateISO }
    return Projection(days, c.simDateISO, s.cash, c.cash, minCash, minISO, evs)
}

fun daysUntilMonthEnd(s: AppState): Int {
    val d = isoToDate(s.simDateISO)
    return d.lengthOfMonth() - d.dayOfMonth
}

class CalendarItem(val dateISO: String, val label: String, val amount: Double, val kind: String)

fun upcomingCalendar(s: AppState, days: Int = 45): List<CalendarItem> {
    val out = mutableListOf<CalendarItem>()
    project(s, days).events.forEach { out.add(CalendarItem(it.dateISO, it.label, it.amount, it.type)) }
    pendingDarfs(s).forEach { out.add(CalendarItem(it.dueISO, "DARF ${formatMonthLabel(it.ym)}", -it.tax, "imposto")) }
    for (c in allCards(s)) {
        if (c.pendingAmount > 0 && c.pendingDueYM != null)
            out.add(CalendarItem(c.pendingDueYM + "-" + c.dueDay.toString().padStart(2, '0'), "Fatura ${c.name}", -c.pendingAmount, "fatura"))
    }
    return out.sortedBy { it.dateISO }
}

/* ============ Independência financeira ============ */

class FiStatus(
    val monthlyIncome: Double, val target: Double, val pct: Double,
    val monthsToGoal: Int?, val targetFromExpenses: Boolean,
)

fun monthlyDividendIncome(s: AppState) = s.assets.sumOf { it.quantity * it.price * (it.yieldPct / 100) }

fun fixedExpensesTotal(s: AppState) = s.expenses.sumOf { it.amount }

fun fiStatus(s: AppState): FiStatus {
    val income = monthlyDividendIncome(s)
    val fromExp = s.fiTargetMonthly <= 0
    val target = if (fromExp) fixedExpensesTotal(s) + subscriptionsMonthlyTotal(s) else s.fiTargetMonthly
    val pct = if (target > 0) income / target else 0.0
    var months: Int? = null
    if (target > 0) {
        if (income >= target) months = 0
        else {
            val invested = portfolioValue(s)
            val priced = s.assets.filter { it.price > 0 && it.quantity > 0 }
            val avgYield = if (invested > 0) income / invested else s.assets.map { it.yieldPct / 100 }.average().takeIf { !it.isNaN() } ?: 0.0
            val contrib = if (s.recurringInvestEnabled) s.monthlyContribution else 0.0
            if (avgYield > 0 && (invested > 0 || contrib > 0)) {
                var v = invested + s.leftover
                for (m in 1..600) {
                    v += v * avgYield + contrib
                    if (v * avgYield >= target) { months = m; break }
                }
            }
        }
    }
    return FiStatus(round2(income), round2(target), pct, months, fromExp)
}

fun fiInsights(s: AppState): List<Insight> {
    val f = fiStatus(s)
    if (f.target <= 0 || monthlyDividendIncome(s) <= 0) return emptyList()
    return listOf(Insight("🏝", "Independência financeira: ${Math.round(f.pct * 100)}%",
        "Seus dividendos (${formatBRL(f.monthlyIncome)}/mês) cobrem ${Math.round(Math.min(f.pct, 9.99) * 100)}% de ${formatBRL(f.target)}.", "Planejamento", "plano"))
}

/* ============ Dividendos mês a mês ============ */

fun dividendsByMonth(s: AppState): List<Pair<String, Double>> {
    val m = linkedMapOf<String, Double>()
    s.history.filter { it.type == "dividendo" }.forEach { m[ymKey(it.ts)] = (m[ymKey(it.ts)] ?: 0.0) + it.amount }
    return m.toList().sortedBy { it.first }
}

fun dividendsByTicker(s: AppState, months: Int = 12): List<Pair<String, Double>> {
    val yms = s.dividendBreakdown.keys.sorted().takeLast(months)
    val out = linkedMapOf<String, Double>()
    yms.forEach { ym -> s.dividendBreakdown[ym]?.forEach { (t, v) -> out[t] = (out[t] ?: 0.0) + v } }
    return out.toList().sortedByDescending { it.second }
}

/* ============ Importar extrato (CSV / OFX) ============ */

class StmtItem(val dateISO: String, val desc: String, val amount: Double)

private val DATE_BR = Regex("""(\d{2})/(\d{2})/(\d{4})""")
private val DATE_ISO = Regex("""(\d{4})-(\d{2})-(\d{2})""")

private fun parseStmtDate(raw: String): String? {
    DATE_BR.find(raw)?.let { return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }
    DATE_ISO.find(raw)?.let { return it.value }
    Regex("""^(\d{4})(\d{2})(\d{2})""").find(raw.trim())?.let { return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
    return null
}

private fun parseStmtAmount(raw: String): Double? {
    var t = raw.trim().replace("R$", "").replace(" ", "").replace(" ", "")
    if (t.isEmpty()) return null
    val neg = t.startsWith("-") || t.startsWith("(") || t.endsWith("-") || t.uppercase().endsWith("D")
    t = t.trim('-', '(', ')', '+')
    if (t.uppercase().endsWith("D") || t.uppercase().endsWith("C")) t = t.dropLast(1)
    val v = parseBrNumber(t) ?: return null
    return if (neg) -Math.abs(v) else v
}

fun parseStatement(text: String): List<StmtItem> {
    val out = mutableListOf<StmtItem>()
    if (text.contains("<STMTTRN>", ignoreCase = true)) {
        val blocks = Regex("<STMTTRN>(.*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(text)
        for (b in blocks) {
            fun tag(n: String) = Regex("<$n>([^<\\r\\n]*)", RegexOption.IGNORE_CASE).find(b.groupValues[1])?.groupValues?.get(1)?.trim()
            val d = parseStmtDate(tag("DTPOSTED") ?: continue) ?: continue
            val a = (tag("TRNAMT") ?: continue).replace(",", ".").toDoubleOrNull() ?: continue
            out.add(StmtItem(d, (tag("MEMO") ?: tag("NAME") ?: "Lançamento").take(60), round2(a)))
        }
        return out
    }
    val lines = text.lines().filter { it.isNotBlank() }
    for (line in lines) {
        val delim = if (line.count { it == ';' } >= line.count { it == '\t' } && line.contains(';')) ';' else if (line.contains('\t')) '\t' else ','
        val cols = line.split(delim).map { it.trim().trim('"') }
        val di = cols.indexOfFirst { parseStmtDate(it) != null && (DATE_BR.containsMatchIn(it) || DATE_ISO.containsMatchIn(it)) }
        if (di < 0) continue
        val date = parseStmtDate(cols[di]) ?: continue
        // valor: último campo numérico que não seja a data
        var ai = -1; var amount = 0.0
        for (i in cols.indices.reversed()) {
            if (i == di) continue
            val v = parseStmtAmount(cols[i]) ?: continue
            if (cols[i].any { it.isDigit() } && Regex("""^[-+()R$\s\d.,DC]+$""", RegexOption.IGNORE_CASE).matches(cols[i])) { ai = i; amount = v; break }
        }
        if (ai < 0) continue
        val desc = cols.filterIndexed { i, c -> i != di && i != ai && c.isNotBlank() }.joinToString(" ").take(60).ifBlank { "Lançamento" }
        out.add(StmtItem(date, desc, round2(amount)))
    }
    return out
}

fun stmtKey(i: StmtItem) = "${i.dateISO}|${i.desc}|${i.amount}"

fun guessCategory(desc: String): String {
    val d = desc.lowercase()
    return when {
        listOf("mercado", "super", "padaria", "ifood", "restaurante", "lanche", "açougue", "acougue").any { d.contains(it) } -> "Alimentação"
        listOf("uber", "99", "posto", "combust", "ônibus", "onibus", "metro", "metrô").any { d.contains(it) } -> "Transporte"
        listOf("farm", "drog", "médic", "medic", "clínica", "clinica", "hospital").any { d.contains(it) } -> "Saúde"
        listOf("netflix", "spotify", "cinema", "game", "lazer", "bar ").any { d.contains(it) } -> "Lazer"
        listOf("energia", "luz", "água", "agua", "internet", "claro", "vivo", "tim ", "oi ").any { d.contains(it) } -> "Contas"
        listOf("aluguel", "condom").any { d.contains(it) } -> "Moradia"
        listOf("escola", "curso", "faculdade").any { d.contains(it) } -> "Educação"
        else -> "Outros"
    }
}

class ImportResult(val added: Int, val skipped: Int)

fun applyStatement(s: AppState, items: List<StmtItem>): ImportResult {
    var added = 0; var skipped = 0
    val known = s.importedKeys.toHashSet()
    for (i in items.sortedBy { it.dateISO }) {
        val k = stmtKey(i)
        if (i.amount == 0.0 || !known.add(k)) { skipped++; continue }
        s.cash = round2(s.cash + i.amount)
        val e = if (i.amount > 0) HistoryEntry(i.dateISO, "renda", "Extrato importado: ${i.desc}", i.amount)
        else HistoryEntry(i.dateISO, "gastolivre", "Extrato importado: ${i.desc}", i.amount, category = guessCategory(i.desc))
        s.history.add(e)
        recordStat(e, s.monthlyStats)
        s.importedKeys.add(k)
        added++
    }
    s.history.sortByDescending { it.ts }
    while (s.history.size > 500) s.history.removeAt(s.history.size - 1)
    while (s.importedKeys.size > 3000) s.importedKeys.removeAt(0)
    return ImportResult(added, skipped)
}
