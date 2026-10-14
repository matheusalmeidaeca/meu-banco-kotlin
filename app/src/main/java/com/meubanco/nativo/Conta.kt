package com.meubanco.nativo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// ============================================================
// Aba "Conta & cartão"
// ============================================================

@Composable
fun ContaPanel(store: Store, onImport: () -> Unit, goTo: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var sheet by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf("all") }
    var showPatr by rememberSaveable { mutableStateOf(false) }

    if (st == null) {
        CardBox {
            Text("Nenhum dado ainda", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Se você usava o app HTML: abra Configurações → Exportar backup lá, depois toque abaixo e escolha o arquivo. Ou comece uma conta nova.",
                color = p.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("⬆ Importar backup", onClick = onImport)
            Spacer(Modifier.height(8.dp))
            GhostButton("Começar conta nova") { store.createNew() }
        }
        return
    }

    val name = st.sub("settings").str("displayName")
    if (name.isNotEmpty()) {
        Text("Olá, $name", color = p.muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
    }

    // ---- saldo ----
    val heroShape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(Brush.linearGradient(listOf(p.brandDim, p.bgSoft)))
            .border(1.dp, p.border, heroShape)
            .padding(22.dp),
    ) {
        Column {
            Text("Saldo em conta corrente", color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(st.maskBrl(st.cash), color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val acc = st.sub("account")
            Text("Agência ${acc.str("agency")} · Conta ${acc.str("number")}", color = p.muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row {
                Text("Patrimônio total  ", color = p.muted, fontSize = 12.sp)
                Text(st.maskBrl(st.totalNetWorth()), color = p.emerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .clip(CircleShape)
                .clickable {
                    store.update { it.sub("settings").put("hideBalance", !it.hideBalance()) }
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (st.hideBalance()) "🙈" else "👁", fontSize = 18.sp) }
    }
    Spacer(Modifier.height(14.dp))

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction("🐷", "Caixinhas") { sheet = "caixinhas" }
        QuickAction("🔁", "Pix") { sheet = "pix" }
        QuickAction("💳", "Cartão") { sheet = "cardmgr" }
        QuickAction("📄", "Extrato") { goTo("historico") }
        QuickAction("📈", "Investir") { goTo("carteira") }
        QuickAction("🛒", "Compras") { goTo("gastos") }
    }
    Spacer(Modifier.height(16.dp))

    // ---- assistente ----
    if (st.sub("settings").optBoolean("assistantEnabled", true)) {
        val ins = st.computeInsights()
        if (ins.isNotEmpty()) {
            CardBox {
                BoxTitle("🤖 Assistente", "${ins.size} aviso(s) e dica(s) para você")
                ins.take(6).forEachIndexed { i, it ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text(it.ico, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(it.desc, color = p.muted, fontSize = 12.5.sp)
                        }
                        if (it.goTab != null && it.goLabel != null) {
                            Spacer(Modifier.width(8.dp))
                            GChip(it.goLabel) {
                                if (it.goTab == "conta") {
                                    sheet = "cardmgr"
                                } else {
                                    goTo(it.goTab)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    // ---- cartão ----
    if (st.sub("card").optBoolean("enabled", true)) {
        CardSummary(store, st, onBuy = { sheet = "cardbuy" }, onManage = { sheet = "cardmgr" })
        Spacer(Modifier.height(14.dp))
    }

    // ---- patrimônio ----
    CardBox {
        BoxTitle("Patrimônio", "Tudo o que você tem, num só lugar")
        MoneyRow("Carteira de dividendos", st.maskBrl(st.portfolioValue() + st.leftover), p.emerald)
        MoneyRow("Reservas", st.maskBrl(st.reservesTotalBalance()), p.sapphire)
        MoneyRow("Guardado em metas", st.maskBrl(st.goalsTotalSaved()))
        MoneyRow("FGTS (não sacável)", st.maskBrl(st.sub("fgts").optDouble("balance", 0.0)))
        Helper("O FGTS não entra no patrimônio total acima.")
        Spacer(Modifier.height(6.dp))
        GhostButton(if (showPatr) "Ocultar patrimônio completo" else "Ver patrimônio completo") { showPatr = !showPatr }
        if (showPatr) {
            Spacer(Modifier.height(8.dp))
            MoneyRow("Conta corrente", st.maskBrl(st.cash))
            MoneyRow("Conta de investimentos", st.maskBrl(st.leftover))
            MoneyRow("Ativos (ações e FIIs)", st.maskBrl(st.portfolioValue()))
            MoneyRow("Reservas", st.maskBrl(st.reservesTotalBalance()))
            MoneyRow("Metas", st.maskBrl(st.goalsTotalSaved()))
            MoneyRow("Total", st.maskBrl(st.totalNetWorth()), p.gold, bold = true)
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- evolução do patrimônio ----
    val full = st.objList("netWorthHistory")
    if (full.size >= 2) {
        val n = when (range) { "1m" -> 2; "6m" -> 7; "1a" -> 13; else -> full.size }
        val hist = full.takeLast(Math.min(n, full.size))
        val tot = hist.map { it.optDouble("value", 0.0) }
        val inv = hist.map { if (it.has("invested")) it.optDouble("invested", 0.0) else it.optDouble("value", 0.0) }
        val delta = r2(tot.last() - tot.first())
        val pct = if (tot.first() != 0.0) (tot.last() / tot.first() - 1) * 100 else 0.0
        val up = delta >= 0
        CardBox {
            BoxTitle("Evolução do patrimônio", "Um ponto por mês")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1m" to "1M", "6m" to "6M", "1a" to "1A", "all" to "Tudo").forEach { (k, l) ->
                    GChip(l, range == k) { range = k }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (st.hideBalance()) "••••" else (if (up) "+" else "−") + brl(Math.abs(delta)) + "  (" + String.format(java.util.Locale("pt", "BR"), "%.1f", pct) + "%)",
                color = if (up) p.emerald else p.coral,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            TwoLineChart(tot, inv, if (up) p.emerald else p.coral, p.gold)
            Row(Modifier.fillMaxWidth()) {
                Text(monthShort(hist.first().str("ym")), color = p.mutedDim, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                Text(monthShort(hist.last().str("ym")), color = p.mutedDim, fontSize = 10.5.sp)
            }
            Helper("Linha dourada: só o que está investido (carteira, reservas e metas).")
        }
        Spacer(Modifier.height(14.dp))
    }

    // ---- movimentações do banco ----
    val bankTypes = setOf("pix", "cartao", "fatura", "transferencia")
    val activity = st.objList("history").filter { it.str("type") in bankTypes }.take(6)
    CardBox {
        BoxTitle("Últimas movimentações", "Pix, cartão e transferências")
        if (activity.isEmpty()) {
            Text("Nenhuma movimentação de Pix, cartão ou transferência ainda.", color = p.muted, fontSize = 13.sp)
        } else {
            activity.forEachIndexed { i, h ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                HistoryRow(h)
            }
        }
    }

    // ---- janelas ----
    when (sheet) {
        "pix" -> PixSheet(store) { sheet = "" }
        "caixinhas" -> CaixinhasSheet(store) { sheet = "" }
        "cardbuy" -> CardPurchaseSheet(store) { sheet = "" }
        "cardmgr" -> CardManageSheet(store) { sheet = "" }
    }
}

@Composable
fun HistoryRow(h: JSONObject) {
    val p = LocalPalette.current
    val a = h.optDouble("amount", 0.0)
    val col = if (a > 0) p.emerald else if (a < 0) p.coral else p.muted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(p.cardAlt),
            contentAlignment = Alignment.Center,
        ) { Text(histIcon(h.str("type")), fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(h.str("label"), color = p.text, fontSize = 13.sp)
            Text(ddmmyyyy(h.str("ts")), color = p.mutedDim, fontSize = 11.sp)
        }
        if (a != 0.0) {
            Spacer(Modifier.width(8.dp))
            Text(
                (if (a > 0) "+" else "−") + brl(Math.abs(a)),
                color = col,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

fun histIcon(type: String): String = when (type) {
    "dividendo" -> "💰"
    "aporte" -> "⬆"
    "saque" -> "⬇"
    "compra" -> "🧾"
    "cdb" -> "🏦"
    "meta" -> "🎯"
    "conquista" -> "🏆"
    "aviso" -> "⚠"
    "renda" -> "💵"
    "despesa" -> "📤"
    "gastolivre" -> "🎉"
    "pix" -> "🔁"
    "cartao" -> "💳"
    "fatura" -> "🧾"
    "transferencia" -> "↔"
    "venda" -> "💱"
    "imposto" -> "🏛"
    else -> "•"
}

// ============================================================
// Cartão de crédito
// ============================================================

@Composable
fun CardSummary(store: Store, st: JSONObject, onBuy: () -> Unit, onManage: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    val cards = st.allCards()
    val idx = st.optInt("activeCardIdx", 0).coerceIn(0, cards.size - 1)
    val c = st.cardAt(idx)
    val pend = c.optDouble("pendingAmount", 0.0)
    val cur = c.optDouble("invoiceCurrent", 0.0)
    val shape = RoundedCornerShape(20.dp)

    if (cards.size > 1) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEachIndexed { i, cd ->
                GChip(cd.str("name").ifEmpty { "Cartão ${i + 1}" }, i == idx) { store.update { it.put("activeCardIdx", i) } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.brand, p.brandDim)))
            .padding(20.dp),
    ) {
        Column {
            Text(c.str("name").ifEmpty { "meu banco" }, color = p.brandInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text("•••• •••• •••• ${c.str("last4")}", color = p.brandInk, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(14.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Fatura atual", color = p.brandInk, fontSize = 11.sp)
                    Text(st.maskBrl(cur), color = p.brandInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Limite disponível", color = p.brandInk, fontSize = 11.sp)
                    Text(st.maskBrl(st.cardAvailable(c)), color = p.brandInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Fecha dia ${c.optInt("closingDay", 25)} · vence dia ${c.optInt("dueDay", 5)}",
                color = p.brandInk,
                fontSize = 11.5.sp,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (pend > 0) {
        WarnBox("Fatura fechada de ${brl(pend)} — será debitada da conta corrente no dia ${c.optInt("dueDay", 5)}.")
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("+ Compra", Modifier.weight(1f), onBuy)
        GhostButton("Pagar fatura", Modifier.weight(1f)) {
            if (pend <= 0) {
                showToast(ctx, "Não há fatura fechada pendente pra pagar agora.")
            } else if (st.cash <= 0) {
                showToast(ctx, "Seu saldo em conta está zerado.")
            } else if (st.cash < pend) {
                confirm = "Seu saldo (${brl(st.cash)}) é menor que a fatura (${brl(pend)}). Pagar ${brl(st.cash)} agora, o que der?"
            } else {
                store.act(ctx, "Fatura paga") { it.payInvoiceNow(idx) }
            }
        }
        GhostButton("⚙", Modifier.width(56.dp), onManage)
    }
    confirm?.let { msg ->
        GConfirm(
            msg,
            onYes = {
                store.act(ctx, "Fatura paga parcialmente") { it.payInvoiceNow(idx) }
                confirm = null
            },
            onNo = { confirm = null },
        )
    }
}

@Composable
fun CardPurchaseSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var inst by remember { mutableStateOf("1") }
    val idx = st?.optInt("activeCardIdx", 0) ?: 0
    val avail = if (st != null) st.cardAvailable(st.cardAt(idx)) else 0.0
    val v = parseNum(amount)
    val n = (inst.toIntOrNull() ?: 1).coerceIn(1, 24)

    GSheet("Compra no cartão", onClose) {
        Text("Limite disponível: ${brl(avail)}", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        GField("Descrição", desc, { desc = it }, placeholder = "Ex.: Celular")
        Spacer(Modifier.height(8.dp))
        GField("Valor total (R$)", amount, { amount = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        GField("Parcelas (1 a 24)", inst, { inst = it.filter { ch -> ch.isDigit() }.take(2) }, keyboard = KeyboardType.Number)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 2, 3, 6, 10, 12).forEach { k -> GChip("${k}x", n == k) { inst = k.toString() } }
        }
        if (v > 0) Helper("$n parcela(s) de ${brl(r2(v / n))}")
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Lançar compra") {
            if (store.act(ctx, "Compra lançada") { it.addCardPurchase(idx, desc, v, n) }) onClose()
        }
    }
}

@Composable
fun CardManageSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    if (st == null) return
    val cards = st.allCards()
    val idx = st.optInt("activeCardIdx", 0).coerceIn(0, cards.size - 1)
    val c = st.cardAt(idx)

    var name by remember(idx) { mutableStateOf(c.str("name")) }
    var limit by remember(idx) { mutableStateOf(numText(c.optDouble("limit", 0.0))) }
    var closing by remember(idx) { mutableStateOf(c.optInt("closingDay", 25).toString()) }
    var due by remember(idx) { mutableStateOf(c.optInt("dueDay", 5).toString()) }

    GSheet("Cartão de crédito", onClose) {
        if (cards.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEachIndexed { i, cd ->
                    GChip(cd.str("name").ifEmpty { "Cartão ${i + 1}" }, i == idx) { store.update { it.put("activeCardIdx", i) } }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (idx == 0) {
            Toggle("Tenho cartão de crédito", st.sub("card").optBoolean("enabled", true)) { on ->
                store.update { it.sub("card").put("enabled", on) }
            }
        }
        GField("Nome do cartão", name, { name = it })
        Spacer(Modifier.height(8.dp))
        GField("Limite (R$)", limit, { limit = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GField("Dia de fechamento", closing, { closing = it.filter { ch -> ch.isDigit() }.take(2) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            GField("Dia de vencimento", due, { due = it.filter { ch -> ch.isDigit() }.take(2) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Salvar cartão") {
            store.update {
                it.updateCardConfig(idx, name, parseNum(limit), closing.toIntOrNull() ?: 25, due.toIntOrNull() ?: 5)
            }
            showToast(ctx, "Cartão salvo")
        }
        Spacer(Modifier.height(16.dp))
        Text("Compras parceladas em aberto", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        val purchases = c.objList("purchases")
        if (purchases.isEmpty()) {
            Text("Nenhuma compra parcelada em aberto.", color = p.muted, fontSize = 13.sp)
        } else {
            purchases.forEach { pu ->
                val total = pu.optInt("installmentsTotal", 1)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pu.str("desc"), color = p.text, fontSize = 14.sp)
                        if (total > 1) Text("${pu.optInt("installmentsPaid")}/$total", color = p.mutedDim, fontSize = 11.sp)
                    }
                    Text(brl(pu.optDouble("installmentValue", 0.0)), color = p.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    RowMenu(
                        listOf(
                            MenuAction("🗑 Excluir compra", true) {
                                confirm = "Excluir esta compra do cartão?" to { store.update { it.removeCardPurchase(idx, pu.optInt("id")) } }
                            },
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GhostButton("+ Adicionar outro cartão") { store.update { it.addExtraCard() } }
        if (cards.size > 1) {
            Spacer(Modifier.height(8.dp))
            GhostButton("Remover este cartão") {
                val open = c.optDouble("pendingAmount", 0.0) > 0 || c.optDouble("invoiceCurrent", 0.0) > 0
                confirm = (if (open) "Esse cartão ainda tem fatura em aberto. Remover mesmo assim?" else "Remover este cartão?") to
                    { store.update { it.removeCardAt(idx) } }
            }
        }
    }
    confirm?.let { (msg, run) ->
        GConfirm(msg, onYes = { run(); confirm = null }, onNo = { confirm = null })
    }
}

// ============================================================
// Pix
// ============================================================

@Composable
fun PixSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var step by remember { mutableStateOf("choice") }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val threshold = st?.sub("settings")?.optDouble("pixConfirmThreshold", 0.0) ?: 0.0
    val v = parseNum(amount)

    val title = when (step) {
        "send" -> "Enviar Pix"
        "receive" -> "Receber Pix"
        "confirm" -> "Confirmar Pix"
        else -> "Pix"
    }
    GSheet(title, onClose) {
        Text("Saldo disponível: ${brl(st?.cash ?: 0.0)}", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        when (step) {
            "choice" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("↓ Receber", Modifier.weight(1f)) { step = "receive" }
                    PrimaryButton("↑ Enviar", Modifier.weight(1f)) { step = "send" }
                }
            }
            "confirm" -> {
                Text("Esse Pix é de valor alto — confirma o envio de ${brl(v)} para ${name.trim()}?", color = p.text, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Sim, confirmar Pix") {
                    if (store.act(ctx, "Pix enviado") { it.sendPix(name, v) }) onClose()
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("← Voltar") { step = "send" }
            }
            else -> {
                val send = step == "send"
                GField("Nome", name, { name = it }, placeholder = if (send) "Destinatário" else "Remetente")
                Spacer(Modifier.height(8.dp))
                GField("Valor (R$)", amount, { amount = it }, keyboard = KeyboardType.Decimal)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(if (send) "Confirmar Pix" else "Receber Pix") {
                    if (name.isBlank() || v <= 0) {
                        showToast(ctx, "Informe o nome e o valor.")
                    } else if (send) {
                        val err = st?.pixPrecheck(v)
                        if (err != null) {
                            showToast(ctx, err)
                        } else if (threshold > 0 && v >= threshold) {
                            step = "confirm"
                        } else if (store.act(ctx, "Pix enviado") { it.sendPix(name, v) }) {
                            onClose()
                        }
                    } else if (store.act(ctx, "Pix recebido") { it.receivePix(name, v) }) {
                        onClose()
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("← Voltar") { step = "choice" }
            }
        }
    }
}

// ============================================================
// Caixinhas (reservas e metas)
// ============================================================

@Composable
fun CaixinhasSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    val amounts = remember { mutableStateMapOf<String, String>() }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newMonthly by remember { mutableStateOf("") }
    var newTarget by remember { mutableStateOf("") }
    var editReserve by remember { mutableStateOf<Int?>(null) }
    var editGoal by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var cdiBusy by remember { mutableStateOf(false) }
    val rate = st?.sub("reserveConfig")?.optDouble("annualRatePct", 11.0) ?: 11.0
    var rateText by remember { mutableStateOf(numText(rate)) }

    GSheet("Caixinhas", onClose) {
        if (st == null) return@GSheet
        Text("Saldo disponível na conta: ${brl(st.cash)}", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        // taxa (CDI)
        val mk = st.sub("market")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GField("Rendimento das reservas (% a.a.)", rateText, { rateText = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            GhostButton("Aplicar", Modifier.width(90.dp)) {
                val r = parseNum(rateText)
                if (r < 0) showToast(ctx, "Taxa inválida.") else store.update { it.sub("reserveConfig").put("annualRatePct", r) }
            }
        }
        Spacer(Modifier.height(6.dp))
        val cdiMsg = when {
            cdiBusy -> "Atualizando o CDI…"
            mk.str("cdiError").isNotEmpty() -> mk.str("cdiError")
            mk.str("cdiUpdatedAt").isNotEmpty() -> "CDI atualizado em ${ddmmyyyy(mk.str("cdiUpdatedAt"))}"
            else -> "Ainda não atualizado"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cdiMsg, color = p.mutedDim, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            GChip("↻ Atualizar CDI") {
                if (!cdiBusy) {
                    cdiBusy = true
                    store.updateCdi { ok ->
                        cdiBusy = false
                        showToast(ctx, if (ok) "CDI atualizado" else "Não foi possível atualizar o CDI agora")
                        store.current()?.let { s -> rateText = numText(s.sub("reserveConfig").optDouble("annualRatePct", 11.0)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        val reserves = st.objList("reserves")
        val goals = st.objList("goals")
        if (reserves.isEmpty() && goals.isEmpty()) {
            Text("Nenhuma caixinha criada ainda. Crie uma reserva logo abaixo — viagens, sofá novo, o que precisar.", color = p.muted, fontSize = 13.sp)
        }

        reserves.forEach { r ->
            val id = r.optInt("id")
            val key = "r$id"
            val target = r.optDouble("targetValue", 0.0)
            val bal = r.optDouble("balance", 0.0)
            val daily = (Math.pow(1 + rate / 100, 1.0 / 365) - 1) * 100
            val est = st.reserveWithdrawEstimate(r, bal)
            CardBox {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("🏦 ${r.optString("name")}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Reserva · " + String.format(java.util.Locale("pt", "BR"), "%.1f%% a.a. · %.3f%%/dia", rate, daily) +
                                (if (r.optDouble("monthlyContribution", 0.0) > 0) " · aporte de ${brl(r.optDouble("monthlyContribution", 0.0))}" else ""),
                            color = p.muted,
                            fontSize = 11.5.sp,
                        )
                    }
                    RowMenu(
                        listOf(
                            MenuAction("✏️ Editar") { editReserve = id },
                            MenuAction("🗑 Excluir", true) {
                                confirm = "Excluir a reserva \"${r.optString("name")}\"? O saldo dela não volta para a conta." to { store.update { it.removeReserve(id) } }
                            },
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (target > 0) {
                    ProgressBar(bal / target, p.gold)
                    Spacer(Modifier.height(4.dp))
                    Text("${brl(bal)} de ${brl(target)} · ${String.format(java.util.Locale("pt", "BR"), "%.1f", Math.min(100.0, bal / target * 100))}%", color = p.muted, fontSize = 12.sp)
                } else {
                    Text(brl(bal), color = p.gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                if (bal > 0) {
                    Helper(
                        if (est.ir > 0) {
                            "Se resgatar tudo agora: ${brl(est.net)} líquido (IR regressivo de ${String.format(java.util.Locale("pt", "BR"), "%.1f", est.aliquota * 100)}% sobre ${brl(est.yieldPortion)} de rendimento)"
                        } else {
                            "Sem rendimento acumulado ainda — nenhum IR a descontar num resgate agora."
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                GField("", amounts[key] ?: "", { amounts[key] = it }, placeholder = "Valor (R$)", keyboard = KeyboardType.Decimal)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Guardar", Modifier.weight(1f)) {
                        if (store.act(ctx, "Valor guardado") { it.reserveDeposit(id, parseNum(amounts[key] ?: "")) }) amounts[key] = ""
                    }
                    GhostButton("Resgatar", Modifier.weight(1f)) {
                        if (store.act(ctx, "Valor resgatado") { it.reserveWithdraw(id, parseNum(amounts[key] ?: "")) }) amounts[key] = ""
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        goals.forEach { g ->
            val id = g.optInt("id")
            val key = "g$id"
            val target = g.optDouble("target", 0.0)
            val saved = g.optDouble("saved", 0.0)
            val done = g.optBoolean("completed", false)
            val months = st.estimateMonthsToGoal(g)
            val eta = when {
                done -> "Concluída"
                months == null -> "Sem previsão — defina um aporte mensal"
                months == 0 -> "Meta batida"
                else -> "≈ $months " + (if (months == 1) "mês" else "meses") + " no ritmo atual"
            }
            CardBox {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("${goalIcon(g.str("type"))} ${g.optString("name")}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Meta · ${goalLabel(g.str("type"))}" + (if (g.optDouble("rate", 0.0) > 0) " · ${numText(g.optDouble("rate", 0.0))}% a.a." else "") + (if (done) " · concluída" else ""),
                            color = p.muted,
                            fontSize = 11.5.sp,
                        )
                    }
                    RowMenu(
                        listOf(
                            MenuAction("✏️ Editar") { editGoal = id },
                            MenuAction("🗑 Excluir", true) {
                                confirm = "Excluir a meta \"${g.optString("name")}\"?" to { store.update { it.removeGoal(id) } }
                            },
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(if (target > 0) saved / target else 0.0)
                Spacer(Modifier.height(4.dp))
                Text("${brl(saved)} de ${brl(target)}", color = p.muted, fontSize = 12.sp)
                Helper(eta + (if (g.optDouble("monthly", 0.0) > 0) " · aporte de ${brl(g.optDouble("monthly", 0.0))}/mês" else ""))
                if (!done) {
                    Spacer(Modifier.height(8.dp))
                    GField("", amounts[key] ?: "", { amounts[key] = it }, placeholder = "Aporte extra (R$)", keyboard = KeyboardType.Decimal)
                    Spacer(Modifier.height(6.dp))
                    PrimaryButton("Aportar agora") {
                        if (store.act(ctx, "Aporte feito") { it.goalDeposit(id, parseNum(amounts[key] ?: "")) }) amounts[key] = ""
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (!showNew) {
            GhostButton("+ Nova reserva") { showNew = true }
        } else {
            CardBox {
                BoxTitle("Nova reserva")
                GField("Nome", newName, { newName = it }, placeholder = "Ex.: Sofá novo")
                Spacer(Modifier.height(8.dp))
                GField("Aporte mensal (R$)", newMonthly, { newMonthly = it }, keyboard = KeyboardType.Decimal)
                Spacer(Modifier.height(8.dp))
                GField("Meta — valor alvo (opcional)", newTarget, { newTarget = it }, keyboard = KeyboardType.Decimal)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Criar", Modifier.weight(1f)) {
                        if (newName.isBlank()) {
                            showToast(ctx, "Dê um nome para a reserva.")
                        } else {
                            store.update { it.addReserve(newName, parseNum(newMonthly), parseNum(newTarget)) }
                            newName = ""
                            newMonthly = ""
                            newTarget = ""
                            showNew = false
                        }
                    }
                    GhostButton("Cancelar", Modifier.weight(1f)) { showNew = false }
                }
            }
        }
    }

    editReserve?.let { id -> ReserveEditSheet(store, id) { editReserve = null } }
    editGoal?.let { id -> GoalEditSheet(store, id) { editGoal = null } }
    confirm?.let { (msg, run) ->
        GConfirm(msg, onYes = { run(); confirm = null }, onNo = { confirm = null })
    }
}

fun goalIcon(type: String): String = when (type) {
    "movel" -> "🛋"
    "imovel" -> "🏠"
    "automovel" -> "🚗"
    else -> "🎯"
}

fun goalLabel(type: String): String = when (type) {
    "movel" -> "Móvel"
    "imovel" -> "Imóvel"
    "automovel" -> "Automóvel"
    else -> "Outro"
}

@Composable
fun ReserveEditSheet(store: Store, id: Int, onClose: () -> Unit) {
    val r = store.current()?.objList("reserves")?.firstOrNull { it.optInt("id") == id }
    var name by remember { mutableStateOf(r?.optString("name") ?: "") }
    var monthly by remember { mutableStateOf(numText(r?.optDouble("monthlyContribution", 0.0) ?: 0.0)) }
    var target by remember { mutableStateOf(numText(r?.optDouble("targetValue", 0.0) ?: 0.0)) }
    GSheet("Editar reserva", onClose) {
        GField("Nome", name, { name = it })
        Spacer(Modifier.height(8.dp))
        GField("Aporte mensal recorrente (R$)", monthly, { monthly = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        GField("Meta — valor alvo (R$, opcional)", target, { target = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Salvar") {
            if (name.isNotBlank()) {
                store.update { it.saveReserve(id, name, parseNum(monthly), parseNum(target)) }
                onClose()
            }
        }
    }
}

@Composable
fun GoalEditSheet(store: Store, id: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val g = store.current()?.objList("goals")?.firstOrNull { it.optInt("id") == id }
    var name by remember { mutableStateOf(g?.optString("name") ?: "") }
    var type by remember { mutableStateOf(g?.str("type")?.ifEmpty { "outro" } ?: "outro") }
    var target by remember { mutableStateOf(numText(g?.optDouble("target", 0.0) ?: 0.0)) }
    var monthly by remember { mutableStateOf(numText(g?.optDouble("monthly", 0.0) ?: 0.0)) }
    var rate by remember { mutableStateOf(numText(g?.optDouble("rate", 0.0) ?: 0.0)) }
    GSheet("Editar meta", onClose) {
        GField("Nome", name, { name = it })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("movel", "imovel", "automovel", "outro").forEach { t -> GChip("${goalIcon(t)} ${goalLabel(t)}", type == t) { type = t } }
        }
        Spacer(Modifier.height(8.dp))
        GField("Valor alvo (R$)", target, { target = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        GField("Aporte mensal (R$)", monthly, { monthly = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        GField("Rendimento (% a.a.)", rate, { rate = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Salvar") {
            if (name.isBlank() || parseNum(target) <= 0) {
                showToast(ctx, "Informe o nome e um valor alvo maior que zero.")
            } else {
                store.update { it.saveGoal(id, name, type, parseNum(target), parseNum(monthly), parseNum(rate)) }
                onClose()
            }
        }
    }
}
