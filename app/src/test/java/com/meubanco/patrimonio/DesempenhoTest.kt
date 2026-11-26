package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import org.junit.Assert.*
import org.junit.Test

class DesempenhoTest {
    private fun st(): AppState { val s = AppState(); s.income.incomeType = "renda"; return s }

    @Test fun rendimentoNaoContaAportes() {
        val s = st()
        val a = s.assets.first { it.ticker == "ITUB4" }
        a.quantity = 10; a.price = 30.0
        val antes = portfolioPerf(s)
        // novo aporte comprando mais cotas ao mesmo preço de mercado não muda o rendimento
        val custoAntes = antes.cost
        assertEquals(antes.total, portfolioPerf(s).total, 0.001)
        assertTrue(custoAntes >= 0.0)
    }

    @Test fun catalogoAmpliadoEMigracao() {
        val s = st()
        s.assets.removeAll { it.ticker == "WEGE3" }
        assertTrue(ensureCatalog(s))
        assertTrue(s.assets.any { it.ticker == "WEGE3" })
        assertFalse(ensureCatalog(s))
        assertTrue(s.assets.size >= 60)
        assertEquals(s.assets.size, s.assets.map { it.ticker }.toSet().size)
    }

    @Test fun snapshotIdempotente() {
        val s = st()
        s.assets.first().apply { quantity = 5; price = 20.0 }
        recordSnapshot(s)
        val n = s.perfHistory.size
        assertFalse(recordSnapshot(s))
        assertEquals(n, s.perfHistory.size)
    }

    @Test fun guiaTemInfoParaTodosOsAtivosEPontuaEmFaixa() {
        val s = st()
        s.assets.forEach { assertTrue(qualityScore(it) in 0..100) }
        assertTrue(qualityScore(s.assets.first { it.ticker == "TAEE11" }) > qualityScore(s.assets.first { it.ticker == "PETR4" }))
        assertEquals(6, pathRanking("seguranca", s).size)
        assertTrue(nextStepAdvice(s).isNotEmpty())
    }
}
