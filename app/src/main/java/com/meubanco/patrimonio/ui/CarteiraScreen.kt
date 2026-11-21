package com.meubanco.patrimonio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.MarketApi
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.launch

@Composable
fun CarteiraScreen() {
    val s = state()
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf("todos") }
    var trade by remember { mutableStateOf<Pair<Asset, Boolean>?>(null) }
    var transfer by remember { mutableStateOf<Boolean?>(null) }
    var quiz by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var editAsset by remember { mutableStateOf<Asset?>(null) }
    var msg by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("resumo") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            H2("Carteira de investimentos")
            KV("Valor das cotas", money(portfolioValue(s)))
            KV("Caixa para investir", money(s.leftover))
            KV("Dividendos recebidos", money(s.totalDividendsReceived), P.green)
            KV("Ganho realizado (vendas)", money(s.realizedGain), if (s.realizedGain >= 0) P.green else P.red)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Conta → Carteira", Modifier.weight(1f)) { transfer = true }
                GhostButton("Carteira → Conta", Modifier.weight(1f)) { transfer = false }
            }
        }

        ChipRow(listOf("resumo" to "Resumo", "ativos" to "Ativos", "metas" to "Metas", "dividendos" to "Dividendos", "impostos" to "Impostos", "ajustes" to "Perfil e auto"), sub) { sub = it }

        // alocação
        if (sub == "metas") MetasInvestBlock()
        if (sub == "resumo") {
        SimuladorJuros()
        val slices = remember(Store.rev) { allocationSlices(s) }
        if (slices.isNotEmpty()) Card {
            H2("Alocação")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Donut(slices)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val tot = slices.sumOf { it.value }
                    slices.forEach { sl ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(sl.color or 0xFF000000)))
                            Txt("${sl.label} ${fmtPlain(Math.round(sl.value / tot * 1000) / 10.0)}%", style = T.small)
                        }
                    }
                    portfolioRiskProfile(s)?.let { Muted("Carteira: risco ${it.label}") }
                }
            }
        }

        }

        if (sub == "ajustes") {
        // perfil
        Card {
            H2("Perfil de investidor")
            val p = s.investorProfile
            if (p == null || profileExpired(s)) Muted(if (p == null) "Você ainda não fez o teste." else "Seu teste venceu — refaça.")
            else { Txt(p.label, bold = true, color = P.gold); PROFILE_INFO[p.label]?.let { Muted(it.text) } }
            GhostButton(if (p == null) "Fazer o teste" else "Refazer o teste") { quiz = true }
        }

        }

        // impostos
        if (sub == "impostos") {
        val darfs = remember(Store.rev) { taxReport(s) }
        if (darfs.isNotEmpty()) Card {
            H2("Impostos sobre vendas (DARF)")
            Muted("Ações: 15%, isento se vendas do mês ≤ R$ 20 mil · FIIs: 20% sempre.")
            darfs.takeLast(6).reversed().forEach { m ->
                Column {
                    KV(formatMonthLabel(m.ym), if (m.tax > 0.005) formatBRL(m.tax) else "sem imposto", if (m.tax > 0.005 && !m.paid) P.red else P.muted)
                    if (m.tax > 0.005) {
                        Muted("vence ${formatSimDate(m.dueISO)}" + if (m.paid) " · pago ✓" else "")
                        if (!m.paid) PrimaryButton("Pagar DARF de ${formatBRL(m.tax)}") { handle(Store.mutate { payDarf(it, m.ym) }) }
                    } else if (m.exempt && m.salesAcao > 0) Muted("Ações isentas neste mês")
                }
            }
        }

        }

        if (sub == "ativos") {
        // ativos
        H2("Ativos")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(if (refreshing) "Atualizando…" else "Atualizar cotações", enabled = !refreshing) {
                refreshing = true
                scope.launch {
                    val (_, m) = MarketApi.refreshAssets(Store.state)
                    Store.commit(); msg = m; refreshing = false
                }
            }
            s.market.assetsUpdatedAt?.let { Muted("última: ${formatSimDate(it)}") }
        }
        if (msg.isNotBlank()) Muted(msg)
        ChipRow(listOf("todos" to "Todos", "minhas" to "Minhas") + CATEGORY_LABELS.map { it.key to it.value }, cat) { cat = it }
        s.assets.filter { when (cat) { "todos" -> true; "minhas" -> it.quantity > 0; else -> it.category == cat } }.forEach { a ->
            Card(onClick = { editAsset = a }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Txt(a.ticker, bold = true); Muted(a.name) }
                    Column(horizontalAlignment = Alignment.End) {
                        Txt(formatBRL(a.price), bold = true)
                        val prev = a.prevPrice
                        if (prev != null && prev > 0 && prev != a.price) {
                            val ch = (a.price / prev - 1) * 100
                            Txt((if (ch >= 0) "▲ " else "▼ ") + fmtPlain(Math.round(Math.abs(ch) * 100) / 100.0) + "%", color = if (ch >= 0) P.green else P.red, style = T.small)
                        }
                    }
                }
                Muted("Rende ~${fmtPlain(a.yieldPct)}% a.m. · risco ${riskOf(a).label}" +
                    if (a.quantity > 0) " · você tem ${a.quantity} (PM ${formatBRL(avgPrice(a))})" else "")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Comprar", Modifier.weight(1f)) { trade = a to false }
                    if (a.quantity > 0) GhostButton("Vender", Modifier.weight(1f)) { trade = a to true }
                }
            }
        }

        }

        if (sub == "ajustes") {
        // automático
        Card {
            H2("Investimento automático")
            var monthly by remember { mutableStateOf<Double?>(s.monthlyContribution) }
            NumField(monthly, { monthly = it; Store.state.monthlyContribution = it ?: 0.0; Store.commit() }, "Aporte mensal (da conta para a carteira)", suffix = "R$")
            SwitchRow("Aporte automático todo mês", s.recurringInvestEnabled) { Store.mutate { st -> st.recurringInvestEnabled = it } }
            SwitchRow("Reinvestir dividendos", s.reinvestDividends) { Store.mutate { st -> st.reinvestDividends = it } }
            Muted("Corretagem por ordem: " + formatBRL(s.settings.brokerageFee) + " (ajuste em Configurações)")
        }
        }

        if (sub == "dividendos") DividendosBlock()
    }

    transfer?.let { to -> TransferDialog(to) { transfer = null } }
    trade?.let { (a, sell) -> TradeDialog(a, sell) { trade = null } }
    if (quiz) QuizDialog { quiz = false }
    editAsset?.let { a -> AssetEditDialog(a) { editAsset = null } }
}

