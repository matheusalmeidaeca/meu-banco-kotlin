package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.PluggyApi
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.data.Backend
import com.meubanco.patrimonio.data.GoogleDrive
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.launch

@Composable
fun ReaisScreen() {
    val s = state(); val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var has by remember { mutableStateOf(PluggyApi.hasCreds(ctx)) }
    var cid by remember { mutableStateOf("") }
    var sec by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ids by remember { mutableStateOf(PluggyApi.loadItemIds(ctx).joinToString("\n")) }
    var idsInfo by remember { mutableStateOf(PluggyApi.loadItemIds(ctx).size) }
    val useBackend = Backend.enabled() && GoogleDrive.isSignedIn(ctx) && !has
    var widgetToken by remember { mutableStateOf<String?>(null) }
    var linkTick by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var ym by remember { mutableStateOf(AppClock.todayISO().take(7)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H2("Contas conectadas")
        Muted("Somente leitura, via Open Finance. O app nunca movimenta dinheiro. Estes são dados reais e ficam separados da simulação.")

        widgetToken?.let { tk ->
            PluggyConnectView(tk,
                onItem = { id ->
                    widgetToken = null
                    if (id.isNotBlank()) { PluggyApi.addItemId(ctx, id); linkTick++; busy = true
                        scope.launch { val m = PluggyApi.syncNow(ctx, Store.state); Store.commit(); busy = false; Ui.show(m) } }
                },
                onError = { m -> widgetToken = null; Ui.show("Não foi possível conectar: $m") },
                onClose = { widgetToken = null })
        }

        if (Backend.enabled() && !has) {
            Card {
                Txt("Conectar banco", bold = true)
                if (!GoogleDrive.isSignedIn(ctx)) {
                    Muted("Primeiro entre com sua conta Google em Configurações → Dados → Conta Google. Depois volte aqui.")
                } else {
                    Muted("Escolha seu banco e autorize o compartilhamento de dados pelo Open Finance. Você pode revogar quando quiser.")
                    PrimaryButton(if (busy) "Aguarde…" else "Conectar um banco", enabled = !busy) {
                        busy = true
                        scope.launch {
                            val r = Backend.connectToken(ctx); busy = false
                            if (r.token != null) widgetToken = r.token else Ui.show(r.error ?: "Não foi possível conectar agora")
                        }
                    }
                    key(linkTick, s.realLinks.size) {
                        s.realLinks.forEach { l ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Muted("🔗 ${l.name}", Modifier.weight(1f))
                                GhostButton("Remover", danger = true) {
                                    scope.launch { val ok = Backend.removeItem(ctx, Store.state, l.itemId); Store.commit(); linkTick++; Ui.show(if (ok) "Conexão removida" else "Não consegui remover agora") }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!has && !useBackend) {
            if (Backend.enabled()) { return@Column }
            Card {
                Txt("Passo a passo", bold = true)
                Muted("1. Meu Pluggy: conta criada e bancos conectados (já feito).")
                Muted("2. Em dashboard.pluggy.ai crie uma conta de desenvolvedor e um aplicativo (Applications → Create).")
                Muted("3. Copie o Client ID e o Client Secret do aplicativo e cole abaixo.")
                Muted("4. No aplicativo de demonstração da Pluggy, escolha o conector MeuPluggy e autorize seus bancos (uma vez por banco).")
                Muted("5. Volte aqui e toque em Sincronizar.")
            }
            Card {
                Field(cid, { cid = it }, "Client ID")
                Field(sec, { sec = it }, "Client Secret")
                Spacer8()
                PrimaryButton("Salvar chaves", enabled = cid.isNotBlank() && sec.isNotBlank()) {
                    PluggyApi.saveCreds(ctx, cid, sec); has = true; cid = ""; sec = ""; Ui.show("Chaves salvas só neste aparelho")
                }
            }
            return@Column
        }

        Card {
            PrimaryButton(if (busy) "Sincronizando…" else "Sincronizar agora", enabled = !busy) {
                busy = true
                scope.launch { val m = PluggyApi.syncNow(ctx, Store.state); Store.commit(); busy = false; Ui.show(m) }
            }
            Muted(s.realLastSyncISO?.let { "Última sincronização: ${formatSimDate(it)}" } ?: "Ainda não sincronizou. Sincroniza sozinho todo dia.")
            s.realSyncError?.let { Txt("⚠ $it", color = P.red) }
            s.realLinks.forEach { l ->
                val exp = l.consentExpiresAt?.take(10)
                if (exp != null && daysBetweenISO(AppClock.todayISO(), exp) in 0..14) Txt("⚠ Autorização de ${l.name} vence em $exp. Autorize de novo na Pluggy.", color = P.red)
                if (l.status.isNotBlank() && l.status !in listOf("UPDATED", "UPDATING")) Muted("${l.name}: ${l.status}")
            }
        }

        if (has) Card {
            Txt("IDs das conexões ($idsInfo salvos)", bold = true)
            Muted("No painel da Pluggy → seu aplicativo Demo → toque em cada conexão MeuPluggy e copie o ID (formato xxxxxxxx-xxxx-…). Cole todos aqui, um por linha.")
            Field(ids, { ids = it }, "IDs (um por linha)", singleLine = false)
            GhostButton("Salvar IDs") { idsInfo = PluggyApi.saveItemIds(ctx, ids); Ui.show("$idsInfo ID(s) salvo(s)") }
        }

        if (s.realAccounts.isNotEmpty()) {
            val b = realBalances(s)
            Card {
                Txt("Saldo em contas: ${money(b.cash)}", bold = true)
                if (b.cardLimit > 0 || b.cardOwed != 0.0) Muted("Cartões: fatura/dívida ${money(b.cardOwed)} de limite ${money(b.cardLimit)}")
                s.realAccounts.forEach { a ->
                    val tag = when (a.kind) { "credit" -> "💳"; "savings" -> "🐖"; else -> "🏦" }
                    Muted("$tag ${a.name}: ${money(a.balance)}" + (a.dueDate?.let { " · vence $it" } ?: ""))
                }
            }

            if (s.realInvest.isNotEmpty()) Card {
                Txt("Investimentos reais: ${money(realInvestTotal(s))}", bold = true)
                realInvestByType(s).forEach { (k, v) -> Muted("• ${investTypeLabel(k)}: ${money(v)}") }
                Spacer8()
                s.realInvest.sortedByDescending { it.balance }.take(25).forEach { i ->
                    Muted("${i.name}: ${money(i.balance)}" + (i.annualRate?.let { " · ${"%.1f".format(it)}% a.a." } ?: "") + (i.dueDate?.let { " · vence $it" } ?: ""))
                }
            }

            val sum = realMonthSummary(s, ym)
            Card {
                Txt("Mês $ym", bold = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("◀") { ym = prevYM(ym) }
                    GhostButton("▶") { ym = nextYM(ym) }
                }
                Muted("Entrou ${money(sum.income)} · Saiu ${money(sum.spent)} (sem pagamento de fatura nem transferências)")
                sum.byCategory.take(8).forEach { (c, v) -> Muted("• $c: ${money(v)}") }
            }

            Card {
                Txt("Usar no app", bold = true)
                Muted("Leva seus lançamentos reais para o histórico (orçamentos, análises). Não altera o saldo simulado.")
                var on by remember { mutableStateOf(s.realToHistory) }
                GhostButton(if (on) "Ligado — tocar para desligar" else "Desligado — tocar para ligar") {
                    on = !on; Store.mutate { it.realToHistory = on }
                    if (on) { val n = importRealToHistory(Store.state); Store.commit(); Ui.show("$n lançamento(s) levados ao histórico") }
                }
            }

            Card {
                Txt("Lançamentos recentes", bold = true)
                Field(q, { q = it }, "Buscar")
                s.realTx.filter { q.isBlank() || it.desc.contains(q, true) || it.category.contains(q, true) }.take(40).forEach { t ->
                    Muted("${t.date.takeLast(5)} · ${t.desc} · ${t.category} · ${money(t.amount)}")
                }
            }
        }

        if (has) Card {
            GhostButton("Remover chaves e dados reais", danger = true) {
                PluggyApi.clearCreds(ctx); has = false
                Store.mutate { it.realAccounts = mutableListOf(); it.realTx = mutableListOf(); it.realLinks = mutableListOf(); it.realInvest = mutableListOf(); it.realLastSyncISO = null; it.realSyncError = null }
                Ui.show("Chaves e dados reais removidos")
            }
        }
    }
}
