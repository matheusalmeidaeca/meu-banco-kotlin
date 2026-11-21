package com.meubanco.patrimonio

import com.meubanco.patrimonio.data.*
import com.meubanco.patrimonio.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class BackendTest {
    @Before fun clock() { AppClock.today = { LocalDate.of(2026, 11, 20) }; AppClock.nowMs = { 1_795_000_000_000L } }
    private val ID = "3478febe-8b1f-47ff-8ef9-691bdac0fb3d"
    private val seen = mutableListOf<String>()
    private val http = object : PluggyHttp {
        override fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String?> {
            seen.add("$method $url"); assertEquals("Bearer G", headers["Authorization"]); assertNull(headers["X-API-KEY"])
            return when {
                url.contains("/items/") -> 200 to """{"id":"$ID","status":"UPDATED","connector":{"name":"Nubank"}}"""
                url.contains("/accounts?") -> 200 to """{"results":[{"id":"a1","type":"BANK","subtype":"CHECKING_ACCOUNT","name":"Conta","balance":10}]}"""
                url.contains("/investments?") -> 200 to """{"totalPages":1,"results":[]}"""
                url.contains("/v2/transactions?") -> 200 to """{"results":[{"id":"t1","date":"2026-11-10T00:00:00Z","description":"Mercado","amount":-5,"type":"DEBIT","status":"POSTED"}],"next":null}"""
                else -> 404 to null
            }
        }
    }

    @Test fun sincronizaPeloServidorSemChavesDaPluggy() {
        val s = AppState.novo()
        val r = PluggyApi.sync(s, "", "", listOf(ID), http, "2026-11-20", backend = "https://srv.example/" to "G")
        assertEquals(1, r.accounts); assertEquals(1, r.newTx)
        assertTrue(seen.all { it.contains("https://srv.example/p/") }) // nada vai direto para a Pluggy
    }

    @Test fun semBancoConectadoOrientaOUsuario() {
        try { PluggyApi.sync(AppState.novo(), "", "", emptyList(), http, "2026-11-20", backend = "https://srv.example" to "G"); fail() }
        catch (e: PluggyException) { assertTrue(e.message!!.contains("Conectar banco")) }
    }

    @Test fun paginaDoWidgetEscapaOToken() {
        val h = Backend.widgetHtml("a\"b</script>", true)
        assertTrue(h.contains("includeSandbox: true")); assertFalse(h.contains("a\"b</script>")); assertTrue(h.contains("pluggy-connect.js"))
    }
}
