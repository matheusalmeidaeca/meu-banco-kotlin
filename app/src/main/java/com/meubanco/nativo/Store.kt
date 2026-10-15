package com.meubanco.nativo

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

/**
 * Guarda o estado do app como JSON num arquivo interno, no mesmo formato do app HTML
 * (o mesmo objeto "state" que ele salvava no localStorage). Ao abrir, avança a simulação
 * até a data real (salário, despesas, fatura, dividendos), como o app HTML fazia.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "state.json")

    var state by mutableStateOf<JSONObject?>(null)
        private set

    /** Contador que sobe a cada alteração, para as telas recomporem. */
    var rev by mutableIntStateOf(0)
        private set

    /** Última sincronização: quantos dias foram processados (para avisar o usuário). */
    var lastSyncDays by mutableIntStateOf(0)
        private set

    /** Estado atual; ler por aqui faz a tela atualizar quando algo muda. */
    fun current(): JSONObject? {
        val r = rev
        return if (r >= 0) state else null
    }

    private fun save() {
        val s = state ?: return
        try {
            file.writeText(s.toString())
        } catch (e: Exception) {
        }
    }

    init {
        val loaded = try {
            if (file.exists()) JSONObject(file.readText()) else null
        } catch (e: Exception) {
            null
        }
        if (loaded != null) {
            state = mergeWithDefaults(loaded)
            syncNow()
        }
    }

    /** Altera o estado no lugar e grava no arquivo (equivale a persist() do app HTML). */
    fun update(block: (JSONObject) -> Unit) {
        val s = state ?: return
        block(s)
        save()
        rev++
    }

    /** Avança a simulação até hoje. Chamado ao abrir e ao voltar para o app. */
    fun syncNow() {
        val s = state ?: return
        val n = try {
            s.syncToRealDate()
        } catch (e: Exception) {
            0
        }
        if (n > 0) {
            lastSyncDays = n
            save()
        }
        rev++
    }

    /** Importa o texto de um backup do app HTML. Retorna false se não parecer um backup válido. */
    fun importText(text: String): Boolean {
        return try {
            val obj = JSONObject(text)
            if (!obj.has("cash") && !obj.has("account")) return false
            state = mergeWithDefaults(obj)
            save()
            syncNow()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Começa uma conta nova, sem backup. */
    fun createNew() {
        state = defaultStateJson().also { it.put("onboardingCompleted", false) }
        save()
        rev++
    }

    /** Apaga tudo e volta ao estado inicial. */
    fun resetAll() {
        state = defaultStateJson().also { it.put("onboardingCompleted", false) }
        save()
        rev++
    }

    fun exportText(): String = state?.toString(2) ?: "{}"

    /** Marca que um backup foi exportado agora. */
    fun markBackup() {
        update { it.put("lastBackupAt", java.time.Instant.now().toString()) }
    }

    /** Busca o CDI atual (BrasilAPI) e usa como rendimento das reservas. */
    fun updateCdi(onDone: (Boolean) -> Unit) {
        Thread {
            var rate = 0.0
            try {
                val conn = URL("https://brasilapi.com.br/api/taxas/v1").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("nome").uppercase() == "CDI") rate = o.optDouble("valor", 0.0)
                }
            } catch (e: Exception) {
                rate = 0.0
            }
            val found = rate
            Handler(Looper.getMainLooper()).post {
                val ok = found > 0.0
                update { s ->
                    val mk = s.sub("market")
                    if (ok) {
                        s.sub("reserveConfig").put("annualRatePct", found)
                        mk.put("cdiUpdatedAt", todayISO())
                        mk.put("cdiError", JSONObject.NULL)
                    } else {
                        mk.put("cdiError", "Não foi possível atualizar o CDI agora.")
                    }
                }
                onDone(ok)
            }
        }.start()
    }
}

fun brl(v: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)
