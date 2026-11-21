package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

@Composable
fun ContaScreen(goTab: (String) -> Unit) {
    val s = state()
    var pix by remember { mutableStateOf<String?>(null) } // "send" | "receive"
    var buyCard by remember { mutableStateOf(false) }
    var newReserve by remember { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!s.settings.simNoticeDismissed) Card {
            H2("ℹ️ Importante")
            Txt("Este app não é banco nem corretora e não move dinheiro real. Pix, caixinhas e investimentos são registros seus e simulações para você se organizar.", color = P.muted)
            GhostButton("Entendi") { Store.mutate { it.settings.simNoticeDismissed = true } }
        }
        // saldo
        Card {
            Muted("Conta corrente · ag. ${s.account.agency} · ${s.account.number}")
            Txt(money(s.cash), style = T.big, bold = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Enviar Pix", Modifier.weight(1f)) { pix = "send" }
                GhostButton("Receber Pix", Modifier.weight(1f)) { pix = "receive" }
            }
            KV("Patrimônio total", money(totalNetWorth(s)), P.gold)
            KV("Na carteira de investimentos", money(portfolioValue(s) + s.leftover))
            KV("Em caixinhas e metas", money(reservesTotalBalance(s) + goalsTotalSaved(s)))
            if (s.fgts.balance > 0) KV("FGTS", money(s.fgts.balance))
        }

        if (s.realAccounts.isNotEmpty()) Card(onClick = { goTab("reais") }) {
            val b = realBalances(s); val m = realMonthSummary(s, AppClock.todayISO().take(7))
            Muted("🔗 Bancos reais (somente leitura)")
            Txt(money(b.cash), style = T.big, bold = true)
            if (s.realInvest.isNotEmpty()) Muted("Investimentos reais: ${money(realInvestTotal(s))}")
            Muted("Neste mês: entrou ${money(m.income)} · saiu ${money(m.spent)}")
        }
        // primeiros passos
        val steps = remember(Store.rev) { firstSteps(s) }
        if (steps.any { !it.done }) Card {
            H2("🚀 Primeiros passos (${steps.count { it.done }}/${steps.size})")
            Progress(steps.count { it.done }.toDouble() / steps.size)
            steps.filter { !it.done }.take(3).forEach { st -> GhostButton("▫️ ${st.label}", Modifier.fillMaxWidth()) { goTab(st.tab) } }
        }
        // saúde financeira
        val health = remember(Store.rev) { financialHealth(s) }
        var showHealth by remember { mutableStateOf(false) }
        Card(onClick = { showHealth = !showHealth }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                H2("❤️ Saúde financeira"); Txt("${health.score}/100 · ${health.label}", bold = true, color = if (health.score >= 70) P.green else if (health.score >= 40) P.gold else P.red)
            }
            Progress(health.score / 100.0, if (health.score >= 70) P.green else if (health.score >= 40) P.gold else P.red)
            if (showHealth) health.parts.forEach { pt ->
                Spacer8(); Txt("${pt.title} · ${pt.points}/${pt.max}", bold = true); Muted(pt.tip)
            } else Muted("Toque para ver o que ajuda a subir a nota.")
        }
        // dicas
        val insights = remember(Store.rev) { computeInsights(s) }
        if (insights.isNotEmpty()) {
            Card {
                H2("💡 Dicas para você")
                insights.take(4).forEach { i ->
                    Column(Modifier.then(if (i.goTab != null) Modifier.clickable { goTab(i.goTab) } else Modifier)) {
                        Txt("${i.ico} ${i.t}", bold = true)
                        Muted(i.d + (if (i.goLabel != null) " · ${i.goLabel} →" else ""))
                    }
                }
            }
        }

        // cartão
        if (s.card.enabled) CardSection { buyCard = true }

        // caixinhas
        H2("Caixinhas (reservas)")
        Muted("Rendem ${fmtPlain(s.reserveConfig.annualRatePct)}% a.a. (CDI) · IR regressivo só no resgate")
        s.reserves.forEach { r -> ReserveCard(r) }
        GhostButton("＋ Nova caixinha", Modifier.fillMaxWidth()) { newReserve = true }

        H2("Metas")
        s.goals.forEach { g -> GoalCard(g) }
        GhostButton("＋ Nova meta", Modifier.fillMaxWidth()) { newGoal = true }
    }

    if (pix != null) PixDialog(pix == "send") { pix = null }
    if (buyCard) BuyCardDialog { buyCard = false }
    if (newReserve) {
        var name by remember { mutableStateOf("") }; var monthly by remember { mutableStateOf<Double?>(null) }; var target by remember { mutableStateOf<Double?>(null) }
        FormDialog("Nova caixinha", { newReserve = false }, "Criar", {
            if (name.isBlank()) { Ui.show("Dê um nome à caixinha."); false } else {
                Store.mutate { addReserve(it, name.trim(), monthly ?: 0.0, target ?: 0.0) }; true
            }
        }) {
            Field(name, { name = it }, "Nome")
            NumField(monthly, { monthly = it }, "Aporte mensal", suffix = "R$")
            NumField(target, { target = it }, "Valor alvo (opcional)", suffix = "R$")
        }
    }
    if (newGoal) NewGoalDialog { newGoal = false }
}

