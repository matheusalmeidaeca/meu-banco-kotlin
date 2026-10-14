package com.meubanco.nativo

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ============================================================
// Motor de simulação: porta do JavaScript do app HTML.
// Tudo age sobre o mesmo JSON "state" (JSONObject) do backup.
// ============================================================

var JSONObject.cash: Double
    get() = optDouble("cash", 0.0)
    set(v) {
        put("cash", v)
    }

var JSONObject.leftover: Double
    get() = optDouble("leftover", 0.0)
    set(v) {
        put("leftover", v)
    }

/** Sub-objeto do estado; cria se não existir. */
fun JSONObject.sub(key: String): JSONObject = optJSONObject(key) ?: JSONObject().also { put(key, it) }

// ---------------- datas ----------------

fun isoDate(iso: String): LocalDate = LocalDate.parse(iso)
fun plusDaysISO(iso: String, d: Long): String = LocalDate.parse(iso).plusDays(d).toString()
fun ymOf(iso: String): String = iso.take(7)
fun daysBetween(a: String, b: String): Long = ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b))

fun monthShort(ym: String): String {
    val m = ym.substring(5, 7).toIntOrNull() ?: return ym
    val n = MONTH_NAMES.getOrElse(m - 1) { "" }.take(3)
    return n.replaceFirstChar { it.uppercase() } + "/" + ym.substring(2, 4)
}

fun easterDate(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val dd = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - dd - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}

fun nationalHolidays(year: Int): List<LocalDate> {
    val list = mutableListOf(
        LocalDate.of(year, 1, 1), LocalDate.of(year, 4, 21), LocalDate.of(year, 5, 1),
        LocalDate.of(year, 9, 7), LocalDate.of(year, 10, 12), LocalDate.of(year, 11, 2),
        LocalDate.of(year, 11, 15), LocalDate.of(year, 12, 25),
    )
    if (year >= 2024) list.add(LocalDate.of(year, 11, 20))
    val easter = easterDate(year)
    list.add(easter.minusDays(2))
    list.add(easter.minusDays(48))
    list.add(easter.minusDays(47))
    return list
}

fun holidaysInMonth(year: Int, month1: Int): List<LocalDate> =
    nationalHolidays(year).filter { it.monthValue == month1 }

fun sundaysInMonth(year: Int, month1: Int): List<LocalDate> {
    val len = YearMonth.of(year, month1).lengthOfMonth()
    return (1..len).map { LocalDate.of(year, month1, it) }.filter { it.dayOfWeek == DayOfWeek.SUNDAY }
}

fun nthBusinessDay(year: Int, month1: Int, n: Int): LocalDate {
    val hol = holidaysInMonth(year, month1).toSet()
    val len = YearMonth.of(year, month1).lengthOfMonth()
    var d = LocalDate.of(year, month1, 1)
    var count = 0
    var day = 1
    while (day <= len) {
        d = LocalDate.of(year, month1, day)
        val dow = d.dayOfWeek
        if (dow != DayOfWeek.SUNDAY && dow != DayOfWeek.SATURDAY && d !in hol) count++
        if (count >= n) break
        day++
    }
    return d
}

fun lastBusinessDay(year: Int, month1: Int): LocalDate {
    val hol = holidaysInMonth(year, month1).toSet()
    var d = LocalDate.of(year, month1, YearMonth.of(year, month1).lengthOfMonth())
    while (d.dayOfWeek == DayOfWeek.SUNDAY || d.dayOfWeek == DayOfWeek.SATURDAY || d in hol) d = d.minusDays(1)
    return d
}

fun taxDueISO(ym: String): String {
    val y = ym.take(4).toInt()
    val m = ym.substring(5, 7).toInt()
    val nm = m + 1
    return if (nm > 12) lastBusinessDay(y + 1, 1).toString() else lastBusinessDay(y, nm).toString()
}

// ---------------- INSS / IRRF (tabelas 2026 do app) ----------------

private val INSS_BRACKETS = listOf(1621.00 to 0.075, 2902.84 to 0.09, 4354.27 to 0.12, 8475.55 to 0.14)

fun computeINSS(gross: Double): Double {
    if (gross <= 0) return 0.0
    val capped = Math.min(gross, 8475.55)
    var inss = 0.0
    var prev = 0.0
    for ((limit, rate) in INSS_BRACKETS) {
        if (capped > prev) inss += (Math.min(capped, limit) - prev) * rate
        prev = limit
    }
    return r2(inss)
}

private class IrBracket(val limit: Double, val rate: Double, val deduct: Double)

private val IRRF_BRACKETS = listOf(
    IrBracket(2428.80, 0.0, 0.0),
    IrBracket(2826.65, 0.075, 182.16),
    IrBracket(3751.05, 0.15, 394.16),
    IrBracket(4664.68, 0.225, 675.49),
    IrBracket(Double.POSITIVE_INFINITY, 0.275, 908.73),
)

private fun irrfTabela(base: Double): Double {
    if (base <= 0) return 0.0
    val b = IRRF_BRACKETS.first { base <= it.limit }
    return Math.max(0.0, r2(base * b.rate - b.deduct))
}

private fun irrfRedutor(rtb: Double): Double =
    if (rtb > 7350) 0.0 else Math.max(0.0, r2(978.62 - 0.133145 * rtb))

fun computeIRRF(rtb: Double, baseDeducao: Double): Double {
    val base = Math.max(0.0, rtb - Math.max(baseDeducao, 607.20))
    return Math.max(0.0, r2(irrfTabela(base) - irrfRedutor(rtb)))
}

// ---------------- renda ----------------

fun JSONObject.holidayDayValue(): Double {
    val inc = sub("income")
    val hourly = inc.optDouble("baseSalary", 0.0) / 220.0
    val holH = inc.optDouble("holidayHours", 7.0)
    val nightH = Math.min(inc.optDouble("holidayNightHours", 0.0), holH)
    val dayH = holH - nightH
    val pct = inc.optDouble("holidayPct", 100.0)
    val npct = inc.optDouble("holidayNightPct", 20.0)
    val dayValue = dayH * hourly * (1 + pct / 100)
    val nightValue = nightH * 1.142857 * hourly * (1 + pct / 100 + npct / 100)
    return r2(dayValue + nightValue)
}

fun JSONObject.pontoExtraHoursForMonth(year: Int, month1: Int): Double {
    val inc = sub("income")
    val sched = scheduledHours(inc, shiftForMonth(inc, year, month1))
    var extra = 0.0
    clockList().forEach { e ->
        val d = e.str("date")
        if (d.length >= 7 && d.take(4).toIntOrNull() == year && d.substring(5, 7).toIntOrNull() == month1) {
            extra += Math.max(0.0, e.optDouble("hours", 0.0) - sched)
        }
    }
    return r2(extra)
}

class MonthlyIncome(
    val salaryWithExtras: Double,
    val extraHoursValue: Double,
    val pontoExtraHours: Double,
    val inssValue: Double,
    val irrfValue: Double,
    val holidaysValue: Double,
    val holidaysCount: Int,
    val sundaysValue: Double,
    val sundaysCount: Int,
    val net: Double,
)

fun JSONObject.computeMonthlyIncome(year: Int, month1: Int): MonthlyIncome {
    val inc = sub("income")
    val base = inc.optDouble("baseSalary", 0.0)
    val hourly = base / 220.0
    val pontoExtra = pontoExtraHoursForMonth(year, month1)
    val extraValue = r2(hourly * pontoExtra * (1 + inc.optDouble("extraHourPct", 50.0) / 100))
    val salaryWithExtras = r2(base + extraValue)
    val inss = computeINSS(salaryWithExtras)
    val irrf = computeIRRF(salaryWithExtras, inss)
    val holCount = holidaysInMonth(year, month1).size
    val holValue = r2(holCount * holidayDayValue())
    val worked = sundays().toSet()
    val sunCount = sundaysInMonth(year, month1).count { it.toString() in worked }
    val sunValue = r2(sunCount * inc.optDouble("sundayBonus", 70.0))
    val net = r2(salaryWithExtras - inss - irrf + holValue)
    return MonthlyIncome(salaryWithExtras, extraValue, pontoExtra, inss, irrf, holValue, holCount, sunValue, sunCount, net)
}

class Thirteenth(
    val gross: Double,
    val first: Double,
    val secondGross: Double,
    val inss: Double,
    val ir: Double,
    val secondNet: Double,
)

fun JSONObject.thirteenth(): Thirteenth {
    val gross = r2(sub("income").optDouble("baseSalary", 0.0))
    val first = r2(gross / 2)
    val secondGross = r2(gross - first)
    val inss = computeINSS(gross)
    val ir = computeIRRF(gross, inss)
    return Thirteenth(gross, first, secondGross, inss, ir, r2(secondGross - inss - ir))
}

class Ferias(val umTerco: Double, val grossTotal: Double, val inss: Double, val ir: Double, val net: Double)

fun JSONObject.feriasBreakdown(): Ferias {
    val base = sub("income").optDouble("baseSalary", 0.0)
    val umTerco = r2(base / 3)
    val grossTotal = r2(base + umTerco)
    val inss = computeINSS(base)
    val ir = computeIRRF(base, inss)
    return Ferias(umTerco, grossTotal, inss, ir, r2(grossTotal - inss - ir))
}

// ---------------- históricos e estatísticas ----------------

