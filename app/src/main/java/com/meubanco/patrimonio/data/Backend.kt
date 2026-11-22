package com.meubanco.patrimonio.data

import android.content.Context
import com.meubanco.patrimonio.BuildConfig
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Conexão com os bancos pelo servidor do app (Pluggy Connect). O usuário só entra com Google e escolhe o banco. */
object Backend {
    fun url(): String = BuildConfig.BACKEND_URL.trim().trimEnd('/')
    fun enabled(): Boolean = url().isNotEmpty()
    fun sandbox(): Boolean = BuildConfig.PLUGGY_SANDBOX

    private suspend fun googleToken(ctx: Context): String? = try { GoogleDrive.authorize(ctx).token } catch (e: Exception) { null }

    class ConnectToken(val token: String?, val error: String?)

    /** Pede ao servidor o token que abre a tela de conexão. */
    suspend fun connectToken(ctx: Context, http: PluggyHttp = RealHttp): ConnectToken = withContext(Dispatchers.IO) {
        val g = googleToken(ctx) ?: return@withContext ConnectToken(null, "Entre com sua conta Google de novo (Configurações → Conta Google).")
        val (code, txt) = http.call("POST", url() + "/connect-token", mapOf("Authorization" to "Bearer $g", "Content-Type" to "application/json"), "{}")
        if (code in 200..299 && txt != null) {
            val t = try { Json.parseToJsonElement(txt).jsonObject["accessToken"]?.jsonPrimitive?.contentOrNull } catch (e: Exception) { null }
            if (t != null) return@withContext ConnectToken(t, null)
        }
        ConnectToken(null, when (code) { 401 -> "Entre com sua conta Google de novo."; 429 -> "Muitas tentativas. Aguarde um minuto."; -1 -> "Sem resposta do servidor. Verifique a internet."; else -> "O servidor não conseguiu abrir a conexão agora ($code)" + (try { Json.parseToJsonElement(txt ?: "").jsonObject["error"]?.jsonPrimitive?.contentOrNull?.let { ": $it" } } catch (e: Exception) { null } ?: ".") })
    }

    /** Endereço da página de conexão (abre no navegador do celular). */
    fun connectUrl(token: String): String = url() + "/connect?t=" + java.net.URLEncoder.encode(token, "UTF-8") + (if (sandbox()) "&s=1" else "")

    suspend fun syncNow(ctx: Context, s: AppState): String {
        val g = googleToken(ctx) ?: return "Entre com sua conta Google de novo (Configurações → Conta Google)."
        return try {
            val r = PluggyApi.sync(s, "", "", PluggyApi.loadItemIds(ctx), backend = url() to g)
            "✓ ${r.accounts} conta(s), ${r.newTx} lançamento(s) novo(s)" + (if (r.imported > 0) ", ${r.imported} levado(s) ao histórico" else "") + "." +
                (if (r.warnings.isNotEmpty()) " Aviso: " + r.warnings.first() else "")
        } catch (e: PluggyException) { s.realSyncError = e.message; e.message ?: "Falha ao sincronizar." }
        catch (e: Exception) { s.realSyncError = "Falha inesperada ao sincronizar."; "Falha inesperada ao sincronizar." }
    }

    /** Remove uma conexão na Pluggy e no aparelho. */
    suspend fun removeItem(ctx: Context, s: AppState, itemId: String, http: PluggyHttp = RealHttp): Boolean = withContext(Dispatchers.IO) {
        val g = googleToken(ctx)
        val ok = g != null && http.call("DELETE", url() + "/p/items/$itemId", mapOf("Authorization" to "Bearer $g"), null).first in 200..299
        if (ok) {
            PluggyApi.removeItemId(ctx, itemId)
            s.realLinks = s.realLinks.filter { it.itemId != itemId }.toMutableList()
            s.realAccounts = s.realAccounts.filter { it.itemId != itemId }.toMutableList()
            s.realInvest = s.realInvest.filter { it.itemId != itemId }.toMutableList()
        }
        ok
    }

    /** Página mínima que carrega o widget oficial da Pluggy. */
    fun widgetHtml(connectToken: String, sandbox: Boolean): String {
        val tk = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(connectToken))
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://cdn.pluggy.ai/connect/v2/pluggy-connect.js"></script></head>
<body style="margin:0;background:#0b1020"><script>
window.onerror = function (m) { Android.onError("js: " + m); };
try {
  var pc = new window.PluggyConnect({
    connectToken: $tk, includeSandbox: $sandbox,
    onSuccess: function (d) { Android.onItem(d && d.item ? d.item.id : ""); },
    onError: function (e) { Android.onError(String((e && (e.message || e.code)) || JSON.stringify(e) || e)); },
    onClose: function () { Android.onClose(); }
  });
  pc.init();
} catch (e) { Android.onError("init: " + e); }
</script></body></html>"""
    }
}
