package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

/* ---------- Calculadoras (Planejar) ---------- */
@Composable
fun CalcBlock() {
    var sub by remember { mutableStateOf("negociar") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChipRow(listOf("negociar" to "Negociar dívida", "emprestimo" to "Comparar empréstimos", "investir" to "Poupança × CDB × Tesouro"), sub) { sub = it }
        when (sub) { "negociar" -> NegociarCalc(); "emprestimo" -> EmprestimoCalc(); else -> InvestirCalc() }
    }
}

@Composable
private fun NegociarCalc() {
    var bal by remember { mutableStateOf<Double?>(null) }; var rate by remember { mutableStateOf<Double?>(null) }
    var n by remember { mutableStateOf<Double?>(24.0) }; var offer by remember { mutableStateOf<Double?>(null) }
    Card {
        H2("🤝 Vale a pena quitar à vista?")
        Muted("Compare continuar pagando as parcelas com a oferta de quitação que o banco ou a loja fez.")
        NumField(bal, { bal = it }, "Saldo devedor hoje", suffix = "R$")
        NumField(rate, { rate = it }, "Juros ao mês (%)", suffix = "%")
        NumField(n, { n = it }, "Parcelas restantes", decimals = false)
        NumField(offer, { offer = it }, "Valor da oferta à vista", suffix = "R$")
        val b = bal ?: 0.0; val o = offer ?: 0.0
        if (b > 0 && o > 0) {
            val r = negotiation(b, rate ?: 0.0, clampI((n ?: 1.0).toInt(), 1, 600), o)
            KV("Pagando todas as parcelas", money(r.keepTotal), P.red)
            KV("Quitando à vista", money(r.cashOffer), P.green)
            if (r.savings > 0) Txt("Você economiza ${money(r.savings)} (${"%.0f".format(r.discountPct)}% de desconto sobre o saldo).", color = P.green, bold = true)
            else Txt("A oferta sai mais cara que continuar pagando. Tente negociar mais.", color = P.red, bold = true)
            Muted("Antes de pagar, confirme o valor e o boleto no canal oficial do credor e peça a quitação por escrito. Quitar à vista só faz sentido se isso não zerar sua reserva.")
        }
    }
}

@Composable
private fun EmprestimoCalc() {
    var amt by remember { mutableStateOf<Double?>(null) }
    val offers = remember { mutableStateListOf(Pair<Double?, Double?>(null, 12.0), Pair<Double?, Double?>(null, 12.0)) } // juros % a.m., prazo
    Card {
        H2("🏦 Comparar propostas de empréstimo")
        Muted("Informe o valor e, para cada proposta, a taxa mensal e o prazo. O app mostra parcela e quanto você paga de juros.")
        NumField(amt, { amt = it }, "Valor que precisa", suffix = "R$")
        offers.forEachIndexed { i, o ->
            Spacer8(); Txt("Proposta ${i + 1}", bold = true)
            NumField(o.first, { offers[i] = Pair(it, o.second) }, "Juros ao mês (%)", suffix = "%")
            NumField(o.second, { offers[i] = Pair(o.first, it) }, "Prazo (meses)", decimals = false)
            val a = amt ?: 0.0
            if (a > 0 && o.first != null && (o.second ?: 0.0) >= 1) {
                val r = loanOffer(a, o.first!!, o.second!!.toInt())
                KV("Parcela", money(r.payment)); KV("Total pago", money(r.total)); KV("Juros pagos", money(r.interest), P.red)
                Muted("Equivale a ${"%.1f".format(r.annualPct)}% ao ano")
            }
        }
        GhostButton("＋ Outra proposta") { if (offers.size < 4) offers.add(Pair(null, 12.0)) }
        Muted("Compare o custo total (CET) informado no contrato: tarifas e seguros podem aumentar o valor.")
    }
}