@Composable
private fun CardSection(onBuy: () -> Unit) {
    val s = state()
    val cards = allCards(s)
    val c = activeCard(s)
    var confirm by remember { mutableStateOf<Res?>(null) }
    Card {
        H2("💳 ${c.name} · final ${c.last4}")
        if (cards.size > 1) ChipRow(cards.mapIndexed { i, cc -> i.toString() to (cc.name) }, s.activeCardIdx.toString()) { Store.mutate { st -> st.activeCardIdx = it.toInt() } }
        KV("Fatura aberta", money(c.invoiceCurrent))
        if (c.pendingAmount > 0) KV("Fatura fechada (a pagar)", money(c.pendingAmount), P.red)
        KV("Limite disponível", money(cardAvailable(c)), P.green)
        Muted("Fecha dia ${c.closingDay} · vence dia ${c.dueDay} · limite ${formatBRL(c.limit)}")
        Progress(if (c.limit > 0) (c.limit - cardAvailable(c)) / c.limit else 0.0, P.gold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Nova compra", Modifier.weight(1f)) { onBuy() }
            if (c.pendingAmount > 0) GhostButton("Pagar fatura", Modifier.weight(1f)) {
                val r = Store.mutate { payInvoiceNow(it, c, false) }
                if (r.confirm != null) confirm = r else handle(r)
            }
        }
        c.purchases.filter { it.installmentsPaid < it.installmentsTotal || it.installmentsTotal <= 1 }.takeLast(5).forEach { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt("${p.desc} (${p.installmentsPaid}/${p.installmentsTotal})", style = T.small)
                Txt(formatBRL(p.installmentValue), style = T.small)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cards.size < 4) GhostButton("＋ Outro cartão") { Store.mutate { addExtraCard(it) } }
            if (s.activeCardIdx > 0) GhostButton("Remover este", danger = true) { Store.mutate { removeActiveCard(it) } }
        }
    }
    confirm?.let { r ->
        Confirm(r.confirm ?: "", { confirm = null; handle(Store.mutate { payInvoiceNow(it, c, true) }) }, { confirm = null }, "Pagar")
    }
}

@Composable
private fun BuyCardDialog(onDismiss: () -> Unit) {
    val s = state()
    var desc by remember { mutableStateOf("") }; var amount by remember { mutableStateOf<Double?>(null) }; var inst by remember { mutableStateOf(1.0) }
    FormDialog("Compra no cartão", onDismiss, "Lançar", {
        val r = Store.mutate { addCardPurchase(it, activeCard(it), desc, amount ?: 0.0, inst.toInt()) }
        handle(r)
    }) {
        Field(desc, { desc = it }, "Descrição")
        NumField(amount, { amount = it }, "Valor total", suffix = "R$")
        NumField(inst, { inst = it ?: 1.0 }, "Parcelas (1 a 24)", decimals = false)
        Muted("Disponível: ${formatBRL(cardAvailable(activeCard(s)))}")
    }
}

