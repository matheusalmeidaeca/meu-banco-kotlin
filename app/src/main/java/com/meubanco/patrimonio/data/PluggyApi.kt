package com.meubanco.patrimonio.data

import android.content.Context
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface PluggyHttp { fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String?> }

object RealHttp : PluggyHttp {
    @Volatile var lastError: String? = null

    /** DNS criptografado (DoH) por IP, usado só quando o DNS do sistema não resolve o domínio. */
    private fun resolveDoh(host: String): List<String> {
        for (base in listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")) {
            try {
                val c = URL("$base?name=$host&type=A").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("Accept", "application/dns-json")
                if (c.responseCode !in 200..299) continue
                val ans = Json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject["Answer"] as? JsonArray ?: continue
                val ips = ans.mapNotNull { (it as? JsonObject)?.takeIf { o -> o["type"]?.jsonPrimitive?.intOrNull == 1 }?.get("data")?.jsonPrimitive?.contentOrNull }
                if (ips.isNotEmpty()) return ips
            } catch (e: Exception) { }
        }
        return emptyList()
    }

    private fun open(url: String, forceHost: String?, ip: String?): HttpURLConnection {
        if (ip == null) return URL(url).openConnection() as HttpURLConnection
        val u = URL(url)
        val c = URL(u.protocol, ip, if (u.port == -1) u.defaultPort else u.port, u.file).openConnection() as javax.net.ssl.HttpsURLConnection
        c.setRequestProperty("Host", forceHost)
        val base = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
        c.sslSocketFactory = object : javax.net.ssl.SSLSocketFactory() {
            override fun getDefaultCipherSuites() = base.defaultCipherSuites
            override fun getSupportedCipherSuites() = base.supportedCipherSuites
            override fun createSocket(s: java.net.Socket, h: String?, p: Int, a: Boolean) = base.createSocket(s, forceHost, p, a)
            override fun createSocket(h: String?, p: Int) = base.createSocket(forceHost, p)
            override fun createSocket(h: String?, p: Int, la: java.net.InetAddress?, lp: Int) = base.createSocket(forceHost, p, la, lp)
            override fun createSocket(a: java.net.InetAddress?, p: Int) = base.createSocket(a, p)
            override fun createSocket(a: java.net.InetAddress?, p: Int, la: java.net.InetAddress?, lp: Int) = base.createSocket(a, p, la, lp)
        }
        c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session -> javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(forceHost, session) }
        return c
    }

    private fun once(method: String, url: String, headers: Map<String, String>, body: String?, ip: String?): Pair<Int, String?> {
        val c = open(url, URL(url).host, ip)
        c.requestMethod = method; c.connectTimeout = 25000; c.readTimeout = 40000
        c.setRequestProperty("User-Agent", "MeuBancoPatrimonio/56")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText()
        return code to txt
    }

    override fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String?> {
      return try {
        try { once(method, url, headers, body, null) }
        catch (e: java.net.UnknownHostException) {
            val ips = resolveDoh(URL(url).host)
            if (ips.isEmpty()) throw e
            var last: Exception = e
            for (ip in ips.take(3)) { try { return once(method, url, headers, body, ip) } catch (x: Exception) { last = x } }
            throw last
        }
    } catch (e: Exception) { lastError = e.javaClass.simpleName + ": " + (e.message ?: "").take(120); -1 to null }
    }
}

class PluggyException(msg: String) : Exception(msg)

class PluggySyncResult(val accounts: Int, val newTx: Int, val imported: Int, val warnings: List<String>)

/** Leitura das suas contas via Pluggy (Meu Pluggy). Nunca movimenta dinheiro. */
object PluggyApi {
    private const val BASE = "https://api.pluggy.ai"

