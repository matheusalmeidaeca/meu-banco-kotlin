package com.meubanco.patrimonio.domain

import java.time.LocalDate

/* ============================================================
   Resultado de uma ação (a interface mostra o aviso/sucesso)
   ============================================================ */
class Res(val ok: Boolean, val msg: String = "", val sub: String = "", val confirm: String? = null) {
    companion object {
        fun ok(msg: String = "", sub: String = "") = Res(true, msg, sub)
        fun err(msg: String) = Res(false, msg)
        fun ask(confirm: String) = Res(false, "", "", confirm)
    }
}

/* ============================================================
   Histórico e estatísticas mensais
   ============================================================ */
fun recordStat(h: HistoryEntry, stats: MutableMap<String, MonthStat>) {
    if (h.ts.isEmpty() || h.amount == 0.0) return
    val ym = h.ts.substring(0, 7)
    val a = h.amount
    val t = h.type
    val st = stats.getOrPut(ym) { MonthStat() }
    fun addCat(c: String, v: Double) {
        st.cats[c] = round2((st.cats[c] ?: 0.0) + v)
        st.spent = round2(st.spent + v)
    }
    when {
        t == "renda" && a > 0 -> st.income = round2(st.income + a)
        t == "despesa" && a < 0 -> {
            val cats = h.cats
            if (cats != null) cats.forEach { (c, v) -> addCat(c, v) } else addCat("Gastos fixos", -a)
        }
        t == "gastolivre" && a < 0 -> addCat(h.category?.takeIf { it.isNotEmpty() } ?: "Gasto livre", -a)
        t == "fatura" && a < 0 -> addCat("Cartão", -a)
        t == "pix" && a < 0 -> addCat("Pix enviado", -a)
        t == "imposto" && a < 0 -> addCat("Impostos", -a)
        (t == "aporte" || t == "transferencia" || t == "cdb" || t == "meta") && a < 0 -> st.invested = round2(st.invested - a)
    }
}

fun fixedCats(s: AppState, actual: Double, total: Double): Map<String, Double> {
    val out = linkedMapOf<String, Double>()
    s.expenses.forEach { e ->
        val c = e.category.ifEmpty { e.name.ifEmpty { "Gastos fixos" } }
        out[c] = (out[c] ?: 0.0) + e.amount * (if (total > 0) actual / total else 0.0)
    }
    out.keys.toList().forEach { c -> out[c] = round2(out[c]!!) }
    return out
}

fun addHistory(s: AppState, type: String, label: String, amount: Double = 0.0, cats: Map<String, Double>? = null, category: String? = null) {
    val entry = HistoryEntry(s.simDateISO, type, label, amount, cats, category)
    s.history.add(0, entry)
    recordStat(entry, s.monthlyStats)
    recordPortfolioFlow(entry, s)
    while (s.history.size > 500) s.history.removeAt(s.history.size - 1)
}

/* ============================================================
   Totais
   ============================================================ */
fun portfolioValue(s: AppState) = s.assets.sumOf { it.quantity * it.price }
fun goalsTotalSaved(s: AppState) = s.goals.sumOf { it.saved }
fun reservesTotalBalance(s: AppState) = s.reserves.sumOf { it.balance }
fun totalNetWorth(s: AppState) = portfolioValue(s) + s.leftover + reservesTotalBalance(s) + goalsTotalSaved(s) + s.cash

/* ============================================================
   Cartões
   ============================================================ */
fun allCards(s: AppState): List<Card> = listOf(s.card) + s.extraCards
fun activeCard(s: AppState): Card {
    val idx = s.activeCardIdx
    return if (idx == 0) s.card else (s.extraCards.getOrNull(idx - 1) ?: s.card)
}

fun cardFutureInstallmentsTotal(c: Card): Double =
    c.purchases.sumOf { it.installmentValue * Math.max(0, it.installmentsTotal - it.installmentsPaid) }

fun cardAvailable(c: Card): Double = Math.max(0.0, c.limit - c.invoiceCurrent - c.pendingAmount - cardFutureInstallmentsTotal(c))

