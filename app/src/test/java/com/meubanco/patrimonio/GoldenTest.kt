package com.meubanco.patrimonio

import com.meubanco.patrimonio.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Compara o motor em Kotlin com o resultado do motor ORIGINAL em HTML/JS (v48),
 * gerado por tools/golden/gen_golden.js. Mesmo estado inicial + mesmas operações = mesmo resultado.
 */
class GoldenTest {
    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Before
    fun fixClock() {
        AppClock.today = { LocalDate.of(2026, 11, 17) }
        AppClock.nowMs = { 1_795_000_000_000L }
    }

    // ---------- comparação tolerante de JSON ----------
    private fun norm(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    private fun cmp(path: String, exp: JsonElement?, act: JsonElement?, errs: MutableList<String>) {
        if (errs.size > 25) return
        if (exp == null || exp is JsonNull) {
            if (act != null && act !is JsonNull) {
                // campo existente só no Kotlin com valor "vazio" é aceito
                val p = act as? JsonPrimitive
                if (p != null && (p.content == "" || p.content == "false" || p.content == "0" || p.content == "0.0")) return
                errs.add("$path: esperado null, veio $act")
            }
            return
        }
        when (exp) {
            is JsonObject -> {
                val a = act as? JsonObject
                if (a == null) { errs.add("$path: esperado objeto, veio $act"); return }
                for ((k, v) in exp) cmp("$path.$k", v, a[k], errs)
            }
            is JsonArray -> {
                val a = act as? JsonArray
                if (a == null) { errs.add("$path: esperado array, veio $act"); return }
                if (a.size != exp.size) { errs.add("$path: tamanho ${exp.size} x ${a.size}"); return }
                for (i in exp.indices) cmp("$path[$i]", exp[i], a[i], errs)
            }
            is JsonPrimitive -> {
                val ap = act as? JsonPrimitive
                if (ap == null) { errs.add("$path: esperado $exp, veio $act"); return }
                if (exp.isString) {
                    if (norm(exp.content) != norm(ap.content)) errs.add("$path: '${exp.content}' x '${ap.content}'")
                } else {
                    val e = exp.content
                    if (e == "true" || e == "false") {
                        if (e != ap.content) errs.add("$path: $e x ${ap.content}")
                    } else {
                        val ed = exp.double
                        val ad = ap.doubleOrNull
                        if (ad == null || Math.abs(ed - ad) > 1e-6 * Math.max(1.0, Math.abs(ed))) errs.add("$path: $ed x ${ap.content}")
                    }
                }
            }
        }
    }

    private fun runScenario(file: String) {
        val sc = AppJson.parseToJsonElement(res(file)).jsonObject
        val input = decodeStateRaw(sc["input"]!!.toString())
        // o JS foi gerado com onboarding concluído e o mesmo estado; aplica as operações
        for (o in sc["ops"]!!.jsonArray) {
            val op = o.jsonObject
            when (op["op"]!!.jsonPrimitive.content) {
                "sim" -> repeat(op["days"]!!.jsonPrimitive.int) { simulateOneDay(input) }
                "transfer" -> transferInvestAccounts(input, op["toInvest"]!!.jsonPrimitive.boolean, op["amount"]!!.jsonPrimitive.double)
                "buy" -> manualInvest(input, op["id"]!!.jsonPrimitive.content, op["qty"]!!.jsonPrimitive.int)
                "sell" -> sellShares(input, op["id"]!!.jsonPrimitive.content, op["qty"]!!.jsonPrimitive.int)
                "darf" -> payDarf(input, op["ym"]!!.jsonPrimitive.content)
                "reserveExtra" -> addExtraToReserve(input, op["id"]!!.jsonPrimitive.int, op["amount"]!!.jsonPrimitive.double)
                "reserveWithdraw" -> withdrawFromReserve(input, op["id"]!!.jsonPrimitive.int, op["amount"]!!.jsonPrimitive.double)
                "spendFree" -> spendFree(input, op["amount"]!!.jsonPrimitive.double, op["category"]!!.jsonPrimitive.content)
                "setPrice" -> input.assets.first { it.id == op["id"]!!.jsonPrimitive.content }.price = op["price"]!!.jsonPrimitive.double
                else -> fail("op desconhecida")
            }
        }
        val actual = AppJson.parseToJsonElement(encodeState(input))
        val errs = mutableListOf<String>()
        cmp("", sc["expected"]!!, actual, errs)
        // ignora diferenças em campos puramente de UI/aleatórios
        assertTrue("Divergências em $file:\n" + errs.joinToString("\n"), errs.isEmpty())

        // IR: os DARFs pendentes também precisam bater
        sc["pendingDarfYm"]?.let { exp ->
            val got = pendingDarfs(input).map { it.ym }
            assertEquals(exp.jsonArray.map { it.jsonPrimitive.content }, got)
        }
    }

    /** decodifica sem a etapa de migração (o JS gerou estados já normalizados) */
    private fun decodeStateRaw(text: String): AppState = AppJson.decodeFromString(AppState.serializer(), text)

    @Test fun cenarioA_empregadoCompleto() = runScenario("scenario_A_empregado_completo.json")
    @Test fun cenarioB_beneficioVendasIR() = runScenario("scenario_B_beneficio_vendas_ir.json")
    @Test fun cenarioC_cltFerias13() = runScenario("scenario_C_clt_ferias_13o.json")

    @Test fun funcoesPuras() {
        val fn = AppJson.parseToJsonElement(res("functions.json")).jsonObject
        for (p in fn["inss"]!!.jsonArray) {
            val a = p.jsonArray
            val g = a[0].jsonPrimitive.double
            assertEquals("INSS $g", a[1].jsonPrimitive.double, computeINSS(g), 1e-9)
        }
        for (p in fn["irrf"]!!.jsonArray) {
            val a = p.jsonArray
            val g = a[0].jsonPrimitive.double
            assertEquals("IRRF $g", a[1].jsonPrimitive.double, computeIRRF(g, computeINSS(g)), 1e-9)
        }
        for ((y, list) in fn["holidays"]!!.jsonObject) {
            val got = getNationalHolidays(y.toInt()).map { it.toString() }.sorted()
            assertEquals("feriados $y", list.jsonArray.map { it.jsonPrimitive.content }, got)
        }
        for (p in fn["nth"]!!.jsonArray) {
            val a = p.jsonArray
            assertEquals("5º dia útil", a[3].jsonPrimitive.content, getNthBusinessDay(a[0].jsonPrimitive.int, a[1].jsonPrimitive.int, a[2].jsonPrimitive.int).toString())
        }
        for (p in fn["lbd"]!!.jsonArray) {
            val a = p.jsonArray
            assertEquals("último dia útil", a[2].jsonPrimitive.content, lastBusinessDay(a[0].jsonPrimitive.int, a[1].jsonPrimitive.int).toString())
        }
        for (p in fn["fmt"]!!.jsonArray) {
            val a = p.jsonArray
            assertEquals("formatBRL ${a[0]}", norm(a[1].jsonPrimitive.content), norm(formatBRL(a[0].jsonPrimitive.double)))
        }
        for (p in fn["quiz"]!!.jsonArray) {
            val a = p.jsonArray
            val answers = a[0].jsonArray.map { it.jsonPrimitive.int }
            val r = scoreInvestorQuiz(answers)
            val e = a[1].jsonObject
            assertEquals(e["score"]!!.jsonPrimitive.int, r.score)
            assertEquals(e["label"]!!.jsonPrimitive.content, r.label)
            assertEquals(e["cap"]!!.jsonPrimitive.content, r.cap)
        }
        for (p in fn["pontoHours"]!!.jsonArray) {
            val a = p.jsonArray
            val t = a[0].jsonArray.map { it.jsonPrimitive.content }
            assertEquals(a[1].jsonPrimitive.double, computePontoHours(t[0], t[1], t[2], t[3]), 1e-9)
        }
    }

    @Test fun hashDoPinIgualAoDoHtml() {
        // "1234" no JS: ((((0*31+49)*31+50)*31+51)*31+52)
        assertEquals("1509442", simpleHash("1234"))
    }

    @Test fun backupDoHtmlImportaSemErro() {
        val sc = AppJson.parseToJsonElement(res("scenario_A_empregado_completo.json")).jsonObject
        val st = decodeState(sc["expected"]!!.toString())
        assertEquals(true, st.onboardingCompleted)
        assertTrue(st.history.isNotEmpty())
        // reexportar e importar de novo mantém os dados
        val again = decodeState(encodeState(st))
        assertEquals(st.cash, again.cash, 0.0)
        assertEquals(st.history.size, again.history.size)
    }
}
