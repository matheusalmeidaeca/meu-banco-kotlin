package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ExtrasTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()
    private fun base(): AppState = decodeState(AppJson.parseToJsonElement(res("scenario_A_empregado_completo.json")).jsonObject["expected"]!!.toString())

    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 17) }; AppClock.nowMs = { 1_795_000_000_000L } }

    @Test fun pinNovoEAntigo() {
        val h = hashPin("4321")
        assertTrue(h.startsWith("v2:"))
        assertTrue(verifyPin("4321", h)); assertFalse(verifyPin("1234", h))
        assertTrue(verifyPin("1234", simpleHash("1234"))); assertFalse(verifyPin("0000", simpleHash("1234")))
        assertNotEquals(hashPin("4321"), hashPin("4321")) // sal diferente
    }

    @Test fun csvBrasileiro() {
        val csv = "Data;Descrição;Valor\n05/11/2026;MERCADO CENTRAL;-152,30\n06/11/2026;PIX RECEBIDO JOAO;1.200,00\n"
        val it = parseStatement(csv)
        assertEquals(2, it.size)
        assertEquals("2026-11-05", it[0].dateISO); assertEquals(-152.30, it[0].amount, 1e-9)
        assertEquals(1200.0, it[1].amount, 1e-9)
    }

    @Test fun ofx() {
        val ofx = "<OFX><BANKTRANLIST><STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261103120000<TRNAMT>-45.90<MEMO>FARMACIA X</STMTTRN>" +
            "<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261104<TRNAMT>300.00<MEMO>TED</STMTTRN></BANKTRANLIST></OFX>"
        val it = parseStatement(ofx)
        assertEquals(2, it.size)
        assertEquals("2026-11-03", it[0].dateISO); assertEquals(-45.90, it[0].amount, 1e-9)
        assertEquals("Saúde", guessCategory(it[0].desc))
    }

    @Test fun importacaoNaoDuplica() {
        val s = base(); val cash0 = s.cash
        val items = listOf(StmtItem("2026-11-01", "TESTE", -10.0), StmtItem("2026-11-02", "SALARIO X", 100.0))
        val r1 = applyStatement(s, items); val r2 = applyStatement(s, items)
        assertEquals(2, r1.added); assertEquals(0, r2.added); assertEquals(2, r2.skipped)
        assertEquals(cash0 + 90.0, s.cash, 1e-9)
    }

    @Test fun projecaoNaoMudaOEstado() {
        val s = base(); val antes = encodeState(s)
        val p = project(s, 30)
        assertEquals(antes, encodeState(s))
        assertEquals(s.cash, p.startCash, 0.0)
        // a projeção bate com simular de verdade
        val c = cloneState(s); repeat(30) { simulateOneDay(c) }
        assertEquals(c.cash, p.endCash, 1e-9)
    }

    @Test fun assinaturaCobraUmaVezPorMes() {
        val s = base(); s.cash = 1000.0; s.subscriptions.clear()
        addSubscription(s, "Streaming", 30.0, 10, "Lazer")
        repeat(70) { simulateOneDay(s) }
        val charges = s.history.count { it.label == "Assinatura — Streaming" }
        assertTrue("cobranças=$charges", charges in 2..3)
        assertTrue(s.subscriptions[0].lastChargedYM != null)
    }

    @Test fun orcamentoAvisaUmaVez() {
        val s = base(); val ym = ymKey(s.simDateISO)
        s.budgets["Lazer"] = 100.0
        s.monthlyStats.getOrPut(ym) { MonthStat() }.cats["Lazer"] = 95.0
        assertEquals(1, newBudgetAlerts(s).size)
        assertEquals(0, newBudgetAlerts(s).size)
        assertTrue(budgetInsights(s).isNotEmpty())
    }

    @Test fun independenciaFinanceira() {
        val s = base()
        val f = fiStatus(s)
        assertTrue(f.target >= 0)
        s.fiTargetMonthly = 1.0
        s.assets.first().quantity = 1000
        assertEquals(0, fiStatus(s).monthsToGoal)
    }

    @Test fun backupAntigoSemCamposNovosAbre() {
        val s = base()
        assertTrue(s.budgets.isEmpty() && s.subscriptions.isEmpty())
        val back = decodeState(encodeState(s))
        assertEquals(s.cash, back.cash, 0.0)
    }
}
