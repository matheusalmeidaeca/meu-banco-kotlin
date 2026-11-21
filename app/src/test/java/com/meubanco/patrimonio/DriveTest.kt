package com.meubanco.patrimonio

import com.meubanco.patrimonio.data.*
import org.junit.Assert.*
import org.junit.Test

class DriveTest {
    private class Fake(var existing: String?) : PluggyHttp {
        val calls = mutableListOf<Triple<String, String, String?>>(); var stored: String? = null
        override fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String?> {
            calls.add(Triple(method, url, body)); assertEquals("Bearer T", headers["Authorization"])
            return when {
                url.contains("/about") -> 200 to """{"user":{"emailAddress":"a@b.com"}}"""
                url.contains("spaces=appDataFolder") -> 200 to (if (existing != null) """{"files":[{"id":"$existing"}]}""" else """{"files":[]}""")
                url.contains("alt=media") -> 200 to stored
                url.contains("uploadType=multipart") -> { stored = body!!.substringAfter("\r\n\r\n").substringAfter("\r\n\r\n").substringBefore("\r\n--"); 200 to "{}" }
                url.contains("uploadType=media") -> { assertEquals("PATCH", headers["X-HTTP-Method-Override"]); stored = body; 200 to "{}" }
                else -> 404 to null
            }
        }
    }

    @Test fun descobreEmail() { assertEquals("a@b.com", GoogleDrive.whoAmI("T", Fake(null))) }

    @Test fun primeiroEnvioCriaNaPastaEscondida() {
        val f = Fake(null); GoogleDrive.upload("T", """{"x":1}""", f)
        val post = f.calls.last(); assertTrue(post.second.contains("uploadType=multipart")); assertTrue(post.third!!.contains("appDataFolder"))
        assertEquals("""{"x":1}""", f.stored)
    }

    @Test fun envioSeguinteSubstituiOMesmoArquivo() {
        val f = Fake("id123"); GoogleDrive.upload("T", """{"x":2}""", f)
        assertTrue(f.calls.last().second.contains("/files/id123?uploadType=media")); assertEquals("""{"x":2}""", f.stored)
    }

    @Test fun baixaOQueFoiEnviadoEDizQuandoNaoHa() {
        val f = Fake("id1"); f.stored = """{"y":3}"""
        assertEquals("""{"y":3}""", GoogleDrive.download("T", f))
        assertNull(GoogleDrive.download("T", Fake(null)))
    }
}