fun JSONObject.recordStat(h: JSONObject) {
    val a = h.optDouble("amount", 0.0)
    val ts = h.str("ts")
    if (ts.length < 7 || a == 0.0) return
    val ym = ts.take(7)
    val t = h.str("type")
    val stats = sub("monthlyStats")
    val s = stats.optJSONObject(ym) ?: JSONObject()
        .put("spent", 0.0).put("income", 0.0).put("invested", 0.0).put("cats", JSONObject())
        .also { stats.put(ym, it) }
    val cats = s.sub("cats")
    fun addCat(c: String, v: Double) {
        cats.put(c, r2(cats.optDouble(c, 0.0) + v))
        s.put("spent", r2(s.optDouble("spent", 0.0) + v))
    }
    when {
        t == "renda" && a > 0 -> s.put("income", r2(s.optDouble("income", 0.0) + a))
        t == "despesa" && a < 0 -> {
            val hc = h.optJSONObject("cats")
            if (hc != null) hc.keys().forEach { k -> addCat(k, hc.optDouble(k, 0.0)) } else addCat("Gastos fixos", -a)
        }
        t == "gastolivre" && a < 0 -> addCat(h.str("category").ifEmpty { "Gasto livre" }, -a)
        t == "fatura" && a < 0 -> addCat("Cartão", -a)
        t == "pix" && a < 0 -> addCat("Pix enviado", -a)
        t == "imposto" && a < 0 -> addCat("Impostos", -a)
        (t == "aporte" || t == "transferencia" || t == "cdb" || t == "meta") && a < 0 ->
            s.put("invested", r2(s.optDouble("invested", 0.0) - a))
    }
}

// ---------------- totais ----------------

fun JSONObject.assetList(): List<JSONObject> = objList("assets")
fun JSONObject.assetById(id: String): JSONObject? = assetList().firstOrNull { it.opt("id").toString() == id }

fun JSONObject.portfolioValue(): Double = assetList().sumOf { it.optInt("quantity") * it.optDouble("price") }
fun JSONObject.goalsTotalSaved(): Double = objList("goals").sumOf { it.optDouble("saved", 0.0) }
fun JSONObject.reservesTotalBalance(): Double = objList("reserves").sumOf { it.optDouble("balance", 0.0) }
fun JSONObject.totalNetWorth(): Double = portfolioValue() + leftover + reservesTotalBalance() + goalsTotalSaved() + cash

fun JSONObject.allCards(): List<JSONObject> = listOf(sub("card")) + objList("extraCards")
fun JSONObject.cardAt(idx: Int): JSONObject =
    if (idx <= 0) sub("card") else (objList("extraCards").getOrNull(idx - 1) ?: sub("card"))

fun JSONObject.cardFuture(c: JSONObject): Double =
    c.objList("purchases").sumOf {
        it.optDouble("installmentValue", 0.0) * Math.max(0, it.optInt("installmentsTotal", 1) - it.optInt("installmentsPaid", 0))
    }

fun JSONObject.cardAvailable(c: JSONObject): Double =
    Math.max(0.0, c.optDouble("limit", 0.0) - c.optDouble("invoiceCurrent", 0.0) - c.optDouble("pendingAmount", 0.0) - cardFuture(c))

// ============================================================
// Simulação dia a dia
// ============================================================

fun JSONObject.simulateOneDay() {
    // 1. preços dos ativos (passeio aleatório com leve tendência)
    val mk = sub("market")
    val drift = Math.pow(1 + mk.optDouble("simGrowthPct", 9.5) / 100, 1.0 / 365) - 1
    assetList().forEach { a ->
        val vol = a.optDouble("volatility", 0.02) * 0.35
        val rnd = (Math.random() * 2 - 1) * vol
        var np = a.optDouble("price") * (1 + drift + rnd)
        if (np < 0.5) np = 0.5
        a.put("_prevPrice", a.optDouble("price"))
        a.put("price", Math.round(np * 100) / 100.0)
    }
    // 2. reservas: juros diários
    val rate = sub("reserveConfig").optDouble("annualRatePct", 11.0)
    val dailyRes = Math.pow(1 + rate / 100, 1.0 / 365) - 1
    objList("reserves").forEach { r ->
        val bal = r.optDouble("balance", 0.0)
        if (bal > 0) {
            val interest = bal * dailyRes
            r.put("balance", bal + interest)
            r.put("totalInterest", r.optDouble("totalInterest", 0.0) + interest)
        }
    }
    // 3. metas
    objList("goals").forEach { g ->
        if (!g.optBoolean("completed", false)) {
            val dr = Math.pow(1 + g.optDouble("rate", 0.0) / 100, 1.0 / 365) - 1
            val sv = g.optDouble("saved", 0.0)
            g.put("saved", sv + sv * dr)
        }
    }
    // 4. relógio
    put("simDateISO", plusDaysISO(optString("simDateISO", todayISO()), 1))
    // 5. renda, 13º, férias, despesas, eventos mensais, carteira, cartão
    maybeProcessIncomePayment()
    maybeProcessThirteenth()
    maybeProcessFerias()
    maybeProcessExpenses()
    maybeProcessMonthlyEvents()
    maybeProcessCarteiraMonthlyEvents()
    allCards().forEach { processCardCycle(it) }
}

/** Avança a simulação até a data real do aparelho. Retorna quantos dias foram processados. */
fun JSONObject.syncToRealDate(): Int {
    val today = todayISO()
    var n = 0
    while (optString("simDateISO", today) < today && n < 4000) {
        simulateOneDay()
        n++
    }
    return n
}

private fun JSONObject.maybeProcessIncomePayment() {
    when (sub("income").optString("incomeType", "empregado")) {
        "autonomo" -> {}
        "renda" -> maybeProcessBeneficio()
        else -> maybeProcessSalario()
    }
    maybeProcessSecondaryIncome()
}

private fun JSONObject.maybeProcessBeneficio() {
    val inc = sub("income")
    val sim = optString("simDateISO")
    if (isoDate(sim).dayOfMonth != inc.optInt("beneficioPayDay", 5)) return
    val ym = ymOf(sim)
    if (str("lastIncomePaidYM") == ym) return
    put("lastIncomePaidYM", ym)
    val amount = r2(Math.max(0.0, inc.optDouble("beneficioAmount", 0.0)))
    if (amount > 0) {
        cash = r2(cash + amount)
        inc.put("totalReceived", inc.optDouble("totalReceived", 0.0) + amount)
        addHistory("renda", inc.str("beneficioLabel").ifEmpty { "Benefício" } + " recebido", amount)
    }
}

private fun JSONObject.maybeProcessSecondaryIncome() {
    val sec = sub("secondaryIncome")
    if (!sec.optBoolean("enabled", false)) return
    val sim = optString("simDateISO")
    if (isoDate(sim).dayOfMonth != sec.optInt("payDay", 5)) return
    val ym = ymOf(sim)
    if (str("lastSecondaryIncomePaidYM") == ym) return
    put("lastSecondaryIncomePaidYM", ym)
    val amount = r2(Math.max(0.0, sec.optDouble("amount", 0.0)))
    if (amount > 0) {
        cash = r2(cash + amount)
        val inc = sub("income")
        inc.put("totalReceived", inc.optDouble("totalReceived", 0.0) + amount)
        addHistory("renda", sec.str("label").ifEmpty { "Renda extra" } + " recebida", amount)
    }
}

private fun JSONObject.addFgts(v: Double) {
    val f = sub("fgts")
    f.put("balance", r2(f.optDouble("balance", 0.0) + v))
}

fun JSONObject.recomputeFgtsBackfill() {
    val f = sub("fgts")
    f.put("balance", r2(f.optDouble("balance", 0.0) - f.optDouble("lastBackfillAmount", 0.0)))
    val inc = sub("income")
    val hire = inc.str("hireDateISO")
    if (hire.isEmpty() || inc.optString("incomeType", "empregado") != "empregado") {
        f.put("lastBackfillAmount", 0.0)
        return
    }
    val h = isoDate(hire)
    val t = isoDate(optString("simDateISO", todayISO()))
    var months = (t.year - h.year) * 12 + (t.monthValue - h.monthValue)
    if (t.dayOfMonth < h.dayOfMonth) months -= 1
    months = Math.max(0, months)
    val amount = r2(months * Math.max(0.0, inc.optDouble("baseSalary", 0.0)) * 0.08)
    f.put("lastBackfillAmount", amount)
    f.put("balance", r2(f.optDouble("balance", 0.0) + amount))
}

private fun JSONObject.maybeProcessSalario() {
    val inc = sub("income")
    val sim = optString("simDateISO")
    val d = isoDate(sim)
    val payDay = nthBusinessDay(d.year, d.monthValue, 5)
    if (d != payDay) return
    var workYear = d.year
    var workMonth = d.monthValue - 1 // 1..12 (0 = dezembro do ano anterior)
    if (workMonth < 1) {
        workMonth = 12
        workYear -= 1
    }
    val workYM = "$workYear-${pad2(workMonth)}"
    if (str("lastIncomePaidYM") == workYM) return
    put("lastIncomePaidYM", workYM)

    if (inc.optBoolean("feriasEnabled", true) && inc.optInt("feriasMonth", -1) == workMonth - 1) {
        addHistory("aviso", "Sem salário este mês — ${MONTH_NAMES[workMonth - 1]} foi mês de férias, já coberto no pagamento anterior", 0.0)
        return
    }
    val m = computeMonthlyIncome(workYear, workMonth)
    val workLabel = "${MONTH_NAMES[workMonth - 1]} de $workYear"
    if (m.net != 0.0) {
        cash = cash + m.net
        inc.put("totalReceived", inc.optDouble("totalReceived", 0.0) + m.net)
        val extra = if (m.holidaysCount > 0) " — ${m.holidaysCount} feriado(s)" else ""
        addHistory("renda", "Salário de $workLabel, recebido hoje$extra", m.net)
    }
    if (m.sundaysValue > 0) {
        cash = r2(cash + m.sundaysValue)
        inc.put("totalReceived", inc.optDouble("totalReceived", 0.0) + m.sundaysValue)
        addHistory("renda", "Recibo de domingo trabalhado (${m.sundaysCount} dia(s)) — $workLabel", m.sundaysValue)
    }
    if (m.net != 0.0 || m.sundaysValue > 0) {
        addFgts(r2(m.salaryWithExtras + m.holidaysValue + m.sundaysValue) * 0.08)
    }
}