@Composable
private fun PixDialog(send: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf<Double?>(null) }
    var ask by remember { mutableStateOf<String?>(null) }
    FormDialog(if (send) "Enviar Pix" else "Receber Pix", onDismiss, if (send) "Enviar" else "Registrar", {
        val v = amount ?: 0.0
        if (send) {
            val chk = checkSendPix(Store.state, name, v)
            if (chk.confirm != null) { ask = chk.confirm; false }
            else if (!chk.ok) { if (chk.msg.isNotBlank()) Ui.show(chk.msg); false }
            else handle(Store.mutate { executeSendPix(it, name.trim(), v) })
        } else handle(Store.mutate { receivePix(it, name.trim().ifBlank { "Pix recebido" }, v) })
    }) {
        if (send) {
            val rec = remember { pixRecents(Store.state) }
            if (rec.isNotEmpty()) { Muted("Recentes"); ChipRow(rec.map { it to it }, name) { name = it } }
        }
        Field(name, { name = it }, if (send) "Para quem" else "De quem")
        if (send && name.isNotBlank() && pixIsNewRecipient(Store.state, name)) {
            Txt("⚠️ Primeira vez enviando para ${name.trim()}. Confira o nome do destinatário.", color = P.gold)
            Muted(SCAM_TIPS[(name.length) % SCAM_TIPS.size])
        }
        NumField(amount, { amount = it }, "Valor", suffix = "R$")
        if (send) pixRiskFlags(Store.state, name, amount ?: 0.0).forEach { Txt("⚠️ $it", color = P.gold) }
        Muted("Saldo: ${formatBRL(Store.state.cash)}")
    }
    ask?.let {
        Confirm(it, { ask = null; handle(Store.mutate { s -> executeSendPix(s, name.trim(), amount ?: 0.0) }); onDismiss() }, { ask = null })
    }
}

@Composable
private fun ReserveCard(r: Reserve) {
    val s = state()
    var extra by remember { mutableStateOf(false) }; var withdraw by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    Card {
        H2("📦 ${r.name}")
        Txt(money(r.balance), style = T.title, bold = true)
        if (r.targetValue > 0) { Progress(r.balance / r.targetValue, P.green); Muted("Alvo ${formatBRL(r.targetValue)}") }
        Muted("Aporte mensal ${formatBRL(r.monthlyContribution)} · rendimento acumulado ${formatBRL(r.totalInterest)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Guardar", Modifier.weight(1f)) { extra = true }
            GhostButton("Resgatar", Modifier.weight(1f)) { withdraw = true }
            GhostButton("🗑", danger = true) { del = true }
        }
    }
    if (extra) {
        var v by remember { mutableStateOf<Double?>(null) }
        FormDialog("Guardar em ${r.name}", { extra = false }, "Guardar", {
            val a = v ?: 0.0
            if (a <= 0) false else if (a > s.cash + 0.001) { Ui.show("Saldo insuficiente."); false } else { Store.mutate { addExtraToReserve(it, r.id, a) }; true }
        }) { NumField(v, { v = it }, "Valor", suffix = "R$"); Muted("Saldo: ${formatBRL(s.cash)}") }
    }
    if (withdraw) {
        var v by remember { mutableStateOf<Double?>(null) }
        val est = v?.takeIf { it > 0 && it <= r.balance }?.let { reserveWithdrawEstimate(s, r, it) }
        FormDialog("Resgatar de ${r.name}", { withdraw = false }, "Resgatar", {
            val a = v ?: 0.0
            if (a <= 0 || a > r.balance + 0.001) { Ui.show("Valor inválido."); false } else { Store.mutate { withdrawFromReserve(it, r.id, a) }; true }
        }) {
            NumField(v, { v = it }, "Valor bruto", suffix = "R$")
            Muted("Disponível: ${formatBRL(r.balance)}")
            if (est != null) Muted("IR estimado ${formatBRL(est.ir)} (${fmtPlain(est.aliquota * 100)}% sobre ${formatBRL(est.yieldPortion)} de rendimento) → você recebe ${formatBRL(est.net)}")
        }
    }
    if (del) Confirm("Excluir a caixinha ${r.name}? O saldo de ${formatBRL(r.balance)} será devolvido à conta.", {
        Store.mutate { st -> if (r.balance > 0) { st.cash = round2(st.cash + r.balance); r.balance = 0.0 }; removeReserve(st, r.id) }; del = false
    }, { del = false }, "Excluir")
}