@Composable
private fun InvestirCalc() {
    val s = state()
    var amt by remember { mutableStateOf<Double?>(10000.0) }; var months by remember { mutableStateOf<Double?>(12.0) }
    var selic by remember { mutableStateOf<Double?>(s.market.selicPct ?: s.reserveConfig.annualRatePct) }; var pct by remember { mutableStateOf<Double?>(100.0) }
    Card {
        H2("📊 Onde rende mais?")
        NumField(amt, { amt = it }, "Quanto investir", suffix = "R$")
        NumField(months, { months = it }, "Por quantos meses", decimals = false)
        NumField(selic, { selic = it }, "Selic ao ano (%)", suffix = "%")
        NumField(pct, { pct = it }, "CDB: % do CDI", suffix = "%")
        val a = amt ?: 0.0
        if (a > 0) {
            val rows = compareInvest(a, clampI((months ?: 1.0).toInt(), 1, 600), selic ?: 0.0, pct ?: 100.0)
            rows.forEachIndexed { i, r ->
                Spacer8(); Txt("${if (i == 0) "🥇 " else ""}${r.name}", bold = true)
                KV("Você recebe (líquido)", money(r.net), if (i == 0) P.green else P.text)
                Muted("Rende ${"%.2f".format(r.annualPct)}% ao ano · IR ${money(r.tax)}")
            }
        }
        Muted("Estimativa com taxa constante, sem TR, taxa de custódia ou inflação. A poupança é isenta de IR; CDB e Tesouro seguem a tabela regressiva. Rentabilidade passada não garante o futuro.")
    }
}

/* ---------- Renda variável (Planejar) ---------- */
@Composable
fun RendaVariavelBlock() {
    val s = state()
    val v = remember(Store.rev) { variableIncome(s) }
    Card {
        H2("🌦 Renda que muda de mês a mês")
        Muted("Para quem é autônomo, faz bicos ou trabalha por comissão: planeje com base nos meses ruins, não nos bons.")
        if (v == null) Muted("Preciso de pelo menos 2 meses fechados com entradas registradas para calcular.")
        else {
            KV("Média dos últimos ${v.months} meses", money(v.avg))
            KV("Pior mês", money(v.min), P.red); KV("Melhor mês", money(v.max), P.green)
            Spacer8()
            Txt("Gaste no máximo ${money(v.baseline)} por mês", bold = true, color = P.gold)
            Muted("É a média dos seus 3 meses mais fracos. O que entrar acima disso nos meses bons vai para a reserva, e não para o consumo.")
            val essencial = essentialMonthly(s)
            if (essencial > v.baseline) Txt("⚠️ Seus gastos fixos (${money(essencial)}) passam desse valor. Vale cortar custos fixos ou buscar uma segunda fonte.", color = P.red)
            if (v.swingPct > 0.3) {
                Txt("Sua renda varia ${"%.0f".format(v.swingPct * 100)}% entre o melhor e o pior mês. Uma reserva de 9 a 12 meses é mais segura.", color = P.gold)
                GhostButton("Usar meta de 12 meses") { Store.mutate { st -> st.emergencyMonthsTarget = 12.0 } }
            }
        }
    }
}