private fun JSONObject.maybeProcessThirteenth() {
    val inc = sub("income")
    if (inc.optString("incomeType", "empregado") != "empregado" || !inc.optBoolean("thirteenthEnabled", true)) return
    val d = isoDate(optString("simDateISO"))
    if (d.monthValue == 11 && d.dayOfMonth == 30 && optInt("lastThirteenthFirstYear", -1) != d.year) {
        put("lastThirteenthFirstYear", d.year)
        val b = thirteenth()
        cash = r2(cash + b.first)
        addHistory("renda", "13º salário — 1ª parcela (${d.year})", b.first)
    }
    if (d.monthValue == 12 && d.dayOfMonth == 20 && optInt("lastThirteenthSecondYear", -1) != d.year) {
        put("lastThirteenthSecondYear", d.year)
        val b = thirteenth()
        cash = r2(cash + b.secondNet)
        addFgts(b.gross * 0.08)
        addHistory("renda", "13º salário — 2ª parcela (INSS ${brl(b.inss)}, IR ${brl(b.ir)})", b.secondNet)
    }
}

private fun JSONObject.maybeProcessFerias() {
    val inc = sub("income")
    if (inc.optString("incomeType", "empregado") != "empregado" || !inc.optBoolean("feriasEnabled", true) || inc.isNull("feriasMonth")) return
    val d = isoDate(optString("simDateISO"))
    if (d.monthValue - 1 != inc.optInt("feriasMonth", -1)) return
    if (d != nthBusinessDay(d.year, d.monthValue, 5)) return
    if (optInt("lastFeriasYear", -1) == d.year) return
    put("lastFeriasYear", d.year)
    val b = feriasBreakdown()
    cash = r2(cash + b.net)
    addFgts(inc.optDouble("baseSalary", 0.0) * 0.08)
    addHistory("renda", "Férias + 1/3 constitucional (${MONTH_NAMES[d.monthValue - 1]}/${d.year})", b.net)
}

private fun JSONObject.maybeProcessExpenses() {
    val sim = optString("simDateISO")
    val d = isoDate(sim)
    val expDay = nthBusinessDay(d.year, d.monthValue, 5).plusDays(1).toString()
    if (sim != expDay) return
    val ym = ymOf(sim)
    if (str("lastExpensesPaidYM") == ym) return
    put("lastExpensesPaidYM", ym)
    val exps = objList("expenses")
    val total = exps.sumOf { it.optDouble("amount", 0.0) }
    val actual = r2(Math.min(total, Math.max(0.0, cash)))
    if (actual > 0) {
        cash = cash - actual
        put("totalExpensesPaid", optDouble("totalExpensesPaid", 0.0) + actual)
        val cats = LinkedHashMap<String, Double>()
        exps.forEach { e ->
            val c = e.str("category").ifEmpty { e.str("name").ifEmpty { "Gastos fixos" } }
            cats[c] = (cats[c] ?: 0.0) + e.optDouble("amount", 0.0) * (if (total > 0) actual / total else 0.0)
        }
        cats.keys.toList().forEach { k -> cats[k] = r2(cats[k] ?: 0.0) }
        addHistory("despesa", "Gastos fixos de ${MONTH_NAMES[d.monthValue - 1]} de ${d.year} (1 dia após o salário)", -actual, cats = cats)
    }
    if (actual < total - 0.01) {
        addHistory("aviso", "Caixa insuficiente: gastos fixos pagos parcialmente (${brl(actual)} de ${brl(total)})", 0.0)
    }
    cash = r2(cash)
}

private fun JSONObject.maybeProcessMonthlyEvents() {
    val sim = optString("simDateISO")
    val d = isoDate(sim)
    val eventsDay = nthBusinessDay(d.year, d.monthValue, 5).plusDays(2).toString()
    if (sim != eventsDay) return
    val ym = ymOf(sim)
    if (str("lastProcessedYM") == ym) return
    put("lastProcessedYM", ym)
    processMonthlyEvents(ym)
}

private fun JSONObject.processMonthlyEvents(ym: String) {
    val sim = optString("simDateISO")
    objList("reserves").forEach { r ->
        val desired = r.optDouble("monthlyContribution", 0.0)
        val actual = r2(Math.min(desired, Math.max(0.0, cash)))
        cash = cash - actual
        if (actual > 0 && r.optDouble("balance", 0.0) <= 0 && r.str("startDateISO").isEmpty()) r.put("startDateISO", sim)
        r.put("balance", r.optDouble("balance", 0.0) + actual)
        r.put("totalContributed", r.optDouble("totalContributed", 0.0) + actual)
        if (actual > 0) addHistory("cdb", "Aporte na reserva \"${r.optString("name")}\"", -actual)
        if (actual < desired - 0.01) {
            addHistory("aviso", "Caixa insuficiente: aporte da reserva \"${r.optString("name")}\" reduzido de ${brl(desired)} para ${brl(actual)}", 0.0)
        }
    }
    objList("goals").forEach { g ->
        if (g.optBoolean("completed", false)) return@forEach
        val monthly = g.optDouble("monthly", 0.0)
        if (monthly > 0) {
            val actual = r2(Math.min(monthly, Math.max(0.0, cash)))
            cash = cash - actual
            g.put("saved", g.optDouble("saved", 0.0) + actual)
            if (actual > 0) addHistory("meta", "Aporte na meta \"${g.optString("name")}\"", -actual)
            if (actual < monthly - 0.01) addHistory("aviso", "Caixa insuficiente para o aporte completo da meta \"${g.optString("name")}\"", 0.0)
        }
        if (g.optDouble("saved", 0.0) >= g.optDouble("target", 0.0)) {
            g.put("completed", true)
            g.put("completedOn", sim)
            addHistory("conquista", "Meta concluída: ${g.optString("name")}", g.optDouble("target", 0.0))
        }
    }
    cash = r2(cash)
    val hist = objList("netWorthHistory")
    hist.add(
        JSONObject().put("ym", ym).put("value", r2(totalNetWorth()))
            .put("invested", r2(portfolioValue() + leftover + reservesTotalBalance() + goalsTotalSaved())),
    )
    putList("netWorthHistory", if (hist.size > 240) hist.takeLast(240) else hist)
}

// ---------------- cartão ----------------

private fun JSONObject.processCardCycle(c: JSONObject) {
    val sim = optString("simDateISO")
    val d = isoDate(sim)
    val day = d.dayOfMonth
    val ym = ymOf(sim)
    val closing = c.optInt("closingDay", 25)
    val due = c.optInt("dueDay", 5)

    if (day == closing && c.str("lastClosedYM") != ym) {
        c.put("lastClosedYM", ym)
        if (c.optDouble("invoiceCurrent", 0.0) > 0) {
            c.put("pendingAmount", r2(c.optDouble("pendingAmount", 0.0) + c.optDouble("invoiceCurrent", 0.0)))
            var dueYear = d.year
            var dueMonth = d.monthValue
            if (due <= closing) {
                dueMonth += 1
                if (dueMonth > 12) {
                    dueMonth = 1
                    dueYear += 1
                }
            }
            c.put("pendingDueYM", "$dueYear-${pad2(dueMonth)}")
        }
        var inv = 0.0
        val keep = mutableListOf<JSONObject>()
        c.objList("purchases").forEach { p ->
            val total = p.optInt("installmentsTotal", 1)
            var paid = p.optInt("installmentsPaid", 0)
            if (paid < total) {
                paid++
                p.put("installmentsPaid", paid)
                val value = if (paid == total) {
                    r2(p.optDouble("totalAmount", 0.0) - p.optDouble("installmentValue", 0.0) * (total - 1))
                } else {
                    p.optDouble("installmentValue", 0.0)
                }
                inv = r2(inv + value)
            }
            if (paid < total) keep.add(p)
        }
        c.put("invoiceCurrent", inv)
        c.putList("purchases", keep)
    }

    if (c.optDouble("pendingAmount", 0.0) > 0 && c.str("pendingDueYM") == ym && day == due) {
        val owed = c.optDouble("pendingAmount", 0.0)
        val actual = r2(Math.min(owed, Math.max(0.0, cash)))
        cash = r2(cash - actual)
        c.put("pendingAmount", r2(owed - actual))
        if (actual > 0) addHistory("fatura", "Fatura do " + c.str("name").ifEmpty { "cartão" }, -actual)
        if (actual < owed - 0.01) {
            addHistory("aviso", "Caixa insuficiente: fatura do cartão paga parcialmente (${brl(actual)} de ${brl(owed)})", 0.0)
        }
    }
}

