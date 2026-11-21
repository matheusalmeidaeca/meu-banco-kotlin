package com.meubanco.patrimonio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.domain.*

@Composable
fun AprenderScreen(goTab: (String) -> Unit) {
    val s = state()
    var sub by remember { mutableStateOf("glossario") }
    var open by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipRow(listOf("glossario" to "Glossário", "golpes" to "Anti-golpe", "sobre" to "Sobre"), sub) { sub = it; open = null }
        when (sub) {
            "glossario" -> GLOSSARY.forEach { (k, v) -> Card(onClick = { open = if (open == k) null else k }) { Txt(k, bold = true); if (open == k) Txt(v, color = P.muted) } }
            "golpes" -> Card {
                H2("🛡 Como evitar golpes")
                SCAM_TIPS.forEach { Txt("• $it") }
                Muted("Se cair em golpe: ligue já para o seu banco, registre um boletim de ocorrência e peça o bloqueio/MED do Pix.")
            }
            else -> Card {
                H2("Meu Banco & Patrimônio")
                KV("Versão", "56.0-kotlin")
                Txt("Controle financeiro pessoal e simulador de investimentos. Não é banco nem corretora e não move dinheiro real.", color = P.muted)
                Txt("Privacidade: os dados ficam somente no seu aparelho. O app acessa a internet só para cotações, CDI e consulta de código de barras.", color = P.muted)
            }
        }
    }
}
