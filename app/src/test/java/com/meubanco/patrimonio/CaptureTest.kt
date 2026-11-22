package com.meubanco.patrimonio

import com.meubanco.patrimonio.data.BankNotif
import org.junit.Assert.*
import org.junit.Test

class CaptureTest {
    private fun p(pkg: String, t: String, x: String) = BankNotif.parse(pkg, t, x, 1_795_000_000_000L)
    @Test fun compraNoCartao() {
        val c = p("com.nu.production", "Compra aprovada", "Compra de R$ 1.234,56 aprovada em MERCADO CENTRAL")!!
        assertEquals("saida", c.kind); assertEquals(1234.56, c.amount, 0.001); assertEquals("Nubank", c.bank); assertTrue(c.desc.contains("MERCADO CENTRAL"))
    }
    @Test fun pixRecebido() {
        val c = p("com.mercadopago.wallet", "Pix recebido", "Você recebeu R$ 50,00 de Maria Silva")!!
        assertEquals("entrada", c.kind); assertEquals(50.0, c.amount, 0.001); assertTrue(c.desc.startsWith("Maria"))
    }
    @Test fun ignoraFaturaEOutrosApps() {
        assertNull(p("com.nu.production", "Fatura fechada", "Sua fatura de R$ 300,00 vence dia 10"))
        assertNull(p("com.whatsapp", "Pix", "Você recebeu R$ 50,00 de João"))
        assertNull(p("com.picpay", "Oferta", "Ganhe R$ 20,00 de cashback"))
        assertNull(p("com.picpay", "Aviso", "Seu saldo mudou"))
    }
}