fun JSONObject.addCardPurchase(idx: Int, desc: String, amount: Double, installments: Int): String? {
    if (desc.isBlank() || amount <= 0) return "Informe a descrição e o valor."
    val c = cardAt(idx)
    val n = Math.max(1, Math.min(24, installments))
    if (amount > cardAvailable(c) + 0.001) return "Limite do cartão insuficiente pra essa compra (parcelas futuras já comprometidas contam no limite)."
    val iv = r2(amount / n)
    val id = c.optInt("nextPurchaseId", 1)
    c.put("nextPurchaseId", id + 1)
    val list = c.objList("purchases")
    list.add(
        JSONObject().put("id", id).put("desc", desc.trim()).put("totalAmount", amount).put("installmentsTotal", n)
            .put("installmentValue", iv).put("installmentsPaid", 1).put("dateISO", optString("simDateISO")),
    )
    c.putList("purchases", list)
    c.put("invoiceCurrent", r2(c.optDouble("invoiceCurrent", 0.0) + iv))
    val label = if (n > 1) "Compra no cartão: ${desc.trim()} (1/$n — ${brl(iv)}/mês)" else "Compra no cartão: ${desc.trim()} (na fatura)"
    addHistory("cartao", label, -iv)
    return null
}

fun JSONObject.removeCardPurchase(idx: Int, id: Int) {
    val c = cardAt(idx)
    val list = c.objList("purchases")
    val p = list.firstOrNull { it.optInt("id") == id } ?: return
    list.remove(p)
    c.putList("purchases", list)
    c.put("invoiceCurrent", r2(Math.max(0.0, c.optDouble("invoiceCurrent", 0.0) - p.optDouble("installmentValue", 0.0))))
}

fun JSONObject.addExtraCard() {
    val list = objList("extraCards")
    val n = list.size + 2
    list.add(
        JSONObject().put("limit", 1000).put("invoiceCurrent", 0.0).put("pendingAmount", 0.0).put("pendingDueYM", JSONObject.NULL)
            .put("closingDay", 25).put("dueDay", 5).put("lastClosedYM", JSONObject.NULL)
            .put("last4", (1000 + (Math.random() * 8999).toInt()).toString())
            .put("purchases", JSONArray()).put("nextPurchaseId", 1).put("name", "Cartão $n"),
    )
    putList("extraCards", list)
    put("activeCardIdx", list.size)
}

fun JSONObject.removeCardAt(idx: Int) {
    val extras = objList("extraCards")
    if (idx == 0) {
        if (extras.isEmpty()) return
        val first = extras.removeAt(0)
        first.put("enabled", true)
        put("card", first)
    } else {
        if (idx - 1 < extras.size) extras.removeAt(idx - 1)
    }
    putList("extraCards", extras)
    put("activeCardIdx", 0)
}

fun JSONObject.updateCardConfig(idx: Int, name: String, limit: Double, closing: Int, due: Int) {
    val c = cardAt(idx)
    if (name.isNotBlank()) c.put("name", name.trim())
    c.put("limit", Math.max(0.0, limit))
    c.put("closingDay", closing.coerceIn(1, 28))
    c.put("dueDay", due.coerceIn(1, 28))
}

/** Paga a fatura fechada com o saldo disponível. Retorna mensagem de erro ou null. */
fun JSONObject.payInvoiceNow(idx: Int): String? {
    val c = cardAt(idx)
    val pend = c.optDouble("pendingAmount", 0.0)
    if (pend <= 0) return "Não há fatura fechada pendente pra pagar agora."
    if (cash <= 0) return "Seu saldo em conta está zerado."
    val actual = r2(Math.min(pend, cash))
    cash = r2(cash - actual)
    val left = r2(pend - actual)
    c.put("pendingAmount", if (left <= 0.009) 0.0 else left)
    if (left <= 0.009) c.put("pendingDueYM", JSONObject.NULL)
    addHistory("fatura", "Fatura do cartão paga antecipadamente", -actual)
    return null
}

// ---------------- Pix ----------------

fun JSONObject.pixPrecheck(amount: Double): String? {
    if (amount <= 0) return "Informe um valor."
    if (amount > cash + 0.001) return "Saldo insuficiente na conta corrente para esse Pix."
    val limit = sub("settings").optDouble("pixDailyLimit", 0.0)
    if (limit > 0) {
        val today = todayISO()
        val sent = if (str("pixSentTodayISO") == today) optDouble("pixSentTodayTotal", 0.0) else 0.0
        if (sent + amount > limit + 0.001) {
            return "Isso passaria do limite diário de Pix (${brl(limit)}). Já enviado hoje: ${brl(sent)}."
        }
    }
    return null
}

fun JSONObject.sendPix(name: String, amount: Double): String? {
    if (name.isBlank()) return "Informe o destinatário."
    pixPrecheck(amount)?.let { return it }
    cash = r2(cash - amount)
    val today = todayISO()
    if (str("pixSentTodayISO") != today) {
        put("pixSentTodayISO", today)
        put("pixSentTodayTotal", 0.0)
    }
    put("pixSentTodayTotal", r2(optDouble("pixSentTodayTotal", 0.0) + amount))
    addHistory("pix", "Pix enviado para ${name.trim()}", -amount)
    return null
}

fun JSONObject.receivePix(name: String, amount: Double): String? {
    if (name.isBlank() || amount <= 0) return "Informe o remetente e o valor."
    cash = r2(cash + amount)
    addHistory("pix", "Pix recebido de ${name.trim()}", amount)
    return null
}

// ---------------- caixinhas ----------------

class WithdrawEst(val net: Double, val ir: Double, val aliquota: Double, val yieldPortion: Double, val daysHeld: Long)

fun reserveIrAliquota(daysHeld: Long): Double = when {
    daysHeld <= 180 -> 0.225
    daysHeld <= 360 -> 0.20
    daysHeld <= 720 -> 0.175
    else -> 0.15
}

fun JSONObject.reserveWithdrawEstimate(r: JSONObject, amount: Double): WithdrawEst {
    val bal = r.optDouble("balance", 0.0)
    val interest = r.optDouble("totalInterest", 0.0)
    val start = r.str("startDateISO")
    if (bal <= 0 || interest <= 0 || start.isEmpty()) return WithdrawEst(amount, 0.0, 0.0, 0.0, 0)
    val days = Math.max(0L, daysBetween(start, optString("simDateISO", todayISO())))
    val aliq = reserveIrAliquota(days)
    val share = (interest / bal).coerceIn(0.0, 1.0)
    val yp = r2(amount * share)
    val ir = r2(yp * aliq)
    return WithdrawEst(r2(amount - ir), ir, aliq, yp, days)
}

fun JSONObject.addReserve(name: String, monthly: Double, target: Double) {
    if (name.isBlank()) return
    val id = takeId("nextReserveId", "reserves")
    val list = objList("reserves")
    list.add(
        JSONObject().put("id", id).put("name", name.trim()).put("balance", 0.0)
            .put("monthlyContribution", Math.max(0.0, monthly)).put("targetValue", Math.max(0.0, target))
            .put("totalContributed", 0.0).put("totalInterest", 0.0).put("startDateISO", JSONObject.NULL)
            .put("createdOn", optString("simDateISO")),
    )
    putList("reserves", list)
    addHistory("cdb", "Nova reserva criada: ${name.trim()}", 0.0)
}

fun JSONObject.removeReserve(id: Int) {
    putList("reserves", objList("reserves").filter { it.optInt("id") != id })
}

fun JSONObject.saveReserve(id: Int, name: String, monthly: Double, target: Double) {
    val r = objList("reserves").firstOrNull { it.optInt("id") == id } ?: return
    if (name.isBlank()) return
    r.put("name", name.trim())
    r.put("monthlyContribution", Math.max(0.0, monthly))
    r.put("targetValue", Math.max(0.0, target))
}

fun JSONObject.reserveDeposit(id: Int, amount: Double): String? {
    val r = objList("reserves").firstOrNull { it.optInt("id") == id } ?: return "Reserva não encontrada."
    if (amount <= 0) return "Informe um valor."
    val actual = r2(Math.min(amount, Math.max(0.0, cash)))
    if (actual <= 0) return "Seu saldo em conta está zerado."
    cash = cash - actual
    if (r.optDouble("balance", 0.0) <= 0 && r.str("startDateISO").isEmpty()) r.put("startDateISO", optString("simDateISO"))
    r.put("balance", r.optDouble("balance", 0.0) + actual)
    r.put("totalContributed", r.optDouble("totalContributed", 0.0) + actual)
    addHistory("cdb", "Aporte extra na reserva \"${r.optString("name")}\"", -actual)
    if (actual < amount - 0.01) {
        addHistory("aviso", "Caixa livre insuficiente: aporte extra na reserva \"${r.optString("name")}\" reduzido de ${brl(amount)} para ${brl(actual)}", 0.0)
    }
    return null
}

fun JSONObject.reserveWithdraw(id: Int, amount: Double): String? {
    val r = objList("reserves").firstOrNull { it.optInt("id") == id } ?: return "Reserva não encontrada."
    if (amount <= 0) return "Informe um valor."
    val actual = r2(Math.min(amount, Math.max(0.0, r.optDouble("balance", 0.0))))
    if (actual <= 0) return "Essa reserva está sem saldo."
    val est = reserveWithdrawEstimate(r, actual)
    r.put("balance", r.optDouble("balance", 0.0) - actual)
    r.put("totalInterest", r2(Math.max(0.0, r.optDouble("totalInterest", 0.0) - est.yieldPortion)))
    if (r.optDouble("balance", 0.0) <= 0) r.put("startDateISO", JSONObject.NULL)
    cash = cash + est.net
    val label = if (est.ir > 0) {
        "Resgate da reserva \"${r.optString("name")}\" (IR de ${brl(est.ir)} sobre o rendimento)"
    } else {
        "Resgate da reserva \"${r.optString("name")}\""
    }
    addHistory("cdb", label, est.net)
    if (actual < amount - 0.01) {
        addHistory("aviso", "Saldo insuficiente: resgate da reserva \"${r.optString("name")}\" reduzido de ${brl(amount)} para ${brl(actual)}", 0.0)
    }
    return null
}

