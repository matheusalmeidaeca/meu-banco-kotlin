package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.domain.*

@Composable
fun MaisScreen(goTab: (String) -> Unit, visible: List<String>) {
    val s = state(); val ctx = androidx.compose.ui.platform.LocalContext.current
    val owner = com.meubanco.patrimonio.data.BankNotif.ownerMode(ctx)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H2("Mais")
        listOf(
            Triple("historico", "🧾 Extrato & histórico", "Busca, filtros por mês, PDF e CSV"),
            Triple("smart", "⚡ Lançamento inteligente", "Fale, escreva ou leia um QR Pix"),
            Triple("importar", "📥 Importar extrato", "OFX ou CSV do banco, categoriza sozinho e aprende com você"),
            Triple("lab", "🧪 Laboratório", "Dia Zero, piloto automático, aporte guiado e teste de estresse"),
            Triple("capturas", "🔔 Capturas dos bancos", "Notificações do banco viram sugestões de lançamento"),
            Triple("reais", "🔗 Contas conectadas", "Bancos reais via Meu Pluggy (somente leitura)"),
            Triple("ir", "🧾 Resumo para o IR", "Entradas, dividendos e saldos do ano"),
            Triple("ponto", "⏱ Ponto", "Registro de horas e extras (módulo opcional)"),
            Triple("ajuda", "💡 Aprender & ajuda", "Glossário e proteção contra golpes"),
            Triple("config", "⚙️ Configurações", "Perfil, segurança, notificações, backup"),
        ).filter { it.first in visible && (it.first != "reais" || owner) }.forEach { (k, title, desc) ->
            Card(onClick = { goTab(k) }) { Txt(title, bold = true); Muted(desc) }
        }
        if ("ponto" !in visible) Muted("O módulo Ponto está desligado. Ative em Configurações → Objetivo e abas.")
    }
}
