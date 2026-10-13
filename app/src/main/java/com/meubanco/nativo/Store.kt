package com.meubanco.nativo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    /** Contador que sobe a cada alteração, para as telas recomporem. */
    var rev by mutableIntStateOf(0)
        private set

    /** Estado atual; ler por aqui faz a tela atualizar quando algo muda. */
    fun current(): JSONObject? {
        val r = rev
        return if (r >= 0) state else null
    }

    /** Altera o estado no lugar e grava no arquivo (equivale a persist() do app HTML). */
    fun update(block: (JSONObject) -> Unit) {
        val s = state ?: return
        block(s)
        try {
            file.writeText(s.toString())
        } catch (e: Exception) {
        }
        rev++
    }

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
            rev++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun exportText(): String = state?.toString(2) ?: "{}"
}

fun brl(v: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)
