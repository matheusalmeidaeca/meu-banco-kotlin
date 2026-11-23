package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class LabTest {
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 20) }; AppClock.nowMs = { 1_795_000_000_000L } }
    private fun base(): AppState { val s = AppState(); s.reserves.forEach { it.balance = 0.0 }; s.leftover = 0.0; s.assets = mutableListOf(); s.expenses = mutableListOf(); s.debts = mutableListOf(); s.subscriptions = mutableListOf(); return s }

    @Test fun aporteNecessario() {
        assertEquals(100.0, requiredMonthly(0.0, 1200.0, 12, 0.0)!!, 0.001)
        assertTrue(requiredMonthly(0.0, 1200.0, 12, 12.0)!! < 100.0)
        assertEquals(0.0, requiredMonthly(2000.0, 1200.0, 12, 0.0)!!, 0.001)
        assertNull(requiredMonthly(0.0, 1200.0, 0, 0.0))
    }
    @Test fun diaZero() {
        val s = base(); s.expenses.add(Expense(1, "Aluguel", 1000.0, "Moradia")); s.reserves.first().balance = 3000.0
        val r = runway(s)
        assertEquals(3.0, r.months!!, 0.001); assertEquals(3, r.levels.count { it.reached }); assertNotNull(r.zeroDateISO)
        assertNull(runway(base()).months)
    }
    @Test fun arredondaGastoParaCaixinha() {
        val s = base(); s.cash = 1000.0; val id = s.reserves.first().id
        s.settings.autoRoundStep = 5; s.settings.autoRoundTarget = "reserve:$id"
        spendFree(s, 22.0, "Outro")
        assertEquals(975.0, s.cash, 0.001); assertEquals(3.0, s.reserves.first().balance, 0.001)
        spendFree(s, 20.0, "Outro")   // já é múltiplo de 5: não guarda nada
        assertEquals(955.0, s.cash, 0.001)
    }
    @Test fun guardaPartedoQueEntra() {
        val s = base(); val id = s.reserves.first().id
        s.settings.autoSavePct = 10.0; s.settings.autoSaveTarget = "reserve:$id"
        receivePix(s, "Maria", 200.0)
        assertEquals(180.0, s.cash, 0.001); assertEquals(20.0, s.reserves.first().balance, 0.001)
    }
    @Test fun semRegrasNadaMuda() {
        val s = base(); s.cash = 100.0; spendFree(s, 22.0, "Outro"); receivePix(s, "A", 10.0)
        assertEquals(88.0, s.cash, 0.001)
    }
    @Test fun aporteGuiado() {
        val s = base(); s.assets = mutableListOf(Asset(id = "a", ticker = "AAAA3", category = "acao", price = 10.0, quantity = 100))
        val adv = rebalanceAdvice(s, 1000.0, "conservador")
        assertEquals(1000.0, adv.sumOf { it.amount }, 0.05); assertEquals("rf", adv.first().cls)
        assertTrue(adv.none { it.cls == "acao" })   // ações já passam da meta
        assertTrue(rebalanceAdvice(s, 0.0).isEmpty())
    }
    @Test fun estresse() {
        val s = base(); s.assets = mutableListOf(Asset(id = "a", ticker = "AAAA3", category = "acao", price = 10.0, quantity = 100, yieldPct = 1.0))
        val r = stressTest(s).first { it.label == "Queda forte" }
        assertEquals(-250.0, r.delta, 0.001); assertEquals(r.incomeBefore * 0.9, r.incomeAfter, 0.001)
    }
    @Test fun concentracao() {
        val s = base(); s.assets = mutableListOf(Asset(id = "a", ticker = "AAAA3", category = "acao", price = 10.0, quantity = 100))
        val c = concentration(s)!!
        assertEquals(100.0, c.topAssetPct, 0.001); assertTrue(c.score < 50); assertTrue(c.alerts.isNotEmpty())
        assertNull(concentration(base()))
    }
}