@Composable
fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Txt(label, Modifier.weight(1f))
        androidx.compose.material3.Switch(value, onChange)
    }
}

@Composable
private fun TransferDialog(toInvest: Boolean, onDismiss: () -> Unit) {
    val s = state()
    var v by remember { mutableStateOf<Double?>(null) }
    FormDialog(if (toInvest) "Conta → Carteira" else "Carteira → Conta", onDismiss, "Transferir", {
        handle(Store.mutate { transferInvestAccounts(it, toInvest, v ?: 0.0) })
    }) {
        NumField(v, { v = it }, "Valor", suffix = "R$")
        Muted("Conta: ${formatBRL(s.cash)} · Carteira (caixa): ${formatBRL(s.leftover)}")
    }
}

@Composable
private fun TradeDialog(a: Asset, sell: Boolean, onDismiss: () -> Unit) {
    val s = state()
    var qty by remember { mutableStateOf(1.0) }
    val q = qty.toInt()
    val max = investMaxQty(s, a, sell)
    var review by remember { mutableStateOf(false) }
    val warn = if (!sell) suitabilityWarning(s, a) else ""
    val est = if (sell && q in 1..a.quantity) estimateSaleTax(s, a.id, q) else null
    FormDialog((if (sell) "Vender " else "Comprar ") + a.ticker, onDismiss, "Confirmar", {
        if (q < 1) { Ui.show("Informe a quantidade."); false }
        else if (!review) { review = true; false }
        else if (sell) { val (r, _) = Store.mutate { sellShares(it, a.id, q) }; handle(r) }
        else handle(Store.mutate { manualInvest(it, a.id, q) })
    }) {
        Txt("${a.name} · ${formatBRL(a.price)} por cota")
        NumField(qty, { qty = it ?: 0.0; review = false }, "Quantidade (máx. $max)", decimals = false)
        val gross = round2(q * a.price)
        KV("Valor das cotas", formatBRL(gross))
        if (brokerageFee(s) > 0) KV("Corretagem", formatBRL(brokerageFee(s)))
        if (!sell) KV("Total", formatBRL(gross + brokerageFee(s)))
        if (!sell) Muted("Caixa da carteira: ${formatBRL(s.leftover)} — transfira da conta antes, se precisar.")
        if (warn.isNotBlank()) Txt(warn, color = P.gold, style = T.small)
        if (est != null) {
            Muted("Lucro/prejuízo estimado: ${formatBRL(est.gain)}")
            if (est.delta > 0.005) Txt("Imposto estimado da venda: ${formatBRL(est.delta)} (DARF até ${formatSimDate(est.dueISO)})", color = P.red, style = T.small)
            else Muted("Sem imposto adicional estimado nesta venda.")
        }
        if (review) Txt("Revise e toque em Confirmar novamente.", color = P.brand, bold = true)
    }
}