fun JSONObject.goalDeposit(id: Int, amount: Double): String? {
    val g = objList("goals").firstOrNull { it.optInt("id") == id } ?: return "Meta não encontrada."
    if (g.optBoolean("completed", false)) return "Essa meta já foi concluída."
    if (amount <= 0) return "Informe um valor."
    val actual = r2(Math.min(amount, Math.max(0.0, cash)))
    if (actual <= 0) return "Seu saldo em conta está zerado."
    cash = cash - actual
    g.put("saved", g.optDouble("saved", 0.0) + actual)
    addHistory("meta", "Aporte extra na meta \"${g.optString("name")}\"", -actual)
    if (actual < amount - 0.01) {
        addHistory("aviso", "Caixa livre insuficiente: aporte extra na meta \"${g.optString("name")}\" reduzido de ${brl(amount)} para ${brl(actual)}", 0.0)
    }
    if (g.optDouble("saved", 0.0) >= g.optDouble("target", 0.0)) {
        g.put("completed", true)
        g.put("completedOn", optString("simDateISO"))
        addHistory("conquista", "Meta concluída: ${g.optString("name")}", g.optDouble("target", 0.0))
    }
    return null
}

fun JSONObject.saveGoal(id: Int, name: String, type: String, target: Double, monthly: Double, rate: Double) {
    val g = objList("goals").firstOrNull { it.optInt("id") == id } ?: return
    if (name.isBlank() || target <= 0) return
    g.put("name", name.trim())
    g.put("type", type)
    g.put("target", target)
    g.put("monthly", Math.max(0.0, monthly))
    g.put("rate", Math.max(0.0, rate))
    val saved = g.optDouble("saved", 0.0)
    if (saved >= target && !g.optBoolean("completed", false)) {
        g.put("completed", true)
        g.put("completedOn", optString("simDateISO"))
    } else if (saved < target && g.optBoolean("completed", false)) {
        g.put("completed", false)
        g.put("completedOn", JSONObject.NULL)
    }
}

fun JSONObject.removeGoal(id: Int) {
    putList("goals", objList("goals").filter { it.optInt("id") != id })
}

fun JSONObject.estimateMonthsToGoal(g: JSONObject): Int? {
    if (g.optBoolean("completed", false)) return 0
    val saved = g.optDouble("saved", 0.0)
    val target = g.optDouble("target", 0.0)
    if (saved >= target) return 0
    val monthly = g.optDouble("monthly", 0.0)
    val rate = g.optDouble("rate", 0.0)
    if (monthly <= 0 && rate <= 0) return null
    var bal = saved
    val mr = Math.pow(1 + rate / 100, 1.0 / 12) - 1
    for (m in 1..1200) {
        bal += bal * mr + monthly
        if (bal >= target) return m
    }
    return null
}

// ---------------- carteira de investimentos ----------------

fun JSONObject.brokerageFee(): Double = Math.max(0.0, sub("settings").optDouble("brokerageFee", 0.0))

fun JSONObject.costBasisOf(a: JSONObject): Double =
    if (a.opt("costBasis") is Number) a.optDouble("costBasis") else r2(a.optInt("quantity") * a.optDouble("price"))

fun JSONObject.avgPriceOf(a: JSONObject): Double {
    val q = a.optInt("quantity")
    return if (q > 0) costBasisOf(a) / q else 0.0
}

fun JSONObject.riskLabelOf(a: JSONObject): String {
    val v = a.optDouble("volatility", 0.0)
    return if (v <= 0.012) "Baixo" else if (v <= 0.02) "Moderado" else "Alto"
}

fun JSONObject.buyShares(a: JSONObject, qty: Int): Double {
    val fee = brokerageFee()
    val cost = r2(qty * a.optDouble("price") + fee)
    a.put("costBasis", r2(costBasisOf(a) + cost))
    a.put("quantity", a.optInt("quantity") + qty)
    leftover = r2(leftover - cost)
    put("totalInvestedCost", optDouble("totalInvestedCost", 0.0) + cost)
    val extra = if (fee > 0) " (corretagem de ${brl(fee)} inclusa)" else ""
    addHistory("compra", "Compra de $qty cota(s) de ${a.optString("ticker")}$extra", -cost)
    return cost
}

private fun JSONObject.sweepLeftover(budget: Double): Double {
    val fee = brokerageFee()
    var spent = 0.0
    for (guard in 0 until 60) {
        val remaining = r2(budget - spent)
        var bestAsset: JSONObject? = null
        var bestQty = 0
        var bestCost = 0.0
        assetList().forEach { a ->
            val price = a.optDouble("price")
            if (!(price > 0)) return@forEach
            val qty = Math.floor((remaining - fee) / price).toInt()
            if (qty < 1) return@forEach
            val cost = qty * price + fee
            if (bestAsset == null || cost > bestCost) {
                bestAsset = a
                bestQty = qty
                bestCost = cost
            }
        }
        val pick = bestAsset ?: break
        spent = r2(spent + buyShares(pick, bestQty))
    }
    return spent
}

private fun JSONObject.autoBuy(budget: Double): Double {
    val fee = brokerageFee()
    var spent = 0.0
    val alloc = assetList().filter { it.optDouble("allocPct", 0.0) > 0 && it.optDouble("price") > 0 }
    if (alloc.isNotEmpty()) {
        val totalPct = alloc.sumOf { it.optDouble("allocPct", 0.0) }
        alloc.forEach { a ->
            val share = budget * (a.optDouble("allocPct", 0.0) / totalPct)
            val qty = Math.floor((share - fee) / a.optDouble("price")).toInt()
            if (qty >= 1) {
                val cost = qty * a.optDouble("price") + fee
                if (cost <= budget - spent + 0.001) spent = r2(spent + buyShares(a, qty))
            }
        }
    }
    spent = r2(spent + sweepLeftover(r2(budget - spent)))
    return spent
}

private fun JSONObject.maybeProcessCarteiraMonthlyEvents() {
    val sim = optString("simDateISO")
    if (isoDate(sim).dayOfMonth != optInt("contributionDay", 7)) return
    val ym = ymOf(sim)
    if (str("lastCarteiraProcessedYM") == ym) return
    put("lastCarteiraProcessedYM", ym)
    processCarteiraMonthlyEvents()
}

private fun JSONObject.processCarteiraMonthlyEvents() {
    var dividends = 0.0
    assetList().forEach { a -> dividends += a.optInt("quantity") * a.optDouble("price") * (a.optDouble("yieldPct", 0.0) / 100) }
    dividends = r2(dividends)
    put("totalDividendsReceived", optDouble("totalDividendsReceived", 0.0) + dividends)
    if (dividends > 0) {
        leftover = r2(leftover + dividends)
        addHistory("dividendo", "Dividendos recebidos", dividends)
    }
    val monthlyWithdrawal = optDouble("monthlyWithdrawal", 0.0)
    val withdrawn = r2(Math.min(monthlyWithdrawal, Math.max(0.0, leftover)))
    if (withdrawn > 0) {
        leftover = r2(leftover - withdrawn)
        cash = r2(cash + withdrawn)
        put("totalWithdrawn", optDouble("totalWithdrawn", 0.0) + withdrawn)
        addHistory("saque", "Retirada da conta de investimentos para a conta principal", withdrawn)
        if (withdrawn < monthlyWithdrawal - 0.01) {
            addHistory("aviso", "Retirada parcial: a conta de investimentos não tinha saldo pro valor pedido", 0.0)
        }
    }
    var contributed = 0.0
    val monthlyContribution = optDouble("monthlyContribution", 0.0)
    if (optBoolean("recurringInvestEnabled", false) && monthlyContribution > 0) {
        contributed = r2(Math.min(monthlyContribution, Math.max(0.0, cash)))
        cash = r2(cash - contributed)
        leftover = r2(leftover + contributed)
        put("totalContributed", optDouble("totalContributed", 0.0) + contributed)
        if (contributed > 0) addHistory("aporte", "Aporte recorrente na conta de investimentos", -contributed)
        if (contributed < monthlyContribution - 0.01) {
            addHistory("aviso", "Saldo insuficiente: aporte recorrente reduzido de ${brl(monthlyContribution)} para ${brl(contributed)}", 0.0)
        }
    }
    val reinvest = optBoolean("reinvestDividends", false)
    val flagged = contributed > 0 || (reinvest && dividends > 0)
    if (flagged) {
        var budget = optDouble("investSobra", 0.0) + contributed + (if (reinvest) dividends else 0.0)
        budget = r2(Math.min(budget, Math.max(0.0, leftover)))
        val spent = autoBuy(budget)
        put("investSobra", r2(Math.max(0.0, budget - spent)))
    }
    put("investSobra", r2(Math.min(optDouble("investSobra", 0.0), Math.max(0.0, leftover))))
}

fun JSONObject.transferInvestAccounts(toInvest: Boolean, amountRaw: Double): String? {
    val amount = r2(amountRaw)
    if (!(amount > 0)) return "Informe um valor."
    if (toInvest) {
        if (amount > cash + 0.001) return "Saldo insuficiente na conta principal."
        cash = r2(cash - amount)
        leftover = r2(leftover + amount)
        addHistory("transferencia", "Transferência para a conta de investimentos", -amount)
    } else {
        if (amount > leftover + 0.001) return "Saldo insuficiente na conta de investimentos."
        leftover = r2(leftover - amount)
        cash = r2(cash + amount)
        put("investSobra", Math.min(optDouble("investSobra", 0.0), Math.max(0.0, leftover)))
        addHistory("transferencia", "Transferência da conta de investimentos para a principal", amount)
    }
    return null
}