fun processCardCycle(s: AppState, c: Card) {
    val d = isoToDate(s.simDateISO)
    val day = d.dayOfMonth
    val ym = ymKey(s.simDateISO)

    if (day == c.closingDay && c.lastClosedYM != ym) {
        c.lastClosedYM = ym
        if (c.invoiceCurrent > 0) {
            c.pendingAmount = round2(c.pendingAmount + c.invoiceCurrent)
            var dueYear = d.year
            var dueMonth = d.monthIdx()
            if (c.dueDay <= c.closingDay) {
                dueMonth += 1
                if (dueMonth > 11) { dueMonth = 0; dueYear += 1 }
            }
            c.pendingDueYM = "%04d-%02d".format(dueYear, dueMonth + 1)
        }
        c.invoiceCurrent = 0.0
        c.purchases.forEach { p ->
            if (p.installmentsPaid < p.installmentsTotal) {
                p.installmentsPaid++
                val isLast = p.installmentsPaid == p.installmentsTotal
                val value = if (isLast) round2(p.totalAmount - p.installmentValue * (p.installmentsTotal - 1)) else p.installmentValue
                c.invoiceCurrent = round2(c.invoiceCurrent + value)
            }
        }
        c.purchases.removeAll { it.installmentsPaid >= it.installmentsTotal }
    }

    if (c.pendingAmount > 0 && c.pendingDueYM == ym && day == c.dueDay) {
        val due = c.pendingAmount
        val actual = round2(Math.min(due, Math.max(0.0, s.cash)))
        s.cash = round2(s.cash - actual)
        c.pendingAmount = round2(due - actual)
        if (actual > 0) addHistory(s, "fatura", "Fatura do " + c.name.ifEmpty { "cartão" }, -actual)
        if (actual < due - 0.01) {
            addHistory(s, "aviso", "Caixa insuficiente: fatura do cartão paga parcialmente (${formatBRL(actual)} de ${formatBRL(due)})", 0.0)
        }
    }
}

/* ============================================================
   Despesas fixas e renda
   ============================================================ */
fun maybeProcessExpenses(s: AppState) {
    val d = isoToDate(s.simDateISO)
    val year = d.year
    val monthIdx = d.monthIdx()
    val payDay = getNthBusinessDay(year, monthIdx, 5)
    val expensesDayISO = addDaysISO(payDay.toString(), 1)
    if (s.simDateISO != expensesDayISO) return

    val ym = ymKey(s.simDateISO)
    if (s.lastExpensesPaidYM == ym) return
    s.lastExpensesPaidYM = ym

    val totalExpenses = s.expenses.sumOf { it.amount }
    val actualExpenses = round2(Math.min(totalExpenses, Math.max(0.0, s.cash)))
    if (actualExpenses > 0) {
        s.cash -= actualExpenses
        s.totalExpensesPaid += actualExpenses
        val monthLabel = MONTH_NAMES[monthIdx] + " de " + year
        addHistory(s, "despesa", "Gastos fixos de $monthLabel (1 dia após o salário)", -actualExpenses, cats = fixedCats(s, actualExpenses, totalExpenses))
    }
    if (actualExpenses < totalExpenses - 0.01) {
        addHistory(s, "aviso", "Caixa insuficiente: gastos fixos pagos parcialmente (${formatBRL(actualExpenses)} de ${formatBRL(totalExpenses)})", 0.0)
    }
    s.cash = round2(s.cash)
}

fun maybeProcessIncomePayment(s: AppState) {
    when (s.income.incomeType) {
        "autonomo" -> {}
        "renda" -> maybeProcessBeneficioPrincipal(s)
        else -> maybeProcessSalarioPrincipal(s)
    }
    maybeProcessSecondaryIncome(s)
}

fun maybeProcessBeneficioPrincipal(s: AppState) {
    val d = isoToDate(s.simDateISO)
    if (d.dayOfMonth != s.income.beneficioPayDay) return
    val ym = ymKey(s.simDateISO)
    if (s.lastIncomePaidYM == ym) return
    s.lastIncomePaidYM = ym
    val amount = round2(Math.max(0.0, s.income.beneficioAmount))
    if (amount > 0) {
        s.cash = round2(s.cash + amount)
        s.income.totalReceived += amount
        addHistory(s, "renda", s.income.beneficioLabel.ifEmpty { "Benefício" } + " recebido", amount)
    }
}

fun maybeProcessSecondaryIncome(s: AppState) {
    val sec = s.secondaryIncome
    if (!sec.enabled) return
    val d = isoToDate(s.simDateISO)
    if (d.dayOfMonth != sec.payDay) return
    val ym = ymKey(s.simDateISO)
    if (s.lastSecondaryIncomePaidYM == ym) return
    s.lastSecondaryIncomePaidYM = ym
    val amount = round2(Math.max(0.0, sec.amount))
    if (amount > 0) {
        s.cash = round2(s.cash + amount)
        s.income.totalReceived += amount
        addHistory(s, "renda", sec.label.ifEmpty { "Renda extra" } + " recebida", amount)
    }
}

