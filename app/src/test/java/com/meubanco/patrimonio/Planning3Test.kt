package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class Planning3Test {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.bufferedReader().readText()
    private fun base() = decodeState(AppJson.parseToJsonElement(res("scenario_A_empregado_completo.json")).jsonObject["expected"]!!.toString()).also {
        it.simDateISO = "2026-05-10"; it.contribByMonth.clear(); it.divByMonth.clear(); it.history.clear()
    }
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 5, 10) }; AppClock.nowMs = { 1_795_000_000_000L } }

    @Test fun mediaContaMesesZerados() {
        val s = base()
        s.contribByMonth["2026-01"] = 100.0; s.contribByMonth["2026-03"] = 300.0; s.contribByMonth["2026-05"] = 9999.0 // mês atual não entra
        val c = contributionStats(s)!!
        assertEquals(4, c.months); assertEquals(2, c.monthsWithContribution); assertEquals(400.0, c.total, 0.0); assertEquals(100.0, c.avg, 0.0)
        assertEquals("2026-01", c.firstYM)
    }

    @Test fun semAporteNaoHaMedia() { assertNull(contributionStats(base())) }

    @Test fun primeiroMesSoNoMesAtualUsaOAtual() {
        val s = base(); s.contribByMonth["2026-05"] = 500.0
        val c = contributionStats(s)!!; assertEquals(1, c.months); assertEquals(500.0, c.avg, 0.0)
    }

    @Test fun rendimentoMedioInclueMesesSemDividendo() {
        val s = base()
        s.contribByMonth["2026-01"] = 1000.0; s.contribByMonth["2026-02"] = 1000.0
        s.divByMonth["2026-02"] = 10.0 // base do mês 2 = 1000 ; mês 3 e 4: base 2000, sem dividendos
        val d = dividendStats(s)
        // num = 10, den = (mês1: base 0 ignorado) 1000 + 2000 + 2000 = 5000 -> 0,2%/mês
        assertEquals(0.2 * 12, d.annualYieldPct, 1e-9); assertTrue(d.fromHistory)
        assertEquals(10.0 / 4, d.avgMonthly, 1e-9)
    }

    @Test fun simulacaoSemJurosAcumulaLinear() {
        val p = InvestParams(0.0, 100.0, 0.0, 0.0)
        assertEquals(10, patrimonyGoal(p, 1000.0, false).months)
        assertEquals(1000.0, patrimonyGoal(p, 1000.0, false).balance, 0.01)
    }

    @Test fun jurosCompostosAceleram() {
        val sem = patrimonyGoal(InvestParams(0.0, 500.0, 0.0, 0.0), 250_000.0, false).months!!
        val com = patrimonyGoal(InvestParams(0.0, 500.0, 10.0, 0.0), 250_000.0, false).months!!
        assertEquals(500, sem); assertTrue("com=$com", com < 250 && com > 150)
        val sim = patrimonyGoal(InvestParams(0.0, 500.0, 10.0, 0.0), 250_000.0, false)
        assertTrue(sim.gains > sim.contributed) // os juros passam os aportes
    }

    @Test fun inflacaoAlongaOPrazo() {
        val a = patrimonyGoal(InvestParams(10_000.0, 500.0, 8.0, 5.0, true, 0.0), 250_000.0, true).months!!
        val b = patrimonyGoal(InvestParams(10_000.0, 500.0, 8.0, 5.0, true, 4.5), 250_000.0, true).months!!
        assertTrue("$a $b", b > a)
    }

    @Test fun metaDeRendaPorDividendos() {
        // 12% a.a. de yield = 1%/mês: R$ 10.000 já pagam R$ 100/mês
        assertEquals(0, incomeGoal(InvestParams(10_000.0, 0.0, 0.0, 12.0), 100.0, false).months)
        assertEquals(1_000.0 * 12 / 0.12 * 1.0, requiredPatrimony(1000.0, 12.0)!!, 1e-6 * 100000) // 100.000
        val r = incomeGoal(InvestParams(0.0, 1000.0, 0.0, 12.0, true), 100.0, false)
        assertNotNull(r.months); assertTrue(r.months!! in 9..10)
        assertNull(incomeGoal(InvestParams(1000.0, 100.0, 5.0, 0.0), 100.0, false).months) // sem dividendos nunca chega
        assertNull(requiredPatrimony(100.0, 0.0))
    }

    @Test fun naoReinvestirChegaMaisTarde() {
        val com = patrimonyGoal(InvestParams(50_000.0, 500.0, 5.0, 6.0, true), 150_000.0, false).months!!
        val sem = patrimonyGoal(InvestParams(50_000.0, 500.0, 5.0, 6.0, false), 150_000.0, false).months!!
        assertTrue("$com $sem", sem > com)
    }

    @Test fun aporteCrescenteAntecipa() {
        val fixo = patrimonyGoal(InvestParams(0.0, 500.0, 6.0, 0.0, true, 0.0, 0.0), 100_000.0, false).months!!
        val cresc = patrimonyGoal(InvestParams(0.0, 500.0, 6.0, 0.0, true, 0.0, 10.0), 100_000.0, false).months!!
        assertTrue(cresc < fixo)
    }

    @Test fun registraFluxosPeloHistorico() {
        val s = base()
        addHistory(s, "aporte", "Aporte recorrente", -300.0)
        addHistory(s, "transferencia", "Transferência para a conta de investimentos", -200.0)
        addHistory(s, "transferencia", "Transferência da conta de investimentos para a principal", 50.0)
        addHistory(s, "dividendo", "Dividendos recebidos", 12.5)
        addHistory(s, "meta", "Aporte extra na meta", -999.0) // metas e reservas não contam
        addHistory(s, "cdb", "Aporte na reserva", -999.0)
        assertEquals(450.0, s.contribByMonth["2026-05"]!!, 0.0); assertEquals(12.5, s.divByMonth["2026-05"]!!, 0.0)
    }

    @Test fun migracaoReconstroiDoHistorico() {
        val s = base(); s.flowsMigrated = false
        s.history.add(HistoryEntry("2026-02-03", "aporte", "x", -100.0)); s.history.add(HistoryEntry("2026-02-20", "dividendo", "d", 5.0))
        val back = decodeState(encodeState(s))
        assertEquals(100.0, back.contribByMonth["2026-02"]!!, 0.0); assertEquals(5.0, back.divByMonth["2026-02"]!!, 0.0); assertTrue(back.flowsMigrated)
        // segunda leitura não duplica
        val again = decodeState(encodeState(back)); assertEquals(100.0, again.contribByMonth["2026-02"]!!, 0.0)
    }

    @Test fun parametrosUsamDadosReaisEPermitemAjuste() {
        val s = base(); s.contribByMonth["2026-02"] = 400.0; s.contribByMonth["2026-04"] = 200.0 // 3 meses fechados (fev-abr): média 200
        assertEquals(200.0, investParams(s).monthly, 0.0)
        s.investContribOverride = 350.0; assertEquals(350.0, investParams(s).monthly, 0.0)
        assertEquals(350.0 + 100.0, investParams(s, extraMonthly = 100.0).monthly, 0.0)
        s.investGrowthPct = 8.0; assertEquals(5.0, investParams(s, growthDelta = -3.0).growthPct, 1e-9)
    }

    @Test fun rotuloDePrazo() { assertEquals("2 ano(s) e 3 mês(es)", monthsLabel(27)); assertEquals("agora", monthsLabel(0)); assertEquals("5 mês(es)", monthsLabel(5)) }

    private fun asset(t: String, q: Int, price: Double, v: Double?, m: Int?) = Asset(id = t, ticker = t, quantity = q, price = price, valAnnualPct = v, valMonths = m)

    @Test fun valorizacaoPonderadaPelaPosicao() {
        val s = base(); s.assets.clear()
        s.assets.add(asset("A", 100, 10.0, 10.0, 60)); s.assets.add(asset("B", 100, 30.0, 2.0, 36)); s.assets.add(asset("C", 0, 10.0, 50.0, 24))
        val g = portfolioGrowth(s)!!
        assertEquals((1000 * 10.0 + 3000 * 2.0) / 4000, g.pct, 0.001); assertEquals(2, g.assets); assertEquals(36, g.months); assertFalse(g.clamped)
        assertEquals(g.pct, growthBasePct(s), 0.0)
        s.investGrowthPct = 7.0; assertEquals(7.0, growthBasePct(s), 0.0)
    }

    @Test fun valorizacaoLimitadaESemDado() {
        val s = base(); s.assets.clear()
        s.assets.add(asset("A", 10, 10.0, 40.0, 60))
        val g = portfolioGrowth(s)!!; assertEquals(15.0, g.pct, 0.0); assertTrue(g.clamped)
        s.assets.clear(); s.assets.add(asset("B", 10, 10.0, null, null))
        assertNull(portfolioGrowth(s)); assertEquals(s.market.simGrowthPct, growthBasePct(s), 0.0)
        s.assets.add(asset("D", 0, 10.0, 8.0, 24)); assertEquals(8.0, portfolioGrowth(s)!!.pct, 0.0) // sem posição: média dos ativos com dado
    }

    @Test fun inflacaoPrioridade() {
        val s = base()
        assertEquals(4.5, inflationPct(s), 0.0)
        s.market.ipcaPct = 4.63; assertEquals(4.63, inflationPct(s), 0.0); assertEquals(4.63, investParams(s).inflationPct, 0.0)
        s.investInflationOverride = 6.0; assertEquals(6.0, inflationPct(s), 0.0)
        assertEquals(0.0, investParams(s, withInflation = false).inflationPct, 0.0)
    }

    @Test fun ativoComNovosCamposLeEstadoAntigo() {
        val s = base(); val js = encodeState(s)
        val o = AppJson.parseToJsonElement(js).jsonObject.toMutableMap(); o.remove("investInflationOverride")
        val s2 = decodeState(JsonObject(o).toString()); assertNull(s2.investInflationOverride); assertNull(s2.market.ipcaPct)
    }
}