fun JSONObject.manualInvest(assetId: String, qty: Int): String? {
    val a = assetById(assetId) ?: return "Ativo não encontrado."
    if (qty < 1) return "Informe a quantidade."
    val cost = r2(qty * a.optDouble("price") + brokerageFee())
    if (cost > leftover + 0.001) return "Saldo insuficiente na conta de investimentos. Transfira mais da conta principal."
    buyShares(a, qty)
    put("investSobra", Math.min(optDouble("investSobra", 0.0), Math.max(0.0, leftover)))
    return null
}

fun JSONObject.sellShares(assetId: String, qty: Int): String? {
    val a = assetById(assetId) ?: return "Ativo não encontrado."
    if (qty < 1) return "Informe a quantidade."
    val have = a.optInt("quantity")
    if (qty > have) return "Você só tem $have cota(s) de ${a.optString("ticker")}."
    val fee = brokerageFee()
    val gross = r2(qty * a.optDouble("price"))
    val proceeds = r2(Math.max(0.0, gross - fee))
    val costPart = r2(costBasisOf(a) * qty / have)
    val gain = r2(proceeds - costPart)
    a.put("costBasis", r2(Math.max(0.0, costBasisOf(a) - costPart)))
    a.put("quantity", have - qty)
    if (have - qty == 0) {
        a.put("costBasis", 0.0)
        a.put("allocPct", 0)
    }
    leftover = r2(leftover + proceeds)
    put("totalInvestedCost", r2(Math.max(0.0, optDouble("totalInvestedCost", 0.0) - costPart)))
    put("realizedGain", r2(optDouble("realizedGain", 0.0) + gain))
    val ledger = objList("taxLedger")
    ledger.add(JSONObject().put("ym", ymOf(optString("simDateISO"))).put("ticker", a.optString("ticker")).put("cls", assetTaxClass(a)).put("gross", gross).put("gain", gain))
    putList("taxLedger", ledger)
    put("investSobra", Math.min(optDouble("investSobra", 0.0), Math.max(0.0, leftover)))
    addHistory("venda", "Venda de $qty cota(s) de ${a.optString("ticker")} (${if (gain >= 0) "lucro " else "prejuízo "}${brl(Math.abs(gain))})", proceeds)
    return null
}

fun assetTaxClass(a: JSONObject): String = if (a.optString("category") == "acao") "acao" else "fii"

class TaxRow(
    val ym: String,
    val salesAcao: Double,
    val gainAcao: Double,
    val salesFii: Double,
    val gainFii: Double,
    val exempt: Boolean,
    val taxAcao: Double,
    val taxFii: Double,
    val tax: Double,
    val carryAcao: Double,
    val carryFii: Double,
    val dueISO: String,
    val paid: Boolean,
)

fun JSONObject.taxReport(extra: JSONObject? = null): List<TaxRow> {
    val ledger = objList("taxLedger") + (if (extra != null) listOf(extra) else emptyList())
    val months = ledger.map { it.str("ym") }.filter { it.length >= 7 }.distinct().sorted()
    var carryA = 0.0
    var carryF = 0.0
    val paidMap = optJSONObject("taxPaid")
    return months.map { ym ->
        val es = ledger.filter { it.str("ym") == ym }
        fun sum(cls: String, f: String): Double = r2(es.filter { it.str("cls") == cls }.sumOf { it.optDouble(f, 0.0) })
        val salesA = sum("acao", "gross")
        val gainA = sum("acao", "gain")
        val gainF = sum("fii", "gain")
        val salesF = sum("fii", "gross")
        var taxA = 0.0
        var exempt = false
        if (salesA > 0) {
            if (salesA <= 20000.0) {
                exempt = true
                if (gainA < 0) carryA = r2(carryA - gainA)
            } else {
                val net = r2(gainA - carryA)
                if (net > 0) {
                    taxA = r2(net * 0.15)
                    carryA = 0.0
                } else {
                    carryA = r2(-net)
                }
            }
        }
        var taxF = 0.0
        if (es.any { it.str("cls") == "fii" }) {
            val net = r2(gainF - carryF)
            if (net > 0) {
                taxF = r2(net * 0.20)
                carryF = 0.0
            } else {
                carryF = r2(-net)
            }
        }
        TaxRow(ym, salesA, gainA, salesF, gainF, exempt, taxA, taxF, r2(taxA + taxF), carryA, carryF, taxDueISO(ym), paidMap?.optBoolean(ym, false) == true)
    }
}

fun JSONObject.pendingDarfs(): List<TaxRow> = taxReport().filter { it.tax > 0.005 && !it.paid }

class SaleEstimate(val cls: String, val gain: Double, val delta: Double, val after: TaxRow)

fun JSONObject.estimateSaleTax(assetId: String, qty: Int): SaleEstimate? {
    val a = assetById(assetId) ?: return null
    val have = a.optInt("quantity")
    if (qty < 1 || have <= 0) return null
    val fee = brokerageFee()
    val gross = r2(qty * a.optDouble("price"))
    val proceeds = r2(Math.max(0.0, gross - fee))
    val gain = r2(proceeds - costBasisOf(a) * qty / have)
    val ym = ymOf(optString("simDateISO"))
    val before = taxReport().firstOrNull { it.ym == ym }
    val extra = JSONObject().put("ym", ym).put("ticker", a.optString("ticker")).put("cls", assetTaxClass(a)).put("gross", gross).put("gain", gain)
    val after = taxReport(extra).first { it.ym == ym }
    return SaleEstimate(assetTaxClass(a), gain, r2(after.tax - (before?.tax ?: 0.0)), after)
}

fun JSONObject.payDarf(ym: String): String? {
    val r = taxReport().firstOrNull { it.ym == ym } ?: return "Apuração não encontrada."
    if (r.tax <= 0.005 || r.paid) return null
    if (cash + 0.001 < r.tax) return "Saldo insuficiente na conta principal pra pagar o DARF de ${brl(r.tax)}."
    cash = r2(cash - r.tax)
    sub("taxPaid").put(ym, true)
    addHistory("imposto", "DARF 6015 — IR sobre ganho de capital de ${monthShort(ym)}", -r.tax)
    return null
}

// ---------------- perfil de investidor ----------------

class QuizQ(val q: String, val o: List<String>, val key: String? = null)

val QUIZ = listOf(
    QuizQ("Qual é o seu principal objetivo ao investir?", listOf("Preservar o que já tenho", "Ter uma renda extra todo mês", "Fazer o patrimônio crescer com o tempo", "Multiplicar o patrimônio, aceitando riscos")),
    QuizQ("Por quanto tempo pretende deixar o dinheiro investido?", listOf("Menos de 1 ano", "De 1 a 3 anos", "De 3 a 5 anos", "Mais de 5 anos"), "horizon"),
    QuizQ("Qual é a sua experiência com investimentos?", listOf("Nunca investi", "Poupança e CDB", "Já investi em fundos ou ações", "Invisto regularmente em renda variável")),
    QuizQ("Se o seu patrimônio caísse 15% em um mês, o que faria?", listOf("Venderia tudo", "Venderia uma parte", "Manteria", "Compraria mais")),
    QuizQ("Quanto você tem de reserva de emergência?", listOf("Nenhuma", "Até 3 meses de gastos", "De 3 a 6 meses", "Mais de 6 meses"), "reserve"),
    QuizQ("Quanto você entende de renda fixa e renda variável?", listOf("Quase nada", "Mais ou menos", "Entendo bem a diferença", "Acompanho o mercado com frequência")),
    QuizQ("Você vai precisar desse dinheiro em breve?", listOf("Sim, nos próximos meses", "Talvez", "Improvável", "Não, é dinheiro de longo prazo"), "need"),
    QuizQ("Qual cenário você aceitaria?", listOf("Ganhar 5%, sem perder nada", "Ganhar até 10%, podendo perder 5%", "Ganhar até 20%, podendo perder 12%", "Ganhar até 40%, podendo perder 25%")),
)

class ProfileInfo(val rank: Int, val text: String, val allows: String)

val PROFILE_INFO = mapOf(
    "Conservador" to ProfileInfo(1, "Você prioriza segurança e previsibilidade. Combina com renda fixa e ativos de risco baixo.", "baixo"),
    "Moderado" to ProfileInfo(2, "Você aceita oscilações moderadas em troca de mais retorno. Mistura renda fixa com FIIs e algumas ações.", "baixo e moderado"),
    "Arrojado" to ProfileInfo(3, "Você tolera perdas temporárias em busca de crescimento. Pode ter bastante renda variável.", "todos os níveis"),
)

class QuizResult(val score: Int, val label: String, val cap: String)

fun scoreInvestorQuiz(answers: List<Int>): QuizResult {
    val score = answers.sumOf { it + 1 }
    var label = if (score <= 14) "Conservador" else if (score <= 22) "Moderado" else "Arrojado"
    var cap = ""
    fun v(key: String): Int {
        val i = QUIZ.indexOfFirst { it.key == key }
        return if (i >= 0 && i < answers.size) answers[i] + 1 else 4
    }
    if (v("horizon") == 1 || v("need") == 1) {
        if (label != "Conservador") cap = "Como você precisa do dinheiro em breve, seu perfil foi limitado a Conservador."
        label = "Conservador"
    } else if (v("reserve") == 1 && label == "Arrojado") {
        label = "Moderado"
        cap = "Sem reserva de emergência, seu perfil foi limitado a Moderado."
    }
    return QuizResult(score, label, cap)
}

