package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class PlanningTest {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.bufferedReader().readText()
    private fun base() = decodeState(AppJson.parseToJsonElement(res("scenario_A_empregado_completo.json")).jsonObject["expected"]!!.toString())
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 17) }; AppClock.nowMs = { 1_795_000_000_000L } }

    @Test fun avalancheGastaMenosJurosQueBolaDeNeve() {
        val d = listOf(Debt(1, "Cartão", "cartao", 2000.0, 12.0, 100.0), Debt(2, "Empréstimo", "emprestimo", 800.0, 3.0, 60.0))
        val a = debtPlan(d, 150.0, "avalanche"); val s = debtPlan(d, 150.0, "snowball")
        assertNotNull("a=${a.months}", a.months) // com 12%/mês a bola de neve pode nunca quitar; a avalanche quita
        if (s.months != null) assertTrue("${a.totalInterest} vs ${s.totalInterest}", a.totalInterest <= s.totalInterest + 0.01)
        assertEquals(a.payoffOrder.toString(), 2, a.payoffOrder.size)
        // pagando mais, termina antes
        assertTrue(debtPlan(d, 400.0, "avalanche").months!! < a.months!!)
    }

    @Test fun minimoMenorQueJurosNuncaQuita() {
        val d = listOf(Debt(1, "Rotativo", "cartao", 5000.0, 14.0, 50.0))
        assertNull(debtPlan(d, 0.0, "avalanche").months)
        assertNotNull(debtPlan(d, 800.0, "avalanche").months)
    }

    @Test fun semDividasZeroMeses() { assertEquals(0, debtPlan(emptyList(), 0.0, "snowball").months) }

    @Test fun saudeFinanceiraEntre0e100() {
        val s = base()
        val h = financialHealth(s)
        assertTrue(h.score in 0..100); assertEquals(5, h.parts.size)
        assertEquals(h.score, h.parts.sumOf { it.points })
        s.debts.add(Debt(1, "X", "cartao", 100000.0, 10.0, 5000.0))
        assertTrue(financialHealth(s).score <= h.score)
    }

    @Test fun reservaEmMeses() {
        val s = base(); s.expenses.clear(); s.subscriptions.clear(); s.expenses.add(Expense(1, "Aluguel", 1000.0, "Moradia"))
        s.reserves.forEach { it.balance = 0.0 }; s.reserves[0].balance = 3000.0
        assertEquals(3.0, emergencyMonthsCovered(s), 1e-9)
        assertEquals(6000.0, emergencyTarget(s, 6.0), 1e-9)
    }

    @Test fun regra503020Soma() {
        val s = base(); val ym = ymKey(s.simDateISO)
        val st = s.monthlyStats.getOrPut(ym) { MonthStat() }
        st.cats.clear(); st.cats["Moradia"] = 500.0; st.cats["Lazer"] = 200.0; st.invested = 100.0; st.income = 1000.0
        val r = rule503020(s)
        assertEquals(500.0, r.needs, 1e-9); assertEquals(200.0, r.wants, 1e-9); assertEquals(100.0, r.future, 1e-9)
    }

    @Test fun jurosCompostos() {
        assertEquals(1000.0 + 12 * 100.0, futureValue(1000.0, 100.0, 0.0, 1), 1e-6)
        assertTrue(futureValue(0.0, 100.0, 12.0, 10) > 12000.0)
    }

    @Test fun pixERecentes() {
        val s = base(); s.cash = 1000.0
        executeSendPix(s, "Maria", 10.0); executeSendPix(s, "João", 5.0); executeSendPix(s, "Maria", 1.0)
        assertEquals(listOf("Maria", "João"), pixRecents(s))
        assertFalse(pixIsNewRecipient(s, "Maria")); assertTrue(pixIsNewRecipient(s, "Zé"))
    }

    @Test fun csvEcompartilhar() {
        val s = base()
        val csv = historyToCsv(s.history.take(3))
        assertTrue(csv.startsWith("Data;Tipo;Descrição;Valor;Categoria")); assertEquals(4, csv.trim().lines().size)
        addShopItem(s, "Arroz", "Alimentação", 20.0, 2)
        assertTrue(shoppingShareText(s).contains("2x Arroz"))
    }

    @Test fun cadastroNovoTemPassosExtras() {
        val d = newOnboardingDraft(AppState.novo())
        d.hasDebts = true
        val steps = onboardingSteps(d)
        assertTrue(steps.containsAll(listOf("goal", "reserve", "hasDebts", "debts", "security", "summary", "done")))
        val s = AppState.novo(); d.mainGoal = "dividas"; d.reserveStart = 500.0; d.debts.add(ObDebt("Cartão", "cartao", 1000.0, 10.0, 100.0)); d.cash = 50.0
        finishOnboarding(s, d)
        assertEquals(1, s.debts.size); assertEquals("dividas", s.mainGoal); assertEquals(500.0, s.reserves[0].balance, 1e-9)
    }
}
