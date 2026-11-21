package com.meubanco.patrimonio.data

import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Cotações e CDI, sem chave de API. Mesmas fontes e ordem do app em HTML. */
object MarketApi {
    /** Bolsa aberta: dias úteis, 10h–18h (Brasília), exceto feriados nacionais. */
    fun isB3Open(): Boolean {
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))
        val dow = now.dayOfWeek.value
        if (dow >= 6) return false
        if (getNationalHolidays(now.year).contains(now.toLocalDate())) return false
        val m = now.hour * 60 + now.minute
        return m in (10 * 60)..(18 * 60)
    }

    private fun get(url: String, timeoutMs: Int = 10000): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MeuBanco/49")
        c.setRequestProperty("Accept", "application/json,text/html,*/*")
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) { null }

    suspend fun fetchCdi(s: AppState): Boolean = withContext(Dispatchers.IO) {
        val txt = get("https://brasilapi.com.br/api/taxas/v1")
        val arr = try { Json.parseToJsonElement(txt!!).jsonArray } catch (e: Exception) { null }
        fun taxa(nome: String) = try {
            arr?.firstOrNull { it.jsonObject["nome"]?.jsonPrimitive?.content?.uppercase() == nome }?.jsonObject?.get("valor")?.jsonPrimitive?.double
        } catch (e: Exception) { null }
        val rate = taxa("CDI")
        taxa("IPCA")?.takeIf { it > -5 && it < 100 }?.let { s.market.ipcaPct = it; s.market.macroUpdatedAt = AppClock.todayISO() }
        taxa("SELIC")?.takeIf { it > 0 && it < 100 }?.let { s.market.selicPct = it; s.market.macroUpdatedAt = AppClock.todayISO() }
        if (rate != null && rate > 0) {
            s.reserveConfig.annualRatePct = rate
            s.market.cdiUpdatedAt = AppClock.todayISO()
            s.market.cdiError = null
            true
        } else {
            s.market.cdiError = "Não foi possível atualizar o CDI agora — tentaremos de novo amanhã."
            false
        }
    }

    private fun applyQuote(a: Asset, price: Double, divPerMonth: Double?): Boolean {
        if (!price.isFinite() || price <= 0) return false
        val np = Math.round(price * 100.0) / 100.0
        if (a.price != np) a.prevPrice = a.price
        a.price = np
        a.realPriceAt = AppClock.nowMs().toDouble()
        if (divPerMonth != null && divPerMonth.isFinite() && divPerMonth > 0) {
            val y = divPerMonth / a.price * 100
            if (y > 0 && y < 6) { a.yieldPct = Math.round(y * 100.0) / 100.0; a.divMonthly = Math.round(divPerMonth * 1000.0) / 1000.0 }
        }
        return true
    }

    private fun quotesFile(s: AppState): JsonObject? {
        val url = s.market.quotesUrl.trim()
        if (url.isEmpty()) return null
        val txt = get(url + (if (url.contains("?")) "&" else "?") + "t=" + System.currentTimeMillis()) ?: return null
        return try { Json.parseToJsonElement(txt).jsonObject.takeIf { it["quotes"] is JsonObject } } catch (e: Exception) { null }
    }

    private fun sparkBatch(tickers: List<String>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        for (chunk in tickers.chunked(10)) {
            for (host in listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")) {
                val url = "https://$host/v8/finance/spark?range=1d&interval=1d&symbols=" + chunk.joinToString(",") { "$it.SA" }
                val txt = get(url) ?: continue
                try {
                    val res = Json.parseToJsonElement(txt).jsonObject["spark"]!!.jsonObject["result"]!!.jsonArray
                    var any = false
                    for (x in res) {
                        val o = x.jsonObject
                        val sym = o["symbol"]?.jsonPrimitive?.content?.removeSuffix(".SA") ?: continue
                        val p = o["response"]?.jsonArray?.firstOrNull()?.jsonObject?.get("meta")?.jsonObject
                            ?.get("regularMarketPrice")?.jsonPrimitive?.doubleOrNull
                        if (p != null && p > 0) { out[sym] = p; any = true }
                    }
                    if (any) break
                } catch (_: Exception) {}
            }
            Thread.sleep(300)
        }
        return out
    }

    private class Chart(val price: Double, val divPerMonth: Double?)

    private fun yahooChart(ticker: String): Chart? {
        for (host in listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")) {
            val txt = get("https://$host/v8/finance/chart/$ticker.SA?range=1y&interval=1mo&events=div") ?: continue
            try {
                val r0 = Json.parseToJsonElement(txt).jsonObject["chart"]!!.jsonObject["result"]!!.jsonArray[0].jsonObject
                val price = r0["meta"]!!.jsonObject["regularMarketPrice"]!!.jsonPrimitive.double
                val nowS = System.currentTimeMillis() / 1000.0
                val divs = r0["events"]?.jsonObject?.get("dividends")?.jsonObject?.values ?: emptyList()
                val sum = divs.map { it.jsonObject }
                    .filter { (it["date"]?.jsonPrimitive?.doubleOrNull ?: 0.0) >= nowS - 366 * 86400 }
                    .sumOf { it["amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0 }
                val firstTs = r0["timestamp"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull ?: (nowS - 365 * 86400)
                val months = Math.max(1.0, Math.min(12.0, Math.round((nowS - firstTs) / (30.4 * 86400)).toDouble()))
                return Chart(price, if (sum > 0) sum / months else null)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun googlePrice(ticker: String): Double? {
        val txt = get("https://www.google.com/finance/quote/$ticker:BVMF?hl=pt") ?: return null
        return Regex("data-last-price=\"([\\d.]+)\"").find(txt)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }
    }

    /** Atualiza preços (arquivo → Yahoo spark → Yahoo chart → Google). Retorna quantos ativos atualizou. */
    suspend fun refreshAssets(s: AppState): Pair<Int, String> = withContext(Dispatchers.IO) {
        var ok = 0
        val file = quotesFile(s)
        var fileOk = 0
        if (file != null) {
            val q = file["quotes"]!!.jsonObject
            for (a in s.assets) {
                val o = q[a.ticker] as? JsonObject ?: continue
                val p = o["price"]?.jsonPrimitive?.doubleOrNull ?: continue
                if (applyQuote(a, p, o["divMonthly"]?.jsonPrimitive?.doubleOrNull)) { a.divAt = AppClock.nowMs().toDouble(); fileOk++ }
                a.valAnnualPct = o["valAnnualPct"]?.jsonPrimitive?.doubleOrNull ?: a.valAnnualPct
                a.valMonths = o["valMonths"]?.jsonPrimitive?.intOrNull ?: a.valMonths
            }
        }
        if (fileOk >= Math.ceil(s.assets.size / 2.0)) {
            s.market.assetsUpdatedAt = AppClock.todayISO()
            s.market.assetsUpdatedAtMs = AppClock.nowMs()
            s.market.assetsCheckedAtMs = AppClock.nowMs()
            s.market.assetsError = null
            return@withContext fileOk to "✓ $fileOk ativo(s) atualizado(s) pelo arquivo de cotações."
        }
        val tickers = s.assets.map { it.ticker }
        val prices = sparkBatch(tickers).toMutableMap()
        var extra = 0
        for (t in tickers) {
            if (prices.containsKey(t) || extra >= 8) continue
            extra++
            val c = yahooChart(t)
            if (c != null) {
                prices[t] = c.price
                s.assets.find { it.ticker == t }?.let { a -> applyQuote(a, c.price, c.divPerMonth); a.divAt = AppClock.nowMs().toDouble() }
            } else googlePrice(t)?.let { prices[t] = it }
            delay(400)
        }
        val failed = mutableListOf<String>()
        for (a in s.assets) {
            val p = prices[a.ticker]
            if (p != null && applyQuote(a, p, null)) ok++ else failed.add(a.ticker)
        }
        s.market.assetsCheckedAtMs = AppClock.nowMs()
        if (ok > 0) {
            s.market.assetsUpdatedAt = AppClock.todayISO(); s.market.assetsUpdatedAtMs = AppClock.nowMs(); s.market.assetsError = null
            ok to "✓ $ok ativo(s) atualizado(s)" + (if (failed.isNotEmpty()) " · sem resposta: " + failed.joinToString(", ") else "") + "."
        } else {
            s.market.assetsError = "Sem resposta das fontes."
            0 to "Sem resposta das fontes agora — tente de novo em alguns minutos."
        }
    }

    /** Código de barras → nome e marca (Open Food Facts). */
    suspend fun lookupBarcode(code: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val txt = get("https://world.openfoodfacts.org/api/v2/product/" + URLEncoder.encode(code, "UTF-8") + ".json?fields=product_name,brands", 8000)
            ?: return@withContext null
        try {
            val p = Json.parseToJsonElement(txt).jsonObject["product"]?.jsonObject ?: return@withContext null
            val name = p["product_name"]?.jsonPrimitive?.content.orEmpty()
            val brand = p["brands"]?.jsonPrimitive?.content.orEmpty().split(",").firstOrNull()?.trim().orEmpty()
            if (name.isBlank() && brand.isBlank()) null else name to brand
        } catch (e: Exception) { null }
    }
}
