package com.meubanco.patrimonio.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.meubanco.patrimonio.domain.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Conta Google + backup na pasta ESCONDIDA do app no Google Drive da própria pessoa (escopo drive.appdata).
 * Nada passa por servidor nosso. O app só enxerga o que ele mesmo gravou ali.
 */
object GoogleDrive {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val FILE = "meu-banco-backup.json"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("google", Context.MODE_PRIVATE)
    fun email(ctx: Context): String? = prefs(ctx).getString("email", null)
    fun isSignedIn(ctx: Context) = email(ctx) != null
    fun lastUpload(ctx: Context): String? = prefs(ctx).getString("last", null)
    fun signOut(ctx: Context) { prefs(ctx).edit().clear().apply(); try { photoFile(ctx).delete() } catch (_: Exception) {} }
    fun name(ctx: Context): String? = prefs(ctx).getString("name", null)
    fun photoFile(ctx: Context) = java.io.File(ctx.filesDir, "foto-google.jpg")
    fun saveEmail(ctx: Context, e: String) { prefs(ctx).edit().putString("email", e).apply() }
    private fun markUploaded(ctx: Context) { prefs(ctx).edit().putString("last", java.time.Instant.ofEpochMilli(AppClock.nowMs()).toString()).apply() }

    class Auth(val token: String?, val pending: PendingIntent?)

    /** Pede acesso. Se já foi autorizado, devolve o token direto; senão devolve a tela de consentimento. */
    suspend fun authorize(ctx: Context): Auth = suspendCoroutine { c ->
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(ctx).authorize(req)
            .addOnSuccessListener { r -> c.resume(if (r.hasResolution()) Auth(null, r.pendingIntent) else Auth(r.accessToken, null)) }
            .addOnFailureListener { e -> c.resumeWithException(e) }
    }

    fun tokenFromIntent(ctx: Context, data: Intent?): String? = try {
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data).accessToken
    } catch (e: Exception) { null }

    private fun hdr(token: String, extra: Map<String, String> = emptyMap()) = mapOf("Authorization" to "Bearer $token") + extra

    fun whoAmI(token: String, http: PluggyHttp = RealHttp): String? {
        val (code, txt) = http.call("GET", "$API/about?fields=user(emailAddress,displayName)", hdr(token), null)
        if (code !in 200..299 || txt == null) return null
        return try { Json.parseToJsonElement(txt).jsonObject["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.contentOrNull } catch (e: Exception) { null }
    }

    class Profile(val email: String, val name: String?, val photoUrl: String?)

    /** Perfil da conta Google (e-mail, nome e foto). */
    fun profile(token: String, http: PluggyHttp = RealHttp): Profile? {
        val (code, txt) = http.call("GET", "$API/about?fields=user(emailAddress,displayName,photoLink)", hdr(token), null)
        if (code !in 200..299 || txt == null) return null
        return try {
            val u = Json.parseToJsonElement(txt).jsonObject["user"]?.jsonObject ?: return null
            val mail = u["emailAddress"]?.jsonPrimitive?.contentOrNull ?: return null
            Profile(mail, u["displayName"]?.jsonPrimitive?.contentOrNull, u["photoLink"]?.jsonPrimitive?.contentOrNull)
        } catch (e: Exception) { null }
    }

    private fun downloadPhoto(ctx: Context, url: String) {
        try {
            val big = url.replace(Regex("=s\\d+(-c)?$"), "=s256-c")
            val c = java.net.URL(big).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000
            if (c.responseCode in 200..299) c.inputStream.use { i -> photoFile(ctx).outputStream().use { o -> i.copyTo(o) } }
        } catch (_: Exception) {}
    }

    private fun findId(token: String, http: PluggyHttp): String? {
        val q = java.net.URLEncoder.encode("name='$FILE'", "UTF-8")
        val (code, txt) = http.call("GET", "$API/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)&orderBy=modifiedTime%20desc", hdr(token), null)
        if (code !in 200..299 || txt == null) throw RuntimeException("Drive respondeu $code")
        return Json.parseToJsonElement(txt).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    /** Envia (cria ou substitui) o backup. */
    fun upload(token: String, json: String, http: PluggyHttp = RealHttp) {
        val id = findId(token, http)
        val (code, _) = if (id == null) {
            val b = "meubancoXbound"
            val body = "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"$FILE\",\"parents\":[\"appDataFolder\"]}\r\n--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n--$b--"
            http.call("POST", "$UPLOAD/files?uploadType=multipart", hdr(token, mapOf("Content-Type" to "multipart/related; boundary=$b")), body)
        } else http.call("POST", "$UPLOAD/files/$id?uploadType=media", hdr(token, mapOf("Content-Type" to "application/json; charset=UTF-8", "X-HTTP-Method-Override" to "PATCH")), json)
        if (code !in 200..299) throw RuntimeException("Drive respondeu $code ao gravar")
    }

    /** Baixa o backup, ou null se ainda não existe. */
    fun download(token: String, http: PluggyHttp = RealHttp): String? {
        val id = findId(token, http) ?: return null
        val (code, txt) = http.call("GET", "$API/files/$id?alt=media", hdr(token), null)
        if (code !in 200..299) throw RuntimeException("Drive respondeu $code ao ler")
        return txt
    }

    /** Backup completo do estado atual. Mensagem pronta para a tela. */
    suspend fun backupNow(ctx: Context, token: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try { upload(token, encodeState(Store.state, true)); markUploaded(ctx); "Backup enviado ao seu Google Drive" }
        catch (e: Exception) { "Não consegui enviar (${e.message ?: "sem rede"})" }
    }

    suspend fun restoreNow(token: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val txt = download(token) ?: return@withContext "Ainda não há backup no seu Drive"
            Store.replace(decodeState(txt)); "Backup restaurado do Drive"
        } catch (e: Exception) { "Não consegui restaurar (${e.message ?: "sem rede"})" }
    }

    /**
     * Depois do login: se o app está "zerado" (instalação nova) e já existe backup no Drive, restaura sozinho.
     * Se o app já tem dados, só liga o backup automático e envia uma cópia.
     */
    suspend fun loginSync(ctx: Context, token: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val pr = profile(token) ?: return@withContext "Não consegui confirmar a conta"
            val mail = pr.email
            saveEmail(ctx, mail)
            prefs(ctx).edit().putString("name", pr.name).apply()
            pr.photoUrl?.let { downloadPhoto(ctx, it) }
            val first = pr.name?.trim()?.split(" ")?.firstOrNull().orEmpty()
            if (Store.state.onboardingCompleted == false) {
                val txt = download(token)
                if (txt != null) { Store.replace(decodeState(txt)); return@withContext "Bem-vindo de volta! Seus dados foram restaurados." }
                if (first.isNotEmpty() && Store.state.settings.displayName.isBlank()) Store.mutate { it.settings.displayName = first }
                "Olá, ${first.ifEmpty { mail }}! Seus dados serão salvos sozinhos no seu Drive."
            } else {
                if (first.isNotEmpty() && Store.state.settings.displayName.isBlank()) Store.mutate { it.settings.displayName = first }
                backupNow(ctx, token); "Conectado: $mail. Backup automático ligado."
            }
        } catch (e: Exception) { "Não consegui acessar o Drive (${e.message ?: "sem rede"})" }
    }

    /** Para o worker diário: sem tela. Se a autorização exigir tela, simplesmente não faz nada. */
    suspend fun dailyBackup(ctx: Context) {
        if (!isSignedIn(ctx)) return
        try { val a = authorize(ctx); if (a.token != null) backupNow(ctx, a.token) } catch (e: Exception) { }
    }
}