fun JSONObject.saveInvestorProfile(answers: List<Int>) {
    val r = scoreInvestorQuiz(answers)
    put(
        "investorProfile",
        JSONObject().put("answers", JSONArray(answers)).put("score", r.score).put("label", r.label)
            .put("cap", r.cap).put("doneAt", optString("simDateISO")),
    )
}

fun JSONObject.profileLabel(): String? = optJSONObject("investorProfile")?.str("label")?.ifEmpty { null }

fun JSONObject.profileValidUntil(): String? {
    val p = optJSONObject("investorProfile") ?: return null
    val done = p.str("doneAt")
    if (done.isEmpty()) return null
    return isoDate(done).plusYears(2).toString()
}

fun JSONObject.profileExpired(): Boolean {
    val u = profileValidUntil() ?: return false
    return u < optString("simDateISO", todayISO())
}

fun JSONObject.suitabilityWarning(a: JSONObject): String {
    val label = profileLabel() ?: return "Você ainda não fez o teste de perfil de investidor (Investimentos → Perfil de investidor)."
    val info = PROFILE_INFO[label] ?: return ""
    val risk = riskLabelOf(a)
    val rank = when (risk) { "Baixo" -> 1; "Moderado" -> 2; else -> 3 }
    if (rank > info.rank) {
        return "⚠️ ${a.optString("ticker")} é de risco $risk, acima do seu perfil $label (que combina com risco ${info.allows})."
    }
    return ""
}

fun JSONObject.portfolioRiskProfile(): String? {
    var total = 0.0
    var score = 0.0
    assetList().forEach { a ->
        val q = a.optInt("quantity")
        if (q > 0) {
            val v = q * a.optDouble("price")
            total += v
            val w = when (a.optString("category")) { "acao" -> 1.0; "tijolo" -> 0.5; "papel" -> 0.35; else -> 1.0 }
            score += v * w
        }
    }
    val fixed = reservesTotalBalance()
    total += fixed
    score += fixed * 0.1
    if (total <= 0) return null
    val s = score / total
    return if (s < 0.3) "Conservador" else if (s < 0.6) "Moderado" else "Arrojado"
}

// ---------------- gastos livres / dados ----------------

val FREE_SPEND_CATALOG = listOf(
    "Restaurante", "Lanche / padaria", "Delivery", "Bar / balada", "Cinema / shows", "Viagem", "Presentes", "Vestuário",
    "Beleza / cabelo", "Pets", "Jogos / apps", "Compras online", "Hobbies", "Doações", "Transporte por app", "Imprevistos",
)

fun JSONObject.toggleFreeCategory(name: String) {
    val cur = freeCats().toMutableList()
    if (cur.contains(name)) cur.remove(name) else cur.add(name)
    put("freeSpendCategories", JSONArray(cur))
}

fun JSONObject.addFreeCategory(name: String) {
    val n = name.trim()
    val cur = freeCats().toMutableList()
    if (n.isEmpty() || cur.contains(n)) return
    cur.add(n)
    put("freeSpendCategories", JSONArray(cur))
}

fun JSONObject.removeFreeCategory(name: String): Boolean {
    val cur = freeCats().toMutableList()
    if (cur.size <= 1) return false
    cur.remove(name)
    put("freeSpendCategories", JSONArray(cur))
    return true
}

fun JSONObject.resetPartial(kind: String) {
    val def = defaultStateJson()
    when (kind) {
        "historico" -> {
            put("history", JSONArray())
            put("monthlyStats", JSONObject())
        }
        "carteira" -> {
            put("assets", def.getJSONArray("assets"))
            put("monthlyContribution", 0)
            put("monthlyWithdrawal", 0)
            leftover = 0.0
            put("totalContributed", 0)
            put("totalWithdrawn", 0)
            put("totalInvestedCost", 0)
            put("totalDividendsReceived", 0)
            put("taxLedger", JSONArray())
            put("taxPaid", JSONObject())
            put("realizedGain", 0)
            put("investSobra", 0)
        }
        "caixinhas" -> {
            put("reserves", JSONArray())
            put("goals", JSONArray())
            put("nextReserveId", 1)
            put("nextGoalId", 1)
        }
        "gastos" -> {
            put("expenses", def.getJSONArray("expenses"))
            put("nextExpenseId", def.optInt("nextExpenseId", 6))
            put("shoppingLinkedExpenseId", def.optInt("shoppingLinkedExpenseId", 3))
            put("shoppingList", JSONArray())
            put("nextShoppingId", 1)
        }
    }
}

fun simpleHash(s: String): String {
    var h = 0
    for (ch in s) h = ((h shl 5) - h + ch.code)
    return h.toString()
}

// ---------------- assistente (alertas) ----------------

class Insight(val ico: String, val title: String, val desc: String, val goTab: String? = null, val goLabel: String? = null)

fun JSONObject.computeInsights(): List<Insight> {
    val out = mutableListOf<Insight>()
    val sim = isoDate(optString("simDateISO", todayISO()))
    if (sub("card").optBoolean("enabled", true)) {
        allCards().forEach { cd ->
            val pend = cd.optDouble("pendingAmount", 0.0)
            val dueYm = cd.str("pendingDueYM")
            if (pend > 0 && dueYm.length >= 7) {
                val due = LocalDate.of(dueYm.take(4).toInt(), dueYm.substring(5, 7).toInt(), cd.optInt("dueDay", 5).coerceIn(1, 28))
                val days = ChronoUnit.DAYS.between(sim, due)
                if (days <= 5) {
                    out.add(
                        Insight(
                            "💳", "Fatura chegando",
                            "A fatura do ${cd.str("name").ifEmpty { "cartão" }} (${brl(pend)}) " + (if (days <= 0) "vence hoje ou já venceu." else "vence em $days dia(s)."),
                            "conta", "Cartão",
                        ),
                    )
                }
            }
        }
    }
    val fixed = objList("expenses").sumOf { it.optDouble("amount", 0.0) }
    if (fixed > 0 && cash < fixed) {
        out.add(Insight("⚠️", "Saldo abaixo dos gastos fixos", "Seu saldo (${brl(cash)}) é menor que os gastos fixos do mês (${brl(fixed)}).", "gastos", "Gastos"))
    }
    val limit = optDouble("freeSpendMonthlyLimit", 0.0)
    if (limit > 0) {
        val spent = freeSpentThisMonth()
        val pct = spent / limit * 100
        if (pct >= sub("settings").optDouble("notifyFreeSpendPct", 90.0)) {
            out.add(Insight("🎉", "Gasto livre no limite", "Você já usou ${Math.round(pct)}% do limite de gasto livre deste mês.", "gastos", "Gastos"))
        }
    }
    if (sub("income").optString("incomeType", "empregado") == "empregado") {
        val today = LocalDate.now()
        if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            if (today.toString() !in sundays()) out.add(Insight("📅", "Hoje é domingo", "Se você trabalhou, marque o domingo na aba Ponto.", "ponto", "Ponto"))
        } else if (clockList().none { it.str("date") == today.toString() } && java.time.LocalTime.now().hour >= 6) {
            out.add(Insight("🕐", "Ponto de hoje", "Você ainda não registrou o ponto de hoje.", "ponto", "Bater ponto"))
        }
    }
    pendingDarfs().forEach { r ->
        val days = ChronoUnit.DAYS.between(sim, isoDate(r.dueISO))
        out.add(
            Insight(
                "🏛", "DARF a pagar",
                "IR de ${monthShort(r.ym)}: ${brl(r.tax)}" + (if (days < 0) " — vencido há ${-days} dia(s)." else " — vence em $days dia(s)."),
                "carteira", "Ver",
            ),
        )
    }
    if (assetList().any { it.optInt("quantity") > 0 } && (profileLabel() == null || profileExpired())) {
        out.add(Insight("🧭", "Perfil de investidor", if (profileLabel() == null) "Faça o teste de perfil pra receber alertas de adequação nas compras." else "Seu perfil de investidor venceu. Refaça o teste.", "carteira", "Fazer teste"))
    }
    val cheapest = assetList().map { it.optDouble("price") }.filter { it > 0 }.minOrNull()
    if (cheapest != null && leftover >= cheapest) {
        out.add(Insight("📈", "Dinheiro parado", "${brl(leftover)} na conta de investimentos já dá pra comprar cotas.", "carteira", "Investir"))
    }
    val div = assetList().sumOf { it.optInt("quantity") * it.optDouble("price") * (it.optDouble("yieldPct", 0.0) / 100) }
    if (div > 0) out.add(Insight("💰", "Dividendos estimados", "Seus ativos devem render cerca de ${brl(div)} por mês."))
    objList("reserves").forEach { r ->
        val t = r.optDouble("targetValue", 0.0)
        if (t > 0) {
            val p = r.optDouble("balance", 0.0) / t * 100
            if (p >= sub("settings").optDouble("notifyGoalPct", 90.0) && p < 100) {
                out.add(Insight("🏦", "Quase lá!", "A caixinha \"${r.optString("name")}\" está em ${Math.round(p)}% da meta."))
            }
        }
    }
    if (!sub("settings").optBoolean("autoBackupEnabled", true)) {
        val last = str("lastBackupAt")
        val days = if (last.isEmpty()) null else try {
            ChronoUnit.DAYS.between(LocalDate.parse(last.take(10)), LocalDate.now())
        } catch (e: Exception) {
            null
        }
        if (days == null || days >= sub("settings").optInt("notifyBackupDays", 14)) {
            out.add(Insight("💾", "Faça um backup", if (days == null) "Você ainda não exportou nenhum backup." else "Faz $days dias desde o último backup.", "config", "Backup"))
        }
    }
    return out
}

