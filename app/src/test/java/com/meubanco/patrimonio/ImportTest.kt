package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ImportTest {
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 20) }; AppClock.nowMs = { 1_795_000_000_000L } }
    private val ofx = """
OFXHEADER:100
<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261105120000[-3:BRT]<TRNAMT>-45,90<FITID>A1<MEMO>COMPRA MERCADO BOM PRECO 1234</STMTTRN>
<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261106<TRNAMT>1500.00<FITID>A2<MEMO>PIX RECEBIDO JOAO</STMTTRN>
</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"""
    private val csv = "Data;Descrição;Valor\n05/11/2026;Uber *Trip;-23,50\n05/11/2026;Uber *Trip;-23,50\n06/11/2026;Salário;3.200,00\n"

    @Test fun valores() {
        assertEquals(-1234.56, bkAmount("-1.234,56")!!, 0.001); assertEquals(1234.56, bkAmount("1,234.56")!!, 0.001)
        assertEquals(-10.0, bkAmount("(10,00)")!!, 0.001); assertEquals(1500.0, bkAmount("1500.00")!!, 0.001)
        assertNull(bkAmount("abc"))
    }
    @Test fun datas() { assertEquals("2026-11-05", bkDate("05/11/2026")); assertEquals("2026-11-05", bkDate("20261105120000[-3:BRT]")); assertEquals("2026-11-05", bkDate("2026-11-05 10:00")) }
    @Test fun leOfx() {
        val r = parseBankFile(ofx); assertEquals(2, r.size)
        assertEquals(-45.9, r[0].amount, 0.001); assertEquals("2026-11-05", r[0].date); assertEquals("A1", r[0].fitId); assertTrue(r[0].desc.contains("MERCADO"))
    }
    @Test fun leCsv() { val r = parseBankFile(csv); assertEquals(3, r.size); assertEquals(3200.0, r[2].amount, 0.001) }
    @Test fun categoriaPorPalavraChave() {
        val s = AppState(); assertEquals("Mercado", suggestCategory(s, "COMPRA MERCADO BOM PRECO 1234")); assertEquals("Transporte", suggestCategory(s, "Uber *Trip"))
    }
    @Test fun aprendeComCorrecao() {
        val s = AppState(); s.cash = 0.0
        val items = prepareImport(s, parseBankFile(csv)); setCategoryForMerchant(items, items.first { it.row.desc.startsWith("Uber") }, "Trabalho")
        applyImport(s, items, true)
        assertEquals("Trabalho", suggestCategory(s, "UBER *TRIP 9988"))
    }
    @Test fun semDuplicarNaSegundaVez() {
        val s = AppState(); s.cash = 0.0
        val r1 = applyImport(s, prepareImport(s, parseBankFile(csv)), true)
        assertEquals(2, r1.expenses); assertEquals(1, r1.incomes)       // duas compras iguais no mesmo dia continuam sendo duas
        assertEquals(3200.0 - 47.0, s.cash, 0.001)
        val again = prepareImport(s, parseBankFile(csv)); assertTrue(again.all { it.duplicate })
        val r2 = applyImport(s, again, true); assertEquals(0, r2.expenses + r2.incomes); assertEquals(3200.0 - 47.0, s.cash, 0.001)
    }
    @Test fun ofxPorFitId() {
        val s = AppState(); s.cash = 0.0
        applyImport(s, prepareImport(s, parseBankFile(ofx)), false)
        assertEquals(0.0, s.cash, 0.001)                                  // "só registrar" não mexe no saldo
        assertTrue(prepareImport(s, parseBankFile(ofx)).all { it.duplicate })
    }
    @Test fun inverterSinais() { val s = AppState(); assertTrue(prepareImport(s, parseBankFile(csv), invert = true)[2].isExpense) }
    @Test fun saudeDosDados() {
        val s = AppState(); assertTrue(dataHealth(s).none { it.severe })
        s.cash = -5.0; s.settings.autoSaveTarget = "reserve:999"
        val h = dataHealth(s); assertTrue(h.any { it.severe }); assertTrue(h.any { !it.severe })
    }
}