/** FGTS retroativo a partir da data de admissão — sempre recalculado do zero. */
fun recomputeFgtsBackfill(s: AppState) {
    s.fgts.balance = round2(s.fgts.balance - s.fgts.lastBackfillAmount)
    val hireISO = s.income.hireDateISO
    if (hireISO.isNullOrEmpty() || s.income.incomeType != "empregado") {
        s.fgts.lastBackfillAmount = 0.0
        return
    }
    val hire = isoToDate(hireISO)
    val today = isoToDate(s.simDateISO)
    var months = (today.year - hire.year) * 12 + (today.monthIdx() - hire.monthIdx())
    if (today.dayOfMonth < hire.dayOfMonth) months -= 1
    months = Math.max(0, months)
    val amount = round2(months * Math.max(0.0, s.income.baseSalary) * 0.08)
    s.fgts.lastBackfillAmount = amount
    s.fgts.balance = round2(s.fgts.balance + amount)
}

fun maybeProcessSalarioPrincipal(s: AppState) {
    val d = isoToDate(s.simDateISO)
    val year = d.year
    val monthIdx = d.monthIdx()
    val payDay = getNthBusinessDay(year, monthIdx, 5)
    if (d != payDay) return

    var workYear = year
    var workMonth = monthIdx - 1
    if (workMonth < 0) { workMonth = 11; workYear -= 1 }
    val workYM = "%04d-%02d".format(workYear, workMonth + 1)
    if (s.lastIncomePaidYM == workYM) return
    s.lastIncomePaidYM = workYM

    if (s.income.feriasEnabled && workMonth == s.income.feriasMonth) {
        addHistory(s, "aviso", "Sem salário este mês — ${MONTH_NAMES[workMonth]} foi mês de férias, já coberto no pagamento anterior", 0.0)
        return
    }

    val inc = computeMonthlyIncome(s, workYear, workMonth)
    val workLabel = MONTH_NAMES[workMonth] + " de " + workYear
    if (inc.net != 0.0) {
        s.cash += inc.net
        s.income.totalReceived += inc.net
        val parts = mutableListOf<String>()
        if (inc.holidaysCount > 0) parts.add("${inc.holidaysCount} feriado(s)")
        addHistory(s, "renda", "Salário de $workLabel, recebido hoje" + (if (parts.isNotEmpty()) " — " + parts.joinToString(", ") else ""), inc.net)
    }
    if (inc.sundaysValue > 0) {
        s.cash = round2(s.cash + inc.sundaysValue)
        s.income.totalReceived += inc.sundaysValue
        addHistory(s, "renda", "Recibo de domingo trabalhado (${inc.sundaysCount} dia(s)) — $workLabel", inc.sundaysValue)
    }
    if (inc.net != 0.0 || inc.sundaysValue > 0) {
        val fgtsBase = round2(inc.salaryWithExtras + inc.holidaysValue + inc.sundaysValue)
        s.fgts.balance = round2(s.fgts.balance + fgtsBase * 0.08)
    }
}

fun maybeProcessThirteenth(s: AppState) {
    if (s.income.incomeType != "empregado" || !s.income.thirteenthEnabled) return
    val d = isoToDate(s.simDateISO)
    val year = d.year
    val monthIdx = d.monthIdx()
    val day = d.dayOfMonth

    if (monthIdx == 10 && day == 30 && s.lastThirteenthFirstYear != year) {
        s.lastThirteenthFirstYear = year
        val b = compute13thBreakdown(s)
        s.cash = round2(s.cash + b.firstInstallment)
        addHistory(s, "renda", "13º salário — 1ª parcela ($year)", b.firstInstallment)
    }
    if (monthIdx == 11 && day == 20 && s.lastThirteenthSecondYear != year) {
        s.lastThirteenthSecondYear = year
        val b = compute13thBreakdown(s)
        s.cash = round2(s.cash + b.secondInstallmentNet)
        s.fgts.balance = round2(s.fgts.balance + b.gross * 0.08)
        addHistory(s, "renda", "13º salário — 2ª parcela (INSS ${formatBRL(b.inss)}, IR ${formatBRL(b.ir)})", b.secondInstallmentNet)
    }
}

fun maybeProcessFerias(s: AppState) {
    val fm = s.income.feriasMonth
    if (s.income.incomeType != "empregado" || !s.income.feriasEnabled || fm == null) return
    val d = isoToDate(s.simDateISO)
    val year = d.year
    val monthIdx = d.monthIdx()
    if (monthIdx != fm) return
    val payDay = getNthBusinessDay(year, monthIdx, 5)
    if (d != payDay) return
    if (s.lastFeriasYear == year) return
    s.lastFeriasYear = year

    val b = computeFeriasBreakdown(s)
    s.cash = round2(s.cash + b.net)
    s.fgts.balance = round2(s.fgts.balance + s.income.baseSalary * 0.08)
    addHistory(s, "renda", "Férias + 1/3 constitucional (${MONTH_NAMES[monthIdx]}/$year)", b.net)
}

