package com.meubanco.patrimonio.domain

import java.time.LocalDate

/* ============================================================
   Feriados, domingos, turnos e ponto
   ============================================================ */

fun computeEasterDate(year: Int): LocalDate {
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

fun getNationalHolidays(year: Int): List<LocalDate> {
    val list = mutableListOf(
        LocalDate.of(year, 1, 1), LocalDate.of(year, 4, 21), LocalDate.of(year, 5, 1),
        LocalDate.of(year, 9, 7), LocalDate.of(year, 10, 12), LocalDate.of(year, 11, 2),
        LocalDate.of(year, 11, 15), LocalDate.of(year, 12, 25),
    )
    if (year >= 2024) list.add(LocalDate.of(year, 11, 20)) // Consciência Negra
    val easter = computeEasterDate(year)
    list.add(easter.plusDays(-2))  // Sexta-feira Santa
    list.add(easter.plusDays(-48)) // Carnaval (segunda)
    list.add(easter.plusDays(-47)) // Carnaval (terça)
    return list
}

fun getNationalHolidaysInMonth(year: Int, monthIdx: Int): List<LocalDate> =
    getNationalHolidays(year).filter { it.monthIdx() == monthIdx }

fun getSundaysInMonth(year: Int, monthIdx: Int): List<LocalDate> {
    val out = mutableListOf<LocalDate>()
    var d = LocalDate.of(year, monthIdx + 1, 1)
    while (d.monthIdx() == monthIdx) {
        if (d.jsDay() == 0) out.add(d)
        d = d.plusDays(1)
    }
    return out
}

fun isSundayWorked(s: AppState, date: LocalDate) = s.sundaysWorked.contains(date.toString())

/** Enésimo dia útil do mês (o laço do JS deixa o dia "estourar" pro mês seguinte, igual aqui). */
fun getNthBusinessDay(year: Int, monthIdx: Int, n: Int): LocalDate {
    val holidays = getNationalHolidaysInMonth(year, monthIdx).toSet()
    val first = LocalDate.of(year, monthIdx + 1, 1)
    var count = 0
    var day = 1
    var d: LocalDate
    do {
        d = first.plusDays((day - 1).toLong())
        val dow = d.jsDay()
        if (dow != 0 && dow != 6 && !holidays.contains(d)) count++
        day++
    } while (count < n && day < 32)
    return d
}

fun lastBusinessDay(year: Int, monthIdx: Int): LocalDate {
    var d = LocalDate.of(year, monthIdx + 1, 1).plusMonths(1).minusDays(1)
    val hol = getNationalHolidaysInMonth(year, monthIdx).map { it.dayOfMonth }
    while (d.jsDay() == 0 || d.jsDay() == 6 || hol.contains(d.dayOfMonth)) d = d.minusDays(1)
    return d
}

fun taxDueISO(ym: String): String {
    val y = ym.substring(0, 4).toInt()
    val m = ym.substring(5, 7).toInt() - 1
    val nm = m + 1
    return lastBusinessDay(if (nm > 11) y + 1 else y, if (nm > 11) 0 else nm).toString()
}

fun currentShiftForMonth(s: AppState, year: Int, monthIdx: Int): String {
    val inc = s.income
    if (inc.shiftMode == "fixo") return inc.fixedShift
    val base = isoToDate(inc.baseMonthKey + "-01")
    val diffMonths = (year - base.year) * 12 + (monthIdx - base.monthIdx())
    val sameParity = (((diffMonths % 2) + 2) % 2) == 0
    val other = if (inc.baseShift == "manha") "noite" else "manha"
    return if (sameParity) inc.baseShift else other
}

fun parseHM(hm: String?): Int? {
    if (hm.isNullOrEmpty()) return null
    val parts = hm.split(":")
    val h = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
    val m = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
    return (h * 60 + m).toInt()
}

fun computePontoHours(in1: String?, out1: String?, in2: String?, out2: String?): Double {
    var total = 0.0
    val a = parseHM(in1)
    var b = parseHM(out1)
    if (a != null && b != null) {
        if (b <= a) b += 1440
        total += (b - a) / 60.0
    }
    val c = parseHM(in2)
    var d = parseHM(out2)
    if (c != null && d != null) {
        if (d <= c) d += 1440
        total += (d - c) / 60.0
    }
    return round2(total)
}

fun scheduledHoursForShift(s: AppState, shift: String): Double = s.income.shifts[shift]?.hours ?: 7.0

fun pontoExtraHoursForMonth(s: AppState, year: Int, monthIdx: Int): Double {
    var extra = 0.0
    val shift = currentShiftForMonth(s, year, monthIdx)
    val scheduled = scheduledHoursForShift(s, shift)
    s.timeClock.forEach { entry ->
        val d = isoToDate(entry.date)
        if (d.year == year && d.monthIdx() == monthIdx) extra += Math.max(0.0, entry.hours - scheduled)
    }
    return round2(extra)
}

val PONTO_SLOTS = listOf("in1", "out1", "in2", "out2")
val PONTO_SLOT_LABELS = mapOf("in1" to "Entrada", "out1" to "Saída (almoço)", "in2" to "Volta do almoço", "out2" to "Saída")

fun PontoEntry.slot(f: String): String = when (f) { "in1" -> in1; "out1" -> out1; "in2" -> in2; else -> out2 }
fun PontoEntry.setSlot(f: String, v: String) { when (f) { "in1" -> in1 = v; "out1" -> out1 = v; "in2" -> in2 = v; else -> out2 = v } }

fun nextPontoSlot(s: AppState, dateISO: String): String? {
    val e = s.timeClock.find { it.date == dateISO }
    if (e == null || e.in1.isEmpty()) return "in1"
    if (e.out1.isEmpty()) return "out1"
    if (e.in2.isEmpty()) return "in2"
    if (e.out2.isEmpty()) return "out2"
    return null
}

fun savePontoSlot(s: AppState, dateISO: String, field: String, time: String) {
    var entry = s.timeClock.find { it.date == dateISO }
    if (entry == null) {
        entry = PontoEntry(date = dateISO)
        s.timeClock.add(entry)
    }
    entry.setSlot(field, time)
    entry.hours = computePontoHours(entry.in1, entry.out1, entry.in2, entry.out2)
    s.timeClock.sortWith { a, b -> if (a.date < b.date) 1 else -1 }
    while (s.timeClock.size > 500) s.timeClock.removeAt(s.timeClock.size - 1)
}

fun removePontoSlot(s: AppState, dateISO: String, field: String) {
    val entry = s.timeClock.find { it.date == dateISO } ?: return
    entry.setSlot(field, "")
    entry.hours = computePontoHours(entry.in1, entry.out1, entry.in2, entry.out2)
    if (entry.in1.isEmpty() && entry.out1.isEmpty() && entry.in2.isEmpty() && entry.out2.isEmpty()) s.timeClock.remove(entry)
}

fun removeTimeClockEntry(s: AppState, date: String) { s.timeClock.removeAll { it.date == date } }

fun markSundayWorked(s: AppState, iso: String) {
    if (!s.sundaysWorked.contains(iso)) s.sundaysWorked.add(iso)
    s.sundaysWorked.sort()
}
fun removeSundayWorked(s: AppState, iso: String) { s.sundaysWorked.removeAll { it == iso } }

/* ============================================================
   INSS e IRRF — tabelas oficiais de 2026
   (Portaria MPS/MF 13/2026 e Lei 15.270/2025; sem dependentes)
   ============================================================ */
private class InssBracket(val limit: Double, val rate: Double)
private val INSS_BRACKETS_2026 = listOf(
    InssBracket(1621.00, 0.075), InssBracket(2902.84, 0.09), InssBracket(4354.27, 0.12), InssBracket(8475.55, 0.14),
)

fun computeINSS(gross: Double): Double {
    if (gross <= 0) return 0.0
    val capped = Math.min(gross, 8475.55)
    var inss = 0.0
    var prevLimit = 0.0
    for (b in INSS_BRACKETS_2026) {
        if (capped > prevLimit) inss += (Math.min(capped, b.limit) - prevLimit) * b.rate
        prevLimit = b.limit
    }
    return round2(inss)
}

private class IrrfBracket(val limit: Double, val rate: Double, val deduct: Double)
private val IRRF_BRACKETS_2026 = listOf(
    IrrfBracket(2428.80, 0.0, 0.0), IrrfBracket(2826.65, 0.075, 182.16), IrrfBracket(3751.05, 0.15, 394.16),
    IrrfBracket(4664.68, 0.225, 675.49), IrrfBracket(Double.POSITIVE_INFINITY, 0.275, 908.73),
)
const val IRRF_DESCONTO_SIMPLIFICADO = 607.20

fun irrfTabelaTradicional(base: Double): Double {
    if (base <= 0) return 0.0
    val b = IRRF_BRACKETS_2026.first { base <= it.limit }
    return Math.max(0.0, round2(base * b.rate - b.deduct))
}

fun irrfRedutor(rtb: Double): Double {
    if (rtb > 7350) return 0.0
    return Math.max(0.0, round2(978.62 - 0.133145 * rtb))
}

fun computeIRRF(rtb: Double, baseDeducao: Double): Double {
    val base = Math.max(0.0, rtb - Math.max(baseDeducao, IRRF_DESCONTO_SIMPLIFICADO))
    val bruto = irrfTabelaTradicional(base)
    val redutor = irrfRedutor(rtb)
    return Math.max(0.0, round2(bruto - redutor))
}

fun computeHolidayDayValue(s: AppState): Double {
    val inc = s.income
    val hourlyRate = inc.baseSalary / 220
    val nightHours = Math.min(inc.holidayNightHours, inc.holidayHours)
    val dayHours = inc.holidayHours - nightHours
    val nightHoursConverted = nightHours * 1.142857
    val dayValue = dayHours * hourlyRate * (1 + inc.holidayPct / 100)
    val nightValue = nightHoursConverted * hourlyRate * (1 + inc.holidayPct / 100 + inc.holidayNightPct / 100)
    return round2(dayValue + nightValue)
}

class MonthlyIncome(
    val salaryWithExtras: Double, val extraHoursValue: Double, val pontoExtraHours: Double,
    val inssValue: Double, val irrfValue: Double, val holidaysValue: Double, val holidaysCount: Int,
    val sundaysValue: Double, val sundaysCount: Int, val net: Double,
)

fun computeMonthlyIncome(s: AppState, year: Int, monthIdx: Int): MonthlyIncome {
    val inc = s.income
    val hourlyRate = inc.baseSalary / 220
    val pontoExtraHours = pontoExtraHoursForMonth(s, year, monthIdx)
    val extraHoursValue = round2(hourlyRate * pontoExtraHours * (1 + inc.extraHourPct / 100))
    val salaryWithExtras = round2(inc.baseSalary + extraHoursValue)
    val inssValue = computeINSS(salaryWithExtras)
    val irrfValue = computeIRRF(salaryWithExtras, inssValue)

    val holidaysCount = getNationalHolidaysInMonth(year, monthIdx).size
    val holidaysValue = round2(holidaysCount * computeHolidayDayValue(s))

    val sundaysCount = getSundaysInMonth(year, monthIdx).count { isSundayWorked(s, it) }
    val sundaysValue = round2(sundaysCount * inc.sundayBonus)

    val net = round2(salaryWithExtras - inssValue - irrfValue + holidaysValue)
    return MonthlyIncome(salaryWithExtras, extraHoursValue, pontoExtraHours, inssValue, irrfValue, holidaysValue, holidaysCount, sundaysValue, sundaysCount, net)
}

class ThirteenthBreakdown(val gross: Double, val firstInstallment: Double, val secondInstallmentGross: Double, val inss: Double, val ir: Double, val secondInstallmentNet: Double)

fun compute13thBreakdown(s: AppState): ThirteenthBreakdown {
    val gross = round2(s.income.baseSalary)
    val first = round2(gross / 2)
    val secondGross = round2(gross - first)
    val inss = computeINSS(gross)
    val ir = computeIRRF(gross, inss)
    val secondNet = round2(secondGross - inss - ir)
    return ThirteenthBreakdown(gross, first, secondGross, inss, ir, secondNet)
}

class FeriasBreakdown(val umTerco: Double, val grossTotal: Double, val inss: Double, val ir: Double, val net: Double)

fun computeFeriasBreakdown(s: AppState): FeriasBreakdown {
    val base = s.income.baseSalary
    val umTerco = round2(base / 3)
    val grossTotal = round2(base + umTerco)
    val inss = computeINSS(base)
    val ir = computeIRRF(base, inss)
    val net = round2(grossTotal - inss - ir)
    return FeriasBreakdown(umTerco, grossTotal, inss, ir, net)
}
