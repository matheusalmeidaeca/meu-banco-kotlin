package com.meubanco.patrimonio.ui

import android.content.Intent
import android.provider.Settings
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
    var on by remember { mutableStateOf(BankNotif.listenerEnabled(ctx)) }
    val list = remember(tick, Store.rev) { BankNotif.load(ctx) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); on = BankNotif.listenerEnabled(ctx); val n = BankNotif.load(ctx).size; if (n != list.size) tick++ } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H2("Capturas dos bancos")
        Muted("O app lê as notificações dos seus apps de banco (Nubank, Caixa, Mercado Pago, PicPay, XP, Itaú e outros), sugere o lançamento e você confirma. Tudo é processado só neste aparelho; o app não lê notificações de outros apps e não movimenta dinheiro.")
        Card {
            Txt(if (on) "✓ Leitura de notificações ligada" else "Leitura de notificações desligada", bold = true)
            if (!on) {
                Muted("Toque abaixo, procure o Admoney na lista e ligue o acesso. O Android pede confirmação.")
                PrimaryButton("Ligar leitura de notificações") { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            } else Muted("Quando o banco avisar uma compra ou um Pix, a sugestão aparece aqui.")
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