    private fun credsFile(ctx: Context) = File(ctx.noBackupFilesDir, "pluggy.json") // fora do backup automático
    fun loadCreds(ctx: Context): Pair<String, String>? = try {
        val o = Json.parseToJsonElement(credsFile(ctx).readText()).jsonObject
        val id = o["clientId"]!!.jsonPrimitive.content; val sec = o["clientSecret"]!!.jsonPrimitive.content
        if (id.isNotBlank() && sec.isNotBlank()) id to sec else null
    } catch (e: Exception) { null }
    fun saveCreds(ctx: Context, id: String, secret: String) {
        credsFile(ctx).writeText(buildJsonObject { put("clientId", id.trim()); put("clientSecret", secret.trim()) }.toString())
    }
    fun clearCreds(ctx: Context) { credsFile(ctx).delete(); itemsFile(ctx).delete() }
    private fun itemsFile(ctx: Context) = File(ctx.noBackupFilesDir, "pluggy_items.txt")
    fun parseItemIds(raw: String): List<String> = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").findAll(raw).map { it.value.lowercase() }.distinct().toList()
    fun loadItemIds(ctx: Context): List<String> = try { parseItemIds(itemsFile(ctx).readText()) } catch (e: Exception) { emptyList() }
    fun saveItemIds(ctx: Context, raw: String): Int { val ids = parseItemIds(raw); itemsFile(ctx).writeText(ids.joinToString("\n")); return ids.size }
    fun addItemId(ctx: Context, id: String) { saveItemIds(ctx, (loadItemIds(ctx) + id).joinToString("\n")) }
    fun removeItemId(ctx: Context, id: String) { saveItemIds(ctx, loadItemIds(ctx).filter { it != id.lowercase() }.joinToString("\n")) }
    fun hasCreds(ctx: Context) = loadCreds(ctx) != null

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    class Route(val base: String, val headerName: String, val headerValue: String)

    private fun getJson(http: PluggyHttp, r: Route, path: String): JsonObject {
        val (code, txt) = http.call("GET", r.base + path, mapOf(r.headerName to r.headerValue, "Accept" to "application/json"), null)
        when {
            code == 401 -> throw PluggyException(if (r.base == BASE) "A Pluggy recusou o acesso (chave expirada ou sem permissão)." else "Entre com sua conta Google de novo.")
            code == 403 -> throw PluggyException(if (r.base == BASE) "A Pluggy recusou o acesso (chave expirada ou sem permissão)." else "Essa conexão não pertence à sua conta.")
            code == 429 -> throw PluggyException("Muitas consultas à Pluggy. Tente de novo em alguns minutos.")
            code == -1 || txt == null -> throw PluggyException("Sem resposta da Pluggy (" + (RealHttp.lastError ?: "sem detalhe") + ").")
            code !in 200..299 -> throw PluggyException("A Pluggy respondeu com erro ($code).")
        }
        return try { Json.parseToJsonElement(txt!!).jsonObject } catch (e: Exception) { throw PluggyException("Resposta inesperada da Pluggy.") }
    }

    private fun auth(http: PluggyHttp, id: String, secret: String): String {
        val body = buildJsonObject { put("clientId", id); put("clientSecret", secret) }.toString()
        val (code, txt) = http.call("POST", "$BASE/auth", mapOf("Content-Type" to "application/json", "Accept" to "application/json"), body)
        if (code == -1 || txt == null) throw PluggyException("Sem resposta da Pluggy (" + (RealHttp.lastError ?: "sem detalhe") + ").")
        if (code == 429) throw PluggyException("Muitas consultas à Pluggy. Tente de novo em alguns minutos.")
        if (code !in 200..299) throw PluggyException("Credenciais recusadas. Confira o client_id e o client_secret do seu aplicativo na Pluggy.")
        return try { Json.parseToJsonElement(txt).jsonObject["apiKey"]!!.jsonPrimitive.content } catch (e: Exception) { throw PluggyException("Resposta inesperada da Pluggy.") }
    }

