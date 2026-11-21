package com.meubanco.patrimonio.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.*
import java.io.File

/**
 * Guarda único do estado. `rev` muda a cada alteração, o que faz o Compose redesenhar.
 * O estado é salvo em arquivo JSON (mesmo formato do backup do app em HTML).
 */
object Store {
    var state: AppState = AppState.novo()
        private set
    var rev by mutableIntStateOf(0)
        private set

    private var file: File? = null
    private var appCtx: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    fun init(ctx: Context) {
        if (file != null) return
        appCtx = ctx.applicationContext
        val f = File(ctx.filesDir, "estado.json")
        file = f
        state = try {
            if (f.exists()) decodeState(f.readText()) else AppState.novo()
        } catch (e: Exception) {
            // arquivo corrompido: guarda uma cópia e começa limpo
            try { f.copyTo(File(ctx.filesDir, "estado-corrompido-${System.currentTimeMillis()}.json"), true) } catch (_: Exception) {}
            AppState.novo()
        }
        rev++
    }

    /** Aplica uma alteração e salva. Use sempre para mexer no estado. */
    fun <T> mutate(block: (AppState) -> T): T {
        val r = block(state)
        commit()
        return r
    }

    fun commit() {
        rev++
        val snapshot = file ?: return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(150)
            writeNow()
        }
    }

    fun writeNow() {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, "estado.json.tmp")
            tmp.writeText(encodeState(state))
            if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
        } catch (_: Exception) {}
        appCtx?.let { com.meubanco.patrimonio.widget.BalanceWidget.updateAll(it) }
    }

    fun replace(newState: AppState) {
        state = newState
        commit()
    }

    fun resetAll() {
        state = AppState.novo()
        commit()
    }
}