@Composable
private fun QuizDialog(onDismiss: () -> Unit) {
    val answers = remember { mutableStateListOf<Int>().also { l -> repeat(QUIZ.size) { l.add(-1) } } }
    FormDialog("Perfil de investidor", onDismiss, "Salvar", {
        if (answers.any { it < 0 }) { Ui.show("Responda todas as perguntas."); false }
        else { val r = Store.mutate { saveInvestorProfile(it, answers.toList()) }; Ui.show("Seu perfil: ${r.label}"); true }
    }) {
        QUIZ.forEachIndexed { i, q ->
            Txt("${i + 1}. ${q.q}", bold = true)
            q.o.forEachIndexed { j, o ->
                Row(Modifier.fillMaxWidth().clickable { answers[i] = j }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(answers[i] == j, { answers[i] = j })
                    Txt(o, style = T.small)
                }
            }
        }
    }
}

@Composable
private fun AssetEditDialog(a: Asset, onDismiss: () -> Unit) {
    var price by remember { mutableStateOf<Double?>(a.price) }
    var yieldPct by remember { mutableStateOf<Double?>(a.yieldPct) }
    FormDialog("${a.ticker} — ajustar", onDismiss, "Salvar", {
        Store.mutate { if ((price ?: 0.0) > 0) { a.prevPrice = a.price; a.price = price!! }; if ((yieldPct ?: 0.0) >= 0) a.yieldPct = yieldPct ?: a.yieldPct }
        true
    }) {
        Muted("Ajuste manual de preço e rendimento mensal (%). As cotações automáticas voltam a atualizar depois.")
        NumField(price, { price = it }, "Preço", suffix = "R$")
        NumField(yieldPct, { yieldPct = it }, "Rendimento mensal", suffix = "%")
    }
}

@Composable
private fun DividendosBlock() {
    val s = state()
    val byMonth = dividendsByMonth(s).takeLast(12)
    Card {
        H2("💰 Dividendos mês a mês")
        KV("Renda mensal estimada agora", money(monthlyDividendIncome(s)), P.green)
        if (byMonth.isEmpty()) Muted("Ainda não houve pagamentos de dividendos.")
        else {
            BarChart(byMonth.map { (ym, v) -> ym.substring(5) + "/" + ym.substring(2, 4) to v })
            Muted("Total recebido até hoje: ${formatBRL(s.totalDividendsReceived)}")
        }
    }
    val byT = dividendsByTicker(s)
    if (byT.isNotEmpty()) Card { H2("Quem mais pagou (últimos 12 meses)"); BarList(byT.take(10), P.green) }
}


@Composable
private fun SimuladorJuros() {
    val s = state()
    var start by remember { mutableStateOf<Double?>(null) }; var monthly by remember { mutableStateOf<Double?>(500.0) }
    var rate by remember { mutableStateOf<Double?>(10.0) }; var years by remember { mutableStateOf<Double?>(10.0) }
    var open by remember { mutableStateOf(false) }
    Card(onClick = { open = !open }) {
        H2("🧮 Simulador de aportes (juros compostos)")
        if (!open) Muted("Veja quanto seus aportes mensais podem virar. Toque para abrir.")
    }
    if (open) Card {
        NumField(start, { start = it }, "Valor inicial", suffix = "R$")
        NumField(monthly, { monthly = it }, "Aporte por mês", suffix = "R$")
        NumField(rate, { rate = it }, "Rentabilidade ao ano (%)", suffix = "%")
        NumField(years, { years = it }, "Anos", decimals = false)
        val y = clampI((years ?: 1.0).toInt(), 1, 60)
        val fv = futureValue(start ?: 0.0, monthly ?: 0.0, rate ?: 0.0, y)
        val put = (start ?: 0.0) + (monthly ?: 0.0) * 12 * y
        KV("Você investe", money(put)); KV("Total em $y ano(s)", money(fv), P.green); KV("Rendimento", money(fv - put), P.gold)
        Muted("Estimativa sem impostos, inflação ou garantias. Rentabilidade passada não garante retorno futuro.")
    }
}
