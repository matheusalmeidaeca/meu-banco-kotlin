package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.BankNotif
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

@Composable
fun CapturasScreen() {
    val ctx = LocalContext.current; val s = state()
    var tick by remember { mutableIntStateOf(0) }
    var pasted by remember { mutableStateOf("") }
    val list = remember(tick, Store.rev) { BankNotif.load(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H2("Capturas dos bancos")
        Muted("Sem permissões sensíveis. Compartilhe o aviso ou o comprovante do banco com o Admoney, ou cole o texto aqui, e o app sugere o lançamento. Você confirma. Nada sai do aparelho e o app não movimenta dinheiro.")
        Card {
            Txt("Como usar", bold = true)
            Muted("1. No aviso/comprovante do banco, toque em Compartilhar e escolha Admoney. 2. Volte aqui e toque em Registrar.")
            Field(pasted, { pasted = it }, "Ou cole aqui o texto do aviso")
            PrimaryButton("Criar sugestão", enabled = pasted.isNotBlank()) {
                val cap = BankNotif.parseText(pasted, System.currentTimeMillis())
                if (cap != null) { BankNotif.add(ctx, cap); pasted = ""; tick++ } else Ui.show("Não achei um valor em R$ com entrada ou saída clara.")
            }
        }
        if (list.isEmpty()) Muted("Nenhuma sugestão pendente.")
        list.forEach { c ->
            Card {
                Txt((if (c.kind == "entrada") "⬇ Entrada " else "⬆ Saída ") + formatBRL(c.amount), bold = true)
                Muted("${c.desc} · ${c.bank}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Registrar", Modifier.weight(1f)) {
                        val cat = s.freeSpendCategories.firstOrNull() ?: "Outro"
                        val r = Store.mutate { st -> if (c.kind == "entrada") receivePix(st, c.desc, c.amount) else spendFree(st, c.amount, cat) }
                        handle(r)
                        if (r.ok) { BankNotif.remove(ctx, c.id); tick++ }
                    }
                    GhostButton("Ignorar", Modifier.weight(1f)) { BankNotif.remove(ctx, c.id); tick++ }
                }
            }
        }
    }
}
