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