/* ============================================================
   Eventos mensais (reservas, metas, snapshot de patrimônio)
   ============================================================ */
fun maybeProcessMonthlyEvents(s: AppState) {
    val d = isoToDate(s.simDateISO)
    val year = d.year
    val monthIdx = d.monthIdx()
    val payDay = getNthBusinessDay(year, monthIdx, 5)
    val eventsDayISO = addDaysISO(payDay.toString(), 2)
    if (s.simDateISO != eventsDayISO) return

    val ym = ymKey(s.simDateISO)
    if (s.lastProcessedYM == ym) return
    s.lastProcessedYM = ym
    processMonthlyEvents(s, year, monthIdx)
}

fun processMonthlyEvents(s: AppState, year: Int, monthIdx: Int) {
    val ym = "%04d-%02d".format(year, monthIdx + 1)

    s.reserves.forEach { r ->
        val desired = r.monthlyContribution
        val actual = round2(Math.min(desired, Math.max(0.0, s.cash)))
        s.cash -= actual
        if (actual > 0 && r.balance <= 0 && r.startDateISO == null) r.startDateISO = s.simDateISO
        r.balance += actual
        r.totalContributed += actual
        if (actual > 0) addHistory(s, "cdb", "Aporte na reserva \"${r.name}\"", -actual)
        if (actual < desired - 0.01) {
            addHistory(s, "aviso", "Caixa insuficiente: aporte da reserva \"${r.name}\" reduzido de ${formatBRL(desired)} para ${formatBRL(actual)}", 0.0)
        }
    }

    s.goals.forEach { g ->
        if (g.completed) return@forEach
        if (g.monthly > 0) {
            val actual = round2(Math.min(g.monthly, Math.max(0.0, s.cash)))
            s.cash -= actual
            g.saved += actual
            if (actual > 0) addHistory(s, "meta", "Aporte na meta \"${g.name}\"", -actual)
            if (actual < g.monthly - 0.01) {
                addHistory(s, "aviso", "Caixa insuficiente para o aporte completo da meta \"${g.name}\"", 0.0)
            }
        }
        if (g.saved >= g.target) {
            g.completed = true
            g.completedOn = s.simDateISO
            addHistory(s, "conquista", "Meta concluída: ${g.name}", g.target)
        }
    }

    s.cash = round2(s.cash)

    s.netWorthHistory.add(
        NwPoint(
            ym, round2(totalNetWorth(s)),
            round2(portfolioValue(s) + s.leftover + reservesTotalBalance(s) + goalsTotalSaved(s)),
        ),
    )
    if (s.netWorthHistory.size > 240) s.netWorthHistory.removeAt(0)
}

fun maybeProcessCarteiraMonthlyEvents(s: AppState) {
    val d = isoToDate(s.simDateISO)
    if (d.dayOfMonth != s.contributionDay) return
    val ym = ymKey(s.simDateISO)
    if (s.lastCarteiraProcessedYM == ym) return
    s.lastCarteiraProcessedYM = ym
    processCarteiraMonthlyEvents(s)
}

/* ============================================================
   Motor de simulação — avança dia a dia
   ============================================================ */
fun simulateOneDay(s: AppState) {
    // reservas: juros compostos diários na taxa do CDI
    s.reserves.forEach { r ->
        if (r.balance > 0) {
            val dailyRate = Math.pow(1 + s.reserveConfig.annualRatePct / 100, 1.0 / 365) - 1
            val interest = r.balance * dailyRate
            r.balance += interest
            r.totalInterest += interest
        }
    }
    // metas: rendimento diário
    s.goals.forEach { g ->
        if (g.completed) return@forEach
        val dailyRate = Math.pow(1 + g.rate / 100, 1.0 / 365) - 1
        g.saved += g.saved * dailyRate
    }

    s.simDateISO = addDaysISO(s.simDateISO, 1)

    maybeProcessIncomePayment(s)
    maybeProcessThirteenth(s)
    maybeProcessFerias(s)
    maybeProcessExpenses(s)
    maybeProcessMonthlyEvents(s)
    maybeProcessCarteiraMonthlyEvents(s)
    allCards(s).forEach { processCardCycle(s, it) }
    processSubscriptions(s)
}

/** Avança a conta até a data de hoje do aparelho. Retorna quantos dias foram processados. */
fun syncToRealDate(s: AppState, todayISO: String = AppClock.todayISO(), maxDays: Int = Int.MAX_VALUE): Int {
    var n = 0
    while (s.simDateISO < todayISO && n < maxDays) { simulateOneDay(s); n++ }
    return n
}