    /** Busca conexões, contas e lançamentos e atualiza o estado. Bloqueante: chame fora da thread principal. */
    fun sync(s: AppState, clientId: String, clientSecret: String, itemIds: List<String> = emptyList(), http: PluggyHttp = RealHttp, todayISO: String = AppClock.todayISO(), backend: Pair<String, String>? = null): PluggySyncResult {
        // backend = (url do servidor, token Google). Sem ele, usa as chaves próprias (modo avançado).
        val route = if (backend != null) Route(backend.first.trimEnd('/') + "/p", "Authorization", "Bearer " + backend.second)
                    else Route(BASE, "X-API-KEY", auth(http, clientId, clientSecret))
        if (backend != null && itemIds.isEmpty()) throw PluggyException("Nenhum banco conectado ainda. Toque em Conectar banco.")
        val warnings = mutableListOf<String>()

        // 1) conexões (itens) do seu aplicativo
        val items = mutableListOf<JsonObject>()
        if (itemIds.isNotEmpty()) {
            for (id in itemIds) {
                try { items.add(getJson(http, route, "/items/${enc(id)}")) }
                catch (e: PluggyException) { warnings.add("Conexão ${id.take(8)}…: ${e.message}") }
            }
        } else {
            var next: String? = ""
            var guard = 0
            try {
                while (next != null && guard++ < 20) {
                    val j = getJson(http, route, "/v2/items$next")
                    (j["results"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(items::add) }
                    next = (j["next"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                }
            } catch (e: PluggyException) {
                throw PluggyException("A Pluggy não libera a lista automática de conexões. Cole os IDs das suas conexões (veja o passo a passo).")
            }
        }
        if (items.isEmpty()) throw PluggyException("Nenhuma conexão encontrada. Falta autorizar o MeuPluggy no aplicativo de demonstração da Pluggy (passo 5).")

        val from = if (s.realLastSyncISO == null) addDaysISO(todayISO, -90) else addDaysISO(s.realLastSyncISO!!, -10)
        val links = mutableListOf<RealLink>(); val accounts = mutableListOf<RealAccount>(); val fetched = mutableListOf<RealTx>()
        val failedItems = mutableSetOf<String>(); val invests = mutableListOf<RealInvest>(); val investFailed = mutableSetOf<String>()

        for (it in items) {
            val itemId = (it["id"] as? JsonPrimitive)?.contentOrNull ?: continue
            val name = ((it["connector"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull ?: "Conexão"
            val status = (it["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            links.add(RealLink(itemId, name, status, (it["lastUpdatedAt"] as? JsonPrimitive)?.contentOrNull, (it["consentExpiresAt"] as? JsonPrimitive)?.contentOrNull))
            try {
                val accs = parseRealAccounts(itemId, (getJson(http, route, "/accounts?itemId=${enc(itemId)}&pageSize=500")["results"] as? JsonArray) ?: JsonArray(emptyList()))
                accounts.addAll(accs)
                for (a in accs) {
                    var n: String? = "?accountId=${enc(a.id)}&dateFrom=$from&dateTo=$todayISO"
                    var g = 0
                    while (n != null && g++ < 40) {
                        val j = getJson(http, route, "/v2/transactions$n")
                        fetched.addAll(parseRealTx(a.id, a.kind == "credit", (j["results"] as? JsonArray) ?: JsonArray(emptyList())))
                        n = (j["next"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                    }
                }
            } catch (e: PluggyException) { failedItems.add(itemId); warnings.add("$name: ${e.message}") }
            // investimentos (opcional: se falhar, não derruba o resto)
            try {
                var page = 1; var total = 1
                while (page <= total && page <= 10) {
                    val j = getJson(http, route, "/investments?itemId=${enc(itemId)}&pageSize=500&page=$page")
                    invests.addAll(parseRealInvest(itemId, (j["results"] as? JsonArray) ?: JsonArray(emptyList())))
                    total = (j["totalPages"] as? JsonPrimitive)?.intOrNull ?: 1; page++
                }
            } catch (e: PluggyException) { investFailed.add(itemId) }
        }

        // 2) aplica no estado (mantém o que já havia das conexões que falharam)
        s.realAccounts = (s.realAccounts.filter { it.itemId in failedItems } + accounts).toMutableList()
        s.realLinks = links
        s.realInvest = (s.realInvest.filter { it.itemId in investFailed } + invests).distinctBy { it.id }.toMutableList()
        val known = s.realTx.associateBy { it.id }.toMutableMap()
        var newTx = 0
        fetched.forEach { if (known.put(it.id, it) == null) newTx++ }
        val cutoff = addDaysISO(todayISO, -366)
        s.realTx = known.values.filter { it.date >= cutoff }.sortedByDescending { it.date }.take(3000).toMutableList()
        if (failedItems.isEmpty()) s.realLastSyncISO = todayISO
        s.realSyncError = warnings.firstOrNull()
        val imported = if (s.realToHistory) importRealToHistory(s) else 0
        return PluggySyncResult(accounts.size, newTx, imported, warnings)
    }

    /** Para a interface e o worker: devolve uma mensagem pronta. */
    suspend fun syncNow(ctx: Context, s: AppState): String = withContext(Dispatchers.IO) {
        if (Backend.enabled() && GoogleDrive.isSignedIn(ctx) && !hasCreds(ctx)) return@withContext Backend.syncNow(ctx, s)
        val c = loadCreds(ctx) ?: return@withContext "Informe o client_id e o client_secret primeiro."
        try {
            val r = sync(s, c.first, c.second, loadItemIds(ctx))
            "✓ ${r.accounts} conta(s), ${r.newTx} lançamento(s) novo(s)" + (if (r.imported > 0) ", ${r.imported} levado(s) ao histórico" else "") + "." +
                (if (r.warnings.isNotEmpty()) " Aviso: " + r.warnings.first() else "")
        } catch (e: PluggyException) { s.realSyncError = e.message; e.message ?: "Falha ao sincronizar." }
        catch (e: Exception) { s.realSyncError = "Falha inesperada ao sincronizar."; "Falha inesperada ao sincronizar." }
    }
}