/* ---------- Dividir contas (Gastos) ---------- */
@Composable
fun DividirBlock() {
    val s = state(); val ctx = LocalContext.current
    var add by remember { mutableStateOf(false) }
    val bal = sharedBalance(s)
    Card {
        H2("👫 Dividir contas")
        Muted("Controle de contas divididas com uma pessoa. Fica só neste aparelho: para o acerto, compartilhe o resumo.")
        Field(s.partnerName, { n -> Store.mutate { it.partnerName = n } }, "Nome da outra pessoa")
        if (bal > 0) Txt("${s.partnerName} deve ${money(bal)} a você", bold = true, color = P.green)
        else if (bal < 0) Txt("Você deve ${money(-bal)} a ${s.partnerName}", bold = true, color = P.red)
        else Txt("Vocês estão quites", bold = true)
        PrimaryButton("＋ Nova conta dividida") { add = true }
        if (s.sharedBills.any { !it.settled }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Compartilhar", Modifier.weight(1f)) { shareText(ctx, sharedShareText(s), "Contas divididas") }
                GhostButton("Marcar tudo como acertado", Modifier.weight(1f)) { Store.mutate { st -> st.sharedBills.forEach { it.settled = true } } }
            }
        }
    }
    s.sharedBills.filter { !it.settled }.forEach { b ->
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Txt(b.name, bold = true); Muted("pago por ${if (b.paidByMe) "você" else s.partnerName} · sua parte ${"%.0f".format(b.mySharePct)}%") }
                Txt(money(b.amount), bold = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Acertada", Modifier.weight(1f)) { Store.mutate { _ -> b.settled = true } }
                GhostButton("Excluir", Modifier.weight(1f), danger = true) { Store.mutate { st -> st.sharedBills.removeAll { it.id == b.id } } }
            }
        }
    }
    if (add) {
        var name by remember { mutableStateOf("") }; var amt by remember { mutableStateOf<Double?>(null) }
        var mine by remember { mutableStateOf(true) }; var pct by remember { mutableStateOf("50") }
        FormDialog("Nova conta dividida", { add = false }, "Salvar", {
            if (name.isBlank() || (amt ?: 0.0) <= 0) { Ui.show("Informe nome e valor."); false } else {
                Store.mutate { st -> st.sharedBills.add(SharedBill(st.nextSharedId++, name.trim(), amt ?: 0.0, mine, pct.toDouble(), AppClock.todayISO())) }; true
            }
        }) {
            Field(name, { name = it }, "Conta (ex.: Mercado, Aluguel)")
            NumField(amt, { amt = it }, "Valor total", suffix = "R$")
            SwitchRow("Eu paguei", mine) { mine = it }
            Muted("Minha parte")
            ChipRow(listOf("30", "40", "50", "60", "70").map { it to "$it%" }, pct) { pct = it }
        }
    }
}

/* ---------- Resumo para o IR (Mais) ---------- */
@Composable
fun IrScreen() {
    val s = state(); val ctx = LocalContext.current
    val cur = AppClock.today().year
    var year by remember { mutableStateOf(cur - 1) }
    val r = remember(Store.rev, year) { irSummary(s, year) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            H2("🧾 Resumo para o Imposto de Renda")
            Muted("Reúne o que o app registrou para ajudar a organizar a declaração. Não é a declaração e não substitui os informes oficiais.")
            ChipRow(listOf(cur, cur - 1, cur - 2).map { it.toString() to it.toString() }, year.toString()) { year = it.toInt() }
        }
        Card {
            KV("Entradas registradas", money(r.income), P.green); KV("Gastos registrados", money(r.spent))
            KV("Saúde", money(r.health)); KV("Educação", money(r.education))
            Muted("Despesas médicas e de educação podem ser dedutíveis dentro de regras e limites. Guarde os comprovantes.")
        }
        Card {
            H2("Dividendos recebidos: ${money(r.dividends)}")
            if (r.dividendsByTicker.isEmpty()) Muted("Nenhum dividendo registrado em $year.")
            else BarList(r.dividendsByTicker.take(10), P.gold)
        }
        Card {
            H2("Bens e direitos (saldos de hoje)")
            r.assets.forEach { KV(it.first, money(it.second)) }
            if (r.debts > 0) KV("Dívidas", money(r.debts), P.red)
            Muted("Na declaração, informe os saldos de 31/12 do ano-base, conforme seus extratos.")
        }
        PrimaryButton("Compartilhar resumo", Modifier.fillMaxWidth()) { shareText(ctx, irShareText(r), "Resumo para o IR $year") }
        Muted("As regras do IR mudam (por exemplo, a tributação de dividendos). Confirme sempre no site da Receita Federal ou com um contador.")
    }
}