class CardWeekTest {
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 22) }; AppClock.nowMs = { 1_795_000_000_000L } }
    private val fatura = "Data,Descrição,Valor\n10/11/2026,Padaria Central,18.50\n12/11/2026,Uber *Trip,32.00\n15/11/2026,Pagamento recebido,-500.00\n"

    @Test fun faturaPositivaViraCompra() {
        val p = cardPurchases(parseBankFile(fatura)); assertEquals(2, p.size); assertTrue(p.all { it.amount < 0 })
    }
    @Test fun importaFaturaSemTocarNoSaldo() {
        val s = AppState(); s.cash = 100.0; val c = s.card; c.limit = 1000.0; val inv0 = c.invoiceCurrent
        val r = applyCardImport(s, c, prepareImport(s, cardPurchases(parseBankFile(fatura))))
        assertEquals(2, r.added); assertEquals(50.5, r.total, 0.001); assertEquals(100.0, s.cash, 0.001); assertEquals(inv0 + 50.5, c.invoiceCurrent, 0.001)
        assertEquals("2026-11-10", c.purchases.first { it.desc.startsWith("Padaria") }.dateISO)
        val again = applyCardImport(s, c, prepareImport(s, cardPurchases(parseBankFile(fatura)))); assertEquals(0, again.added)
    }
    @Test fun respeitaLimite() {
        val s = AppState(); val c = s.card; c.limit = 20.0
        val r = applyCardImport(s, c, prepareImport(s, cardPurchases(parseBankFile(fatura)))); assertEquals(1, r.added); assertEquals(1, r.skipped)
    }
    @Test fun resumoSemanal() {
        val s = AppState(); s.cash = 1000.0
        spendFree(s, 30.0, "Lazer"); receivePix(s, "Ana", 200.0)
        s.history.add(0, HistoryEntry("2026-10-01", "gastolivre", "antigo", -999.0, null, "Lazer"))   // fora da janela
        val w = weekSummary(s, s.simDateISO)
        assertEquals(30.0, w.spent, 0.001); assertEquals(200.0, w.received, 0.001); assertEquals("Lazer", w.topCategory)
        assertTrue(w.text().contains("Gastou"))
    }
}

class BrainTest {
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 22) }; AppClock.nowMs = { 1_795_000_000_000L } }
    private fun blank(): AppState { val s = AppState(); s.reserves.forEach { it.balance = 0.0 }; s.expenses = mutableListOf(); s.debts = mutableListOf(); s.subscriptions = mutableListOf(); s.assets = mutableListOf(); s.history = mutableListOf(); return s }

    @Test fun notaSobe() {
        val a = blank(); val b = blank(); a.expenses.add(Expense(1, "Aluguel", 1000.0, "Moradia")); b.expenses.add(Expense(1, "Aluguel", 1000.0, "Moradia")); b.reserves.first().balance = 20000.0
        assertTrue(healthScore(b).total > healthScore(a).total)
        assertTrue(healthScore(a).nextStep.isNotBlank()); assertTrue(healthScore(a).total in 0..100)
    }
    @Test fun cobrancaEmDobro() {
        val s = blank()
        s.history.add(HistoryEntry("2026-11-20", "gastolivre", "Extrato — Padaria Central", -18.5, null, "Mercado"))
        s.history.add(HistoryEntry("2026-11-21", "gastolivre", "Extrato — PADARIA CENTRAL 123", -18.5, null, "Mercado"))
        s.history.add(HistoryEntry("2026-11-10", "gastolivre", "Extrato — Outro lugar", -50.0, null, "Outro"))
        assertEquals(1, duplicateCharges(s).size)
    }
    @Test fun assinaturaEscondida() {
        val s = blank()
        listOf("2026-09-12", "2026-10-12", "2026-11-12").forEach { s.history.add(HistoryEntry(it, "gastolivre", "Extrato — Streamflix Brasil", -39.9, null, "Lazer")) }
        s.history.add(HistoryEntry("2026-11-03", "gastolivre", "Extrato — Mercado Unico", -80.0, null, "Mercado"))
        val r = recurringCharges(s); assertEquals(1, r.size); assertEquals(39.9, r[0].avg, 0.01); assertEquals(3, r[0].months)
        addSubscription(s, r[0].name, r[0].avg, r[0].day, "Outro"); assertTrue(recurringCharges(s).isEmpty())
    }
    @Test fun eSeCortar() { assertEquals(1200.0, whatIfCut(100.0, 1, 0.0), 0.001); assertTrue(whatIfCut(100.0, 10, 8.0) > 12000.0); assertEquals(0.0, whatIfCut(0.0, 5, 8.0), 0.001) }
}
