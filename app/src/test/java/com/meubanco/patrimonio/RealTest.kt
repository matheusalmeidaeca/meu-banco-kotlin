package com.meubanco.patrimonio

import com.meubanco.patrimonio.data.*
import com.meubanco.patrimonio.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class RealTest {
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 20) }; AppClock.nowMs = { 1_795_000_000_000L } }

    private val calls = mutableListOf<String>()
    private fun fake(fail401: Boolean = false) = object : PluggyHttp {
        override fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String?> {
            calls.add("$method $url")
            if (url.endsWith("/auth")) return if (fail401) 401 to "{}" else 200 to """{"apiKey":"K"}"""
            assertEquals("K", headers["X-API-KEY"])
            return when {
                url.contains("/investments") -> 200 to """{"page":1,"total":2,"totalPages":1,"results":[
                    {"id":"i1","type":"FIXED_INCOME","subtype":"CDB","name":"CDB XP","balance":1000.5,"amountOriginal":900,"annualRate":12.5,"dueDate":"2028-01-01T00:00:00Z","status":"ACTIVE"},
                    {"id":"i2","type":"EQUITY","name":"PETR4","balance":500,"status":"ACTIVE"},
                    {"id":"i3","type":"EQUITY","name":"Vendido","balance":0,"status":"TOTAL_WITHDRAWAL"}]}"""
                url.contains("/v2/items") || url.contains("/items/") -> 200 to """{"results":[{"id":"it1","status":"UPDATED","connector":{"name":"MeuPluggy"},"consentExpiresAt":"2026-11-25T00:00:00Z"}],"next":null,"id":"it1","connector":{"name":"MeuPluggy"},"status":"UPDATED"}"""
                url.contains("/accounts") -> 200 to """{"results":[
                    {"id":"a1","type":"BANK","subtype":"CHECKING_ACCOUNT","name":"Conta","balance":1500.5},
                    {"id":"a2","type":"CREDIT","subtype":"CREDIT_CARD","name":"Cartão","balance":300,"creditData":{"creditLimit":2000,"availableCreditLimit":1700,"balanceDueDate":"2026-12-05T00:00:00Z"}}]}"""
                url.contains("accountId=a1") -> 200 to """{"results":[
                    {"id":"t1","date":"2026-11-03T00:00:00Z","description":"Salario","amount":3000,"type":"CREDIT","status":"POSTED"},
                    {"id":"t2","date":"2026-11-05T00:00:00Z","description":"Mercado Extra","amount":-200.25,"type":"DEBIT","status":"POSTED"},
                    {"id":"t3","date":"2026-11-06T00:00:00Z","description":"Pagamento de fatura","amount":-300,"type":"DEBIT","status":"POSTED"},
                    {"id":"t4","date":"2026-11-07T00:00:00Z","description":"Pendente","amount":-10,"type":"DEBIT","status":"PENDING"}],"next":null}"""
                else -> 200 to """{"results":[{"id":"t5","date":"2026-11-04T00:00:00Z","description":"Uber viagem","amount":50,"type":"DEBIT","status":"POSTED"}],"next":null}"""
            }
        }
    }

    @Test fun sincronizaContasELancamentos() {
        val s = AppState.novo(); s.realToHistory = false
        val cash0 = s.cash
        val r = PluggyApi.sync(s, "id", "sec", emptyList(), fake(), "2026-11-20")
        assertEquals(2, r.accounts); assertEquals(4, r.newTx) // pendente ignorado
        assertEquals("2026-11-20", s.realLastSyncISO)
        val b = realBalances(s); assertEquals(1500.5, b.cash, 0.001); assertEquals(300.0, b.cardOwed, 0.001); assertEquals(2000.0, b.cardLimit, 0.001)
        val m = realMonthSummary(s, "2026-11") // fatura (t3) não conta como gasto
        assertEquals(3000.0, m.income, 0.001); assertEquals(250.25, m.spent, 0.001)
        assertEquals(cash0, s.cash, 0.0) // não mexe no saldo simulado
        assertTrue(calls.any { it.contains("dateFrom=2026-08-22") }) // primeira vez: 90 dias
        assertEquals("2026-11-25", s.realLinks[0].consentExpiresAt!!.take(10))
    }

    @Test fun segundaSyncNaoDuplicaEUsaJanelaCurta() {
        val s = AppState.novo()
        PluggyApi.sync(s, "id", "sec", emptyList(), fake(), "2026-11-20"); calls.clear()
        val r = PluggyApi.sync(s, "id", "sec", emptyList(), fake(), "2026-11-21")
        assertEquals(0, r.newTx); assertEquals(4, s.realTx.size)
        assertTrue(calls.any { it.contains("dateFrom=2026-11-10") })
    }

    @Test fun importaHistoricoUmaVezSemMexerNoSaldo() {
        val s = AppState.novo(); s.realToHistory = true; val cash0 = s.cash; val h0 = s.history.size
        PluggyApi.sync(s, "id", "sec", emptyList(), fake(), "2026-11-20")
        assertEquals(h0 + 3, s.history.size) // salário, mercado, uber (fatura fora)
        PluggyApi.sync(s, "id", "sec", emptyList(), fake(), "2026-11-21")
        assertEquals(h0 + 3, s.history.size); assertEquals(cash0, s.cash, 0.0)
    }

    @Test fun credenciaisRecusadasDaoMensagemClara() {
        try { PluggyApi.sync(AppState.novo(), "x", "y", emptyList(), fake(true)); fail() }
        catch (e: PluggyException) { assertTrue(e.message!!.contains("Credenciais")) }
    }

    @Test fun sincronizaPorIds() {
        val s = AppState.novo()
        val r = PluggyApi.sync(s, "id", "sec", listOf("3478febe-8b1f-47ff-8ef9-691bdac0fb3d"), fake(), "2026-11-20")
        assertEquals(2, r.accounts); assertTrue(calls.any { it.contains("/items/3478febe") }); assertFalse(calls.any { it.contains("/v2/items") })
    }
    @Test fun extraiIdsDeTextoSujo() {
        val l = PluggyApi.parseItemIds("a 3478FEBE-8b1f-47ff-8ef9-691bdac0fb3d, lixo\n3478febe-8b1f-47ff-8ef9-691bdac0fb3d x")
        assertEquals(1, l.size)
    }
    @Test fun lerInvestimentos() {
        val s = AppState.novo(); PluggyApi.sync(s, "id", "sec", emptyList(), fake(), "2026-11-20")
        assertEquals(2, s.realInvest.size); assertEquals(1500.5, realInvestTotal(s), 0.001)
        assertEquals("FIXED_INCOME", realInvestByType(s)[0].first)
    }
    @Test fun movimentoInterno() {
        assertTrue(isInternalMovement("PAGAMENTO DE FATURA")); assertFalse(isInternalMovement("Mercado"))
    }
}
