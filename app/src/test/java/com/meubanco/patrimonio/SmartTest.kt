package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import org.junit.Assert.*
import org.junit.Test

class SmartTest {
    private fun tlv(id: String, v: String) = id + "%02d".format(v.length) + v
    private fun br(amount: String?, crcOk: Boolean = true): String {
        val acct = tlv("00", "br.gov.bcb.pix") + tlv("01", "fulano@email.com")
        val body = tlv("00", "01") + tlv("26", acct) + tlv("52", "0000") + tlv("53", "986") + (amount?.let { tlv("54", it) } ?: "") +
            tlv("58", "BR") + tlv("59", "FULANO DE TAL") + tlv("60", "BELEM") + tlv("62", tlv("05", "***")) + "6304"
        val crc = pixCrc16(body)
        return body + if (crcOk) crc else "0000"
    }
    @Test fun crcConhecido() { assertEquals("29B1", pixCrc16("123456789")) }
    @Test fun pixValido() {
        val p = parsePixBr(br("25.90"))!!
        assertTrue(p.valid); assertEquals(25.90, p.amount!!, 0.001); assertEquals("FULANO DE TAL", p.name); assertEquals("BELEM", p.city); assertEquals("fulano@email.com", p.key)
    }
    @Test fun pixSemValorEComCrcErrado() {
        assertNull(parsePixBr(br(null))!!.amount)
        assertFalse(parsePixBr(br("10.00", crcOk = false))!!.valid)
        assertNull(parsePixBr("qualquer coisa")); assertNull(parsePixBr("000201xyz"))
    }
    private val cats = listOf("Lazer", "Imprevisto", "Presente", "Outro")
    @Test fun fraseGasto() {
        val e = parseSmartSentence("gastei 25,90 no mercado", cats)!!
        assertEquals("gasto", e.kind); assertEquals(25.90, e.amount, 0.001); assertEquals("Mercado", e.who); assertEquals("Outro", e.category)
    }
    @Test fun fraseEntradaEMilhar() {
        val e = parseSmartSentence("Recebi 1.200 de João", cats)!!
        assertEquals("entrada", e.kind); assertEquals(1200.0, e.amount, 0.001); assertEquals("João", e.who)
    }
    @Test fun frasePixECategoria() {
        val e = parseSmartSentence("mandei 50 reais pix para Ana", cats)!!
        assertEquals("pix", e.kind); assertEquals(50.0, e.amount, 0.001); assertEquals("Ana", e.who)
        assertEquals("Lazer", parseSmartSentence("paguei 40 no cinema", cats)!!.category)
        assertNull(parseSmartSentence("bom dia", cats)); assertNull(parseSmartSentence("25 reais", cats))
    }
    @Test fun pontoDePartida() {
        val d = OnboardingDraft(incomeType = "empregado", baseSalary = 2000.0, wantExpenses = true, reserveStart = 1000.0, hasDebts = true)
        d.expenses = mutableListOf(ObExpense("Aluguel", 800.0, "Outros"), ObExpense("Luz", 200.0, "Outros")); d.debts = mutableListOf(ObDebt("Cartão", "cartao", 500.0))
        val i = onboardingInsights(d)
        assertEquals(1000.0, i.fixedTotal, 0.001); assertEquals(50.0, i.fixedPct!!, 0.001); assertEquals(1.0, i.reserveMonths!!, 0.001); assertEquals(6000.0, i.reserveTarget, 0.001); assertEquals(500.0, i.debtTotal, 0.001)
        assertTrue(i.tips.size >= 2)
    }
}
