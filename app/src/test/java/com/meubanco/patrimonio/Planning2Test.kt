package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class Planning2Test {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.bufferedReader().readText()
    private fun base() = decodeState(AppJson.parseToJsonElement(res("scenario_A_empregado_completo.json")).jsonObject["expected"]!!.toString())
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 17) }; AppClock.nowMs = { 1_795_000_000_000L } }

    @Test fun pmtConfere() {
        assertEquals(1000.0 / 10, pmt(1000.0, 0.0, 10), 1e-9)
        assertEquals(1000.0 * 0.01 / (1 - Math.pow(1.01, -12.0)), pmt(1000.0, 1.0, 12), 1e-9)
        val o = loanOffer(1000.0, 2.0, 12); assertTrue(o.total > 1000.0 && o.interest > 0); assertEquals(26.8, o.annualPct, 0.1)
    }

    @Test fun negociacaoMostraEconomia() {
        val n = negotiation(5000.0, 6.0, 24, 3000.0)
        assertTrue(n.keepTotal > 5000.0); assertEquals(n.keepTotal - 3000.0, n.savings, 0.01); assertEquals(40.0, n.discountPct, 1e-9)
    }

    @Test fun comparadorOrdenaELiquidaIR() {
        val r = compareInvest(10000.0, 24, 14.9, 100.0)
        assertEquals("Poupança", r.last().name)
        val cdb = r.first { it.name.startsWith("CDB") }
        assertEquals((cdb.gross - 10000.0) * 0.15, cdb.tax, 0.02) // > 720 dias: 15%
        assertEquals(0.0, r.first { it.name == "Poupança" }.tax, 0.0)
        assertEquals(irRateForDays(100), 0.225, 0.0); assertEquals(irRateForDays(200), 0.20, 0.0)
    }

    @Test fun rendaVariavel() {
        val s = base(); s.simDateISO = "2026-11-17"; s.monthlyStats.clear()
        assertNull(variableIncome(s))
        s.monthlyStats["2026-06"] = MonthStat(income = 3000.0); s.monthlyStats["2026-07"] = MonthStat(income = 5000.0)
        s.monthlyStats["2026-08"] = MonthStat(income = 2000.0); s.monthlyStats["2026-11"] = MonthStat(income = 9999.0)
        val v = variableIncome(s)!!
        assertEquals(3, v.months); assertEquals(3333.33, v.avg, 0.01); assertEquals(2000.0, v.min, 0.0); assertEquals(5000.0, v.max, 0.0)
        assertEquals(3333.33, v.baseline, 0.01) // média das 3 menores = todas
    }

    @Test fun metaComPrazo() {
        val g = Goal(1, "Viagem", "outro", 1200.0, 0.0, 0.0, 0.0, false, null, "2027-11")
        assertEquals(100.0, goalMonthlyNeeded(g, "2026-11-17")!!, 0.01)
        g.saved = 1200.0; assertEquals(0.0, goalMonthlyNeeded(g, "2026-11-17")!!, 0.0)
        g.deadlineYM = "2026-10"; assertNull(goalMonthlyNeeded(g, "2026-11-17"))
        assertEquals("2027-02", addMonthsYM("2026-11-17", 3))
        g.deadlineYM = "2027-11"; g.saved = 0.0; g.rate = 12.0
        assertTrue(goalMonthlyNeeded(g, "2026-11-17")!! < 100.0) // juros reduzem o aporte
    }

    @Test fun pixRisco() {
        val s = base(); s.cash = 1000.0; s.history.clear()
        assertTrue(pixRiskFlags(s, "X", 900.0, 12).any { "80%" in it })
        assertTrue(pixRiskFlags(s, "X", 10.0, 2).any { "madrugada" in it })
        assertTrue(pixRiskFlags(s, "X", 10.0, 12).isEmpty())
        repeat(3) { s.history.add(HistoryEntry("2026-10-0${it + 1}", "pix", "Pix enviado para A", -20.0)) }
        assertTrue(pixRiskFlags(s, "X", 100.0, 12).any { "típico" in it })
    }

    @Test fun dividirContas() {
        val s = base(); s.sharedBills.clear()
        s.sharedBills.add(SharedBill(1, "Mercado", 200.0, true, 50.0))
        s.sharedBills.add(SharedBill(2, "Luz", 100.0, false, 50.0))
        assertEquals(50.0, sharedBalance(s), 0.01) // parceiro deve 100, eu devo 50
        s.sharedBills[0].settled = true; assertEquals(-50.0, sharedBalance(s), 0.01)
        assertTrue(sharedShareText(s).contains("Luz"))
    }

    @Test fun resumoIr() {
        val s = base(); s.monthlyStats.clear(); s.dividendBreakdown.clear()
        s.monthlyStats["2026-03"] = MonthStat(spent = 100.0, income = 1000.0, cats = mutableMapOf("Saúde" to 40.0))
        s.monthlyStats["2025-12"] = MonthStat(income = 999.0)
        s.dividendBreakdown["2026-05"] = mutableMapOf("ITSA4" to 12.0, "BBAS3" to 30.0)
        val r = irSummary(s, 2026)
        assertEquals(1000.0, r.income, 0.0); assertEquals(40.0, r.health, 0.0); assertEquals(42.0, r.dividends, 0.0)
        assertEquals("BBAS3", r.dividendsByTicker.first().first)
        assertTrue(irShareText(r).contains("2026"))
    }

    @Test fun estadoAntigoSemNovosCamposAindaLe() {
        val s = base(); val js = encodeState(s)
        val o = AppJson.parseToJsonElement(js).jsonObject.toMutableMap(); o.remove("sharedBills"); o.remove("partnerName")
        val s2 = decodeState(JsonObject(o).toString()); assertTrue(s2.sharedBills.isEmpty()); assertEquals("Parceiro(a)", s2.partnerName)
    }
}
