package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.domain.*

@Composable
fun MaisScreen(goTab: (String) -> Unit, visible: List<String>) {
    val s = state()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H2("Mais")
        val undoN = com.meubanco.patrimonio.data.Store.undoCount
        if (undoN > 0) {
            var askUndo by remember { mutableStateOf(false) }
            Card { Txt("↩ Errou alguma coisa?", bold = true); Muted("Desfaça a última alteração feita no app ($undoN disponíveis nesta sessão).")
                GhostButton("Desfazer última alteração") { askUndo = true } }
            if (askUndo) Confirm("Voltar o app ao estado de antes da última alteração?", { askUndo = false; Ui.show(if (com.meubanco.patrimonio.data.Store.undoLast()) "Alteração desfeita." else "Nada para desfazer.") }, { askUndo = false }, "Desfazer")
        }
        listOf(
            Triple("historico", "🧾 Extrato & histórico", "Busca, filtros por mês, PDF e CSV"),
            Triple("lab", "🧪 Laboratório", "Saúde financeira, radar, piloto automático e simulações"),
            Triple("ponto", "⏱ Ponto", "Registro de horas e extras (módulo opcional)"),
            Triple("config", "⚙️ Configurações", "Perfil, segurança, avisos, backup, aprender e IR"),
        ).filter { it.first in visible }.forEach { (k, title, desc) ->
            Card(onClick = { goTab(k) }) { Txt(title, bold = true); Muted(desc) }
        }
        if ("ponto" !in visible) Muted("O módulo Ponto está desligado. Ative em Configurações → Objetivo e abas.")
    }
}