@Composable
private fun GoalCard(g: Goal) {
    val s = state()
    var extra by remember { mutableStateOf(false) }; var del by remember { mutableStateOf(false) }
    Card {
        H2("${GOAL_TYPE_ICON[g.type] ?: "🎯"} ${g.name}" + if (g.completed) " ✅" else "")
        Txt("${money(g.saved)} de ${money(g.target)}", bold = true)
        Progress(if (g.target > 0) g.saved / g.target else 0.0, P.gold)
        val need = goalMonthlyNeeded(g, AppClock.todayISO())
        if (need != null && need > 0) {
            Muted("Para chegar em ${formatMonthLabel(g.deadlineYM!!)} guarde ${formatBRL(need)}/mês")
            if (g.monthly + 0.005 < need) GhostButton("Usar esse aporte (${formatBRL(need)})") { Store.mutate { _ -> g.monthly = need } }
        }
        if (!g.completed && g.monthly > 0) {
            val m = estimateMonthsToGoal(g)
            if (m.isFinite()) Muted("Aporte ${formatBRL(g.monthly)}/mês · faltam ~${Math.ceil(m).toInt()} mês(es)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!g.completed) GhostButton("Guardar", Modifier.weight(1f)) { extra = true }
            GhostButton("🗑", danger = true) { del = true }
        }
    }
    if (extra) {
        var v by remember { mutableStateOf<Double?>(null) }
        FormDialog("Guardar para ${g.name}", { extra = false }, "Guardar", {
            val a = v ?: 0.0
            if (a <= 0) false else if (a > s.cash + 0.001) { Ui.show("Saldo insuficiente."); false } else { Store.mutate { addExtraToGoal(it, g.id, a) }; true }
        }) { NumField(v, { v = it }, "Valor", suffix = "R$") }
    }
    if (del) Confirm("Excluir a meta ${g.name}? O valor guardado volta para a conta.", {
        Store.mutate { st -> if (g.saved > 0 && !g.completed) { st.cash = round2(st.cash + g.saved); g.saved = 0.0 }; removeGoal(st, g.id) }; del = false
    }, { del = false }, "Excluir")
}

@Composable
private fun NewGoalDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var type by remember { mutableStateOf("outro") }
    var target by remember { mutableStateOf<Double?>(null) }; var monthly by remember { mutableStateOf<Double?>(null) }
    var rate by remember { mutableStateOf<Double?>(null) }; var prazo by remember { mutableStateOf<Double?>(null) }
    FormDialog("Nova meta", onDismiss, "Criar", {
        if (name.isBlank() || (target ?: 0.0) <= 0) { Ui.show("Informe nome e valor da meta."); false } else {
            Store.mutate { st ->
                st.goals.add(Goal(st.nextGoalId++, name.trim(), type, target ?: 0.0, 0.0, monthly ?: 0.0, rate ?: 0.0, false, null,
                    prazo?.takeIf { it >= 1 }?.let { addMonthsYM(AppClock.todayISO(), clampI(it.toInt(), 1, 600)) }))
            }; true
        }
    }) {
        Field(name, { name = it }, "Nome da meta")
        ChipRow(GOAL_TYPE_LABELS.map { (k, v) -> k to "${GOAL_TYPE_ICON[k]} $v" }, type) { type = it }
        NumField(target, { target = it }, "Valor da meta", suffix = "R$")
        NumField(monthly, { monthly = it }, "Aporte mensal", suffix = "R$")
        NumField(rate, { rate = it }, "Rendimento ao ano (opcional)", suffix = "%")
        NumField(prazo, { prazo = it }, "Prazo em meses (opcional)", decimals = false)
    }
}