// ============================================================
// Estado inicial (conta nova) e preenchimento de campos ausentes
// ============================================================

private class AssetSeed(val t: String, val name: String, val cat: String, val price: Double, val yieldPct: Double, val vol: Double)

private val ASSET_SEEDS = listOf(
    AssetSeed("BBAS3", "Banco do Brasil", "acao", 27.0, 0.70, 0.030),
    AssetSeed("ITUB4", "Itaú Unibanco", "acao", 36.0, 0.60, 0.030),
    AssetSeed("TAEE11", "Taesa", "acao", 36.0, 0.80, 0.020),
    AssetSeed("CPFE3", "CPFL Energia", "acao", 38.0, 0.65, 0.025),
    AssetSeed("CMIG4", "Cemig", "acao", 11.0, 0.60, 0.030),
    AssetSeed("BBSE3", "BB Seguridade", "acao", 35.0, 0.70, 0.025),
    AssetSeed("VBBR3", "Vibra Energia", "acao", 22.0, 0.65, 0.030),
    AssetSeed("HGLG11", "CSHG Logística", "tijolo", 160.0, 0.75, 0.015),
    AssetSeed("KNRI11", "Kinea Renda Imobiliária", "tijolo", 150.0, 0.65, 0.015),
    AssetSeed("XPML11", "XP Malls", "tijolo", 95.0, 0.75, 0.015),
    AssetSeed("VISC11", "Vinci Shopping Centers", "tijolo", 105.0, 0.70, 0.015),
    AssetSeed("BTLG11", "BTG Pactual Logística", "tijolo", 100.0, 0.75, 0.015),
    AssetSeed("GGRC11", "GGR Covepi Renda", "tijolo", 95.0, 0.80, 0.018),
    AssetSeed("RBRP11", "RBR Properties", "tijolo", 65.0, 0.70, 0.018),
    AssetSeed("MXRF11", "Maxi Renda", "papel", 9.5, 0.90, 0.010),
    AssetSeed("KNCR11", "Kinea Rendimentos", "papel", 98.0, 0.90, 0.008),
    AssetSeed("IRDM11", "Iridium Recebíveis", "papel", 70.0, 0.95, 0.012),
    AssetSeed("VGIR11", "Valora RE III", "papel", 8.8, 0.90, 0.008),
    AssetSeed("KNIP11", "Kinea Índices de Preços", "papel", 85.0, 0.85, 0.010),
    AssetSeed("HGCR11", "CSHG Recebíveis Imob.", "papel", 90.0, 0.90, 0.010),
)

private const val DEFAULT_JSON = """
{
 "simDateISO":"__TODAY__",
 "account":{"agency":"0001","number":"__ACC__"},
 "card":{"enabled":true,"limit":1500,"invoiceCurrent":0,"pendingAmount":0,"pendingDueYM":null,"closingDay":25,"dueDay":5,"lastClosedYM":null,"last4":"__L4__","purchases":[],"nextPurchaseId":1,"name":"Cartão principal"},
 "extraCards":[],"nextExtraCardId":1,"activeCardIdx":0,
 "monthlyContribution":0,"recurringInvestEnabled":false,"reinvestDividends":false,"taxLedger":[],"taxPaid":{},
 "investorProfile":null,"realizedGain":0,"investSobra":0,"monthlyWithdrawal":0,"contributionDay":7,"leftover":0,
 "totalContributed":0,"totalWithdrawn":0,"totalInvestedCost":0,"totalDividendsReceived":0,
 "market":{"autoAssets":true,"simGrowthPct":9.5,"assetsUpdatedAt":null,"assetsUpdatedAtMs":null,"assetsError":null,"cdiUpdatedAt":null,"cdiError":null},
 "reserves":[
  {"id":1,"name":"Reserva de emergência","balance":0,"monthlyContribution":0,"targetValue":0,"totalContributed":0,"totalInterest":0,"startDateISO":null,"createdOn":"__TODAY__"},
  {"id":2,"name":"Viagens","balance":0,"monthlyContribution":0,"targetValue":0,"totalContributed":0,"totalInterest":0,"startDateISO":null,"createdOn":"__TODAY__"}
 ],
 "nextReserveId":3,"reserveConfig":{"annualRatePct":11},
 "settings":{"theme":"dark","fontSize":"normal","highContrast":false,"hapticEnabled":true,"bioEnabled":false,"bioCredId":"","assistantEnabled":true,"celebrationsEnabled":true,"displayName":"","hideBalance":false,"pinEnabled":false,"pinHash":null,"pixConfirmThreshold":0,"pixDailyLimit":0,"brokerageFee":0,"quoteRefreshMode":"daily","notifyCardDays":3,"notifyBackupDays":14,"autoBackupEnabled":true,"notifyGoalPct":90,"notifyFreeSpendPct":90},
 "onboardingCompleted":true,"pixSentTodayISO":null,"pixSentTodayTotal":0,"celebrated":{},"sundaysWorked":[],
 "shoppingList":[],"nextShoppingId":1,"products":[],"nextProductId":1,"goals":[],"nextGoalId":1,
 "profile":{"birthDateISO":null,"sex":null},
 "income":{"incomeType":"empregado","hireDateISO":null,"beneficioLabel":"Benefício social","beneficioAmount":0,"beneficioPayDay":5,
  "baseSalary":1621,"extraHours":0,"extraHourPct":50,"holidayHours":7,"holidayPct":100,"holidayNightHours":0,"holidayNightPct":20,
  "sundayBonus":70,"sundayAnchorISO":"__SUNDAY__","sundayAnchorWorked":true,"sundayMode":"alternado",
  "thirteenthEnabled":true,"feriasEnabled":true,"feriasMonth":__MONTH0__,"baseMonthKey":"__YM__","baseShift":"noite",
  "shiftMode":"alternado","fixedShift":"noite",
  "shifts":{"manha":{"name":"Manhã","detail":"07:00–11:00 · intervalo · 15:00–18:00","hours":7},
            "noite":{"name":"Tarde / Noite","detail":"11:00–15:00 · intervalo · 17:30–21:00","hours":7.5}},
  "totalReceived":0},
 "fgts":{"balance":0,"lastBackfillAmount":0},
 "lastBackupAt":null,"lastThirteenthFirstYear":null,"lastThirteenthSecondYear":null,"lastFeriasYear":null,
 "expenses":[{"id":1,"name":"Aluguel","amount":400,"category":"Moradia"},{"id":2,"name":"Energia","amount":100,"category":"Contas"},
  {"id":3,"name":"Alimentação","amount":0,"category":"Alimentação"},{"id":4,"name":"Internet","amount":130,"category":"Contas"},
  {"id":5,"name":"Telefonia","amount":120,"category":"Contas"}],
 "nextExpenseId":6,"shoppingLinkedExpenseId":3,"cash":0,"totalExpensesPaid":0,
 "history":[],"netWorthHistory":[],"monthlyStats":{},"shoppingDiscount":{"type":"pct","value":0},
 "lastProcessedYM":null,"lastCarteiraProcessedYM":null,"lastIncomePaidYM":null,
 "secondaryIncome":{"enabled":false,"label":"Renda extra","type":"salario","amount":0,"payDay":5},
 "lastSecondaryIncomePaidYM":null,"lastExpensesPaidYM":null,"timeClock":[],
 "freeSpendCategories":["Lazer","Imprevisto","Presente","Outro"],"freeSpendMonthlyLimit":0
}
"""

fun defaultStateJson(): JSONObject {
    val today = LocalDate.now()
    val dow = today.dayOfWeek.value % 7
    val sunday = today.plusDays(((7 - dow) % 7).toLong())
    val acc = (10000 + (Math.random() * 89999).toInt()).toString() + "-" + (Math.random() * 9).toInt()
    val l4 = (1000 + (Math.random() * 8999).toInt()).toString()
    val text = DEFAULT_JSON
        .replace("__TODAY__", today.toString())
        .replace("__ACC__", acc)
        .replace("__L4__", l4)
        .replace("__SUNDAY__", sunday.toString())
        .replace("__MONTH0__", (today.monthValue - 1).toString())
        .replace("__YM__", today.toString().take(7))
    val o = JSONObject(text)
    val arr = JSONArray()
    ASSET_SEEDS.forEach { s ->
        arr.put(
            JSONObject().put("id", s.t).put("ticker", s.t).put("name", s.name).put("category", s.cat)
                .put("price", s.price).put("yieldPct", s.yieldPct).put("volatility", s.vol).put("quantity", 0),
        )
    }
    o.put("assets", arr)
    return o
}

/** Equivale a mergeLoadedState do app HTML: completa campos que faltam em backups antigos. */
fun mergeWithDefaults(parsed: JSONObject): JSONObject {
    val def = defaultStateJson()
    def.keys().asSequence().toList().forEach { k ->
        if (!parsed.has(k)) {
            parsed.put(k, def.get(k))
        } else {
            val dv = def.opt(k)
            val pv = parsed.opt(k)
            if (dv is JSONObject && pv is JSONObject && k != "monthlyStats" && k != "celebrated" && k != "taxPaid") {
                dv.keys().asSequence().toList().forEach { sk -> if (!pv.has(sk)) pv.put(sk, dv.get(sk)) }
            }
        }
    }
    if (parsed.optJSONObject("market") != null) parsed.getJSONObject("market").put("autoAssets", true)
    return parsed
}
