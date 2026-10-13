package com.meubanco.nativo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * Guarda o estado do app como JSON num arquivo interno, no mesmo formato do app HTML
 * (o mesmo objeto "state" que ele salvava no localStorage). Assim o backup antigo
 * é lido sem conversão e as próximas etapas leem os mesmos campos.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "state.json")

    var state by mutableStateOf<JSONObject?>(null)
        private set

    init {
        state = try {
            if (file.exists()) JSONObject(file.readText()) else null
        } catch (e: Exception) {
            null
        }
    }

    /** Importa o texto de um backup do app HTML. Retorna false se não parecer um backup válido. */
    fun importText(text: String): Boolean {
        return try {
            val obj = JSONObject(text)
            if (!obj.has("cash") && !obj.has("account")) return false
            file.writeText(obj.toString())
            state = obj
            true
        } catch (e: Exception) {
            false
        }
    }

    fun exportText(): String = state?.toString(2) ?: "{}"
}

fun brl(v: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)
