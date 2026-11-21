package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

@Composable
fun PlanoScreen() {
    var sub by remember { mutableStateOf("projecao") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChipRow(listOf("projecao" to "Projeção", "calendario" to "Calendário", "dividas" to "Dívidas", "calc" to "Calculadoras", "renda" to "Renda variável", "reserva" to "Reserva", "regra" to "50-30-20", "fi" to "Independência"), sub) { sub = it }
        when (sub) {
            "projecao" -> ProjecaoBlock()
            "calendario" -> CalendarioBlock()
            "dividas" -> DividasBlock()
            "calc" -> CalcBlock()
            "renda" -> RendaVariavelBlock()
            "reserva" -> ReservaBlock()
            "regra" -> RegraBlock()
            else -> IndependenciaBlock()
        }
    }
}

@Composable
private fun ProjecaoBlock() {
    val s = state()
    var mode by remember { mutableStateOf("mes") }
    val days = if (mode == "mes") Math.max(1, daysUntilMonthEnd(s)) else 30
    val p = remember(Store.rev, days) { project(s, days) }
    Card {
        H2("🔮 Saldo previsto")
        ChipRow(listOf("mes" to "Até o fim do mês", "30" to "Próximos 30 dias"), mode) { mode = it }
        KV("Hoje", money(p.startCash))
        KV("Em ${formatSimDate(p.endISO)}", money(p.endCash), if (p.endCash >= p.startCash) P.green else P.red)
        KV("Menor saldo no caminho", money(p.minCash), if (p.minCash < 0.005) P.red else P.text)
        if (p.minCash < 0.005) Txt("⚠️ O saldo chega a zero em ${formatSimDate(p.minISO)}. Alguma conta pode ficar sem pagar.", color = P.red, style = T.small)
        Muted("Usa as mesmas regras do app (salário no 5º dia útil, gastos fixos, fatura, dividendos, assinaturas). É uma estimativa.")
    }
    if (p.events.isNotEmpty()) {
        H2("O que vai acontecer")
        p.events.forEach { e ->
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("${HIST_TYPE_ICON[e.type] ?: "•"} ${e.label}", Modifier.weight(1f), style = T.small)
                    Txt(money(e.amount), color = if (e.amount > 0) P.green else P.red, bold = true, style = T.small)
                }
                Muted(formatSimDate(e.dateISO))
            }
        }
    }
}

@Composable
private fun CalendarioBlock() {
    val s = state()
    val items = remember(Store.rev) { upcomingCalendar(s, 45) }
    Card { H2("📅 Próximos 45 dias"); Muted("Salário, contas, fatura, DARF e dividendos por data.") }
    if (items.isEmpty()) Muted("Nada previsto.")
    items.groupBy { it.dateISO }.forEach { (d, list) ->
        Card {
            Txt(formatSimDate(d), bold = true, color = P.gold)
            list.forEach { i ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("${HIST_TYPE_ICON[i.kind] ?: "•"} ${i.label}", Modifier.weight(1f), style = T.small)
                    Txt(money(i.amount), color = if (i.amount > 0) P.green else P.red, style = T.small, bold = true)
                }
            }
        }
    }
}

@Composable
fun OrcamentoBlock() {
    val s = state()
    var edit by remember { mutableStateOf(false) }
    Card {
        H2("🎯 Orçamento do mês")
        Muted("Defina um teto por categoria. O app avisa ao chegar em ${s.settings.notifyFreeSpendPct}%.")
        PrimaryButton("＋ Definir teto") { edit = true }
    }
    val lines = budgetLines(s)
    if (lines.isEmpty()) Muted("Nenhum teto definido.")
    lines.forEach { l ->
        Card(onClick = { edit = true }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(l.category, bold = true); Txt("${money(l.spent)} / ${money(l.limit)}", style = T.small)
            }
            Progress(l.frac, if (l.frac >= 1) P.red else if (l.frac >= 0.8) P.gold else P.green)
            Muted(if (l.frac >= 1) "Estourou ${formatBRL(l.spent - l.limit)}" else "Restam ${formatBRL(l.limit - l.spent)}")
        }
    }
    if (edit) {
        var cat by remember { mutableStateOf(budgetCategories(s).first()) }
        var v by remember { mutableStateOf<Double?>(s.budgets[cat]) }
        FormDialog("Teto mensal", { edit = false }, "Salvar", {
            Store.mutate { st -> if ((v ?: 0.0) > 0) st.budgets[cat] = v!! else st.budgets.remove(cat) }; true
        }) {
            ChipRow(budgetCategories(s).map { it to it }, cat) { cat = it; v = s.budgets[it] }
            key(cat) { NumField(v, { v = it }, "Teto para $cat", suffix = "R$") }
            Muted("Deixe em branco para remover o teto.")
        }
    }
}

@Composable
fun AssinaturasBlock() {
    val s = state()
    var edit by remember { mutableStateOf<Subscription?>(null) }
    var isNew by remember { mutableStateOf(false) }
    Card {
        H2("🔁 Assinaturas e recorrentes")
        KV("Total por mês", money(subscriptionsMonthlyTotal(s)), P.red)
        Muted("Cobradas sozinhas no dia escolhido, em Gasto livre.")
        PrimaryButton("＋ Nova assinatura") { edit = Subscription(); isNew = true }
    }
    s.subscriptions.forEach { sub ->
        Card(onClick = { edit = sub; isNew = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Txt(sub.name + if (!sub.active) " (pausada)" else "", bold = true); Muted("todo dia ${sub.day} · ${sub.category}") }
                Txt(money(sub.amount), bold = true)
            }
        }
    }
    edit?.let { sub ->
        var name by remember { mutableStateOf(sub.name) }; var amt by remember { mutableStateOf<Double?>(sub.amount) }
        var day by remember { mutableStateOf(sub.day.toDouble().coerceAtLeast(1.0)) }; var cat by remember { mutableStateOf(sub.category) }
        var active by remember { mutableStateOf(sub.active) }
        FormDialog(if (isNew) "Nova assinatura" else "Editar assinatura", { edit = null }, "Salvar", {
            if (name.isBlank() || (amt ?: 0.0) <= 0) { Ui.show("Informe nome e valor."); false } else {
                Store.mutate { st ->
                    if (isNew) addSubscription(st, name, amt ?: 0.0, day.toInt(), cat)
                    else { sub.name = name.trim(); sub.amount = amt ?: 0.0; sub.day = clampI(day.toInt(), 1, 28); sub.category = cat; sub.active = active }
                }; true
            }
        }) {
            Field(name, { name = it }, "Nome (ex.: Netflix)")
            NumField(amt, { amt = it }, "Valor mensal", suffix = "R$")
            NumField(day, { day = it ?: 1.0 }, "Dia da cobrança (1 a 28)", decimals = false)
            ChipRow(s.freeSpendCategories.map { it to it }, cat) { cat = it }
            if (!isNew) {
                SwitchRow("Ativa", active) { active = it }
                GhostButton("Excluir", danger = true) { Store.mutate { st -> st.subscriptions.removeAll { it.id == sub.id } }; edit = null }
            }
        }
    }
}

@Composable
private fun IndependenciaBlock() {
    val s = state()
    val f = fiStatus(s)
    var target by remember { mutableStateOf<Double?>(s.fiTargetMonthly) }
    Card {
        H2("🏝 Independência financeira")
        Muted("Quando seus dividendos pagam as suas contas.")
        Txt("${Math.round(f.pct * 100)}%", style = T.big, bold = true, color = if (f.pct >= 1) P.green else P.gold)
        Progress(f.pct, if (f.pct >= 1) P.green else P.gold)
        KV("Dividendos por mês", money(f.monthlyIncome), P.green)
        KV(if (f.targetFromExpenses) "Gastos fixos + assinaturas" else "Sua meta de renda", money(f.target))
        when {
            f.target <= 0 -> Muted("Cadastre gastos fixos ou defina uma meta abaixo.")
            f.monthsToGoal == 0 -> Txt("🎉 Seus dividendos já cobrem a meta!", color = P.green, bold = true)
            else -> {
                val r = remember(Store.rev, f.target) { incomeGoal(investParams(s, withInflation = false), f.target, false) }
                if (r.months != null) Txt("Pelo seu histórico de aportes e dividendos, você chega lá em ~${monthsLabel(r.months)}.", color = P.gold)
                else Muted("Com os aportes e dividendos atuais não dá para estimar. Veja Investir → Metas para ajustar as premissas.")
            }
        }
        Muted("Mesmo cálculo de Investir → Metas, com dados reais e juros compostos.")
    }
    Card {
        H2("Meta de renda mensal")
        NumField(target, { target = it; Store.mutate { st -> st.fiTargetMonthly = it ?: 0.0 } }, "Renda desejada por mês (vazio = usar gastos fixos)", suffix = "R$")
    }
}


@Composable
private fun DividasBlock() {
    val s = state()
    var edit by remember { mutableStateOf<Debt?>(null) }; var isNew by remember { mutableStateOf(false) }
    val plan = remember(Store.rev) { debtPlan(s.debts, s.debtExtraMonthly, s.debtStrategy) }
    Card {
        H2("💳 Plano para sair das dívidas")
        if (s.debts.isEmpty()) Muted("Nenhuma dívida cadastrada. Se tiver cartão rotativo, empréstimo ou cheque especial, cadastre para ver quando e quanto vai pagar de juros.")
        else {
            KV("Total devido", money(debtTotal(s)), P.red)
            KV("Parcelas mínimas por mês", money(debtMinTotal(s)))
        }
        PrimaryButton("＋ Nova dívida") { edit = Debt(); isNew = true }
    }
    if (s.debts.isNotEmpty()) {
        Card {
            H2("Estratégia")
            ChipRow(listOf("avalanche" to "Maior juro primeiro", "snowball" to "Menor saldo primeiro"), s.debtStrategy) { Store.mutate { st -> st.debtStrategy = it } }
            Muted(if (s.debtStrategy == "avalanche") "Paga menos juros no total." else "Quita as pequenas rápido e motiva.")
            NumField(s.debtExtraMonthly, { Store.mutate { st -> st.debtExtraMonthly = it ?: 0.0 } }, "Valor extra por mês além dos mínimos", suffix = "R$")
            val m = plan.months
            if (m == null) Txt("⚠️ Com esse pagamento a dívida não acaba: os juros comem as parcelas. Aumente o valor extra.", color = P.red)
            else {
                KV("Livre das dívidas em", if (m >= 12) "${m / 12} ano(s) e ${m % 12} mês(es)" else "$m mês(es)", P.green)
                KV("Juros totais", money(plan.totalInterest), P.red)
                KV("Total pago", money(plan.totalPaid))
                plan.payoffOrder.forEach { (n, mo) -> Muted("✔ $n quitada no mês $mo") }
                if (s.debtExtraMonthly > 0) {
                    val base = debtPlan(s.debts, 0.0, s.debtStrategy)
                    val bm = base.months
                    if (bm != null) Txt("Com o extra você economiza ${money(base.totalInterest - plan.totalInterest)} de juros e ${bm - m} mês(es).", color = P.green)
                }
            }
            Muted("Simulação com juros compostos mensais; os valores reais dependem do contrato.")
        }
    }
    s.debts.forEach { d ->
        Card(onClick = { edit = d; isNew = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Txt(d.name, bold = true); Muted("${DEBT_KINDS[d.kind] ?: d.kind} · ${fmtPct(d.ratePct)} ao mês · mín. ${money(d.minPayment)}") }
                Txt(money(d.balance), bold = true, color = P.red)
            }
        }
    }
    edit?.let { d ->
        var name by remember { mutableStateOf(d.name) }; var kind by remember { mutableStateOf(d.kind) }
        var bal by remember { mutableStateOf<Double?>(d.balance) }; var rate by remember { mutableStateOf<Double?>(d.ratePct) }
        var min by remember { mutableStateOf<Double?>(d.minPayment) }
        FormDialog(if (isNew) "Nova dívida" else "Editar dívida", { edit = null }, "Salvar", {
            if (name.isBlank() || (bal ?: 0.0) <= 0) { Ui.show("Informe nome e saldo devedor."); false } else {
                Store.mutate { st ->
                    if (isNew) st.debts.add(Debt(st.nextDebtId++, name.trim(), kind, bal ?: 0.0, rate ?: 0.0, min ?: 0.0))
                    else { d.name = name.trim(); d.kind = kind; d.balance = bal ?: 0.0; d.ratePct = rate ?: 0.0; d.minPayment = min ?: 0.0 }
                }; true
            }
        }) {
            Field(name, { name = it }, "Nome (ex.: Cartão do banco)")
            ChipRow(DEBT_KINDS.map { it.key to it.value }, kind) { kind = it }
            NumField(bal, { bal = it }, "Saldo devedor", suffix = "R$")
            NumField(rate, { rate = it }, "Juros ao mês (%)", suffix = "%")
            NumField(min, { min = it }, "Parcela mínima por mês", suffix = "R$")
            if (!isNew) GhostButton("Excluir (quitada)", danger = true) { Store.mutate { st -> st.debts.removeAll { it.id == d.id } }; edit = null }
        }
    }
}

@Composable
private fun ReservaBlock() {
    val s = state()
    val ess = essentialMonthly(s)
    val target = emergencyTarget(s)
    val have = reservesTotalBalance(s)
    val covered = emergencyMonthsCovered(s)
    Card {
        H2("🛟 Reserva de emergência")
        Muted("É o dinheiro que cobre seus gastos essenciais se a renda faltar. O ideal costuma ser de 3 a 12 meses, conforme a estabilidade da sua renda.")
        ChipRow(listOf("3.0" to "3 meses", "6.0" to "6 meses", "9.0" to "9 meses", "12.0" to "12 meses"), s.emergencyMonthsTarget.toString()) { Store.mutate { st -> st.emergencyMonthsTarget = it.toDouble() } }
        KV("Gastos essenciais por mês", money(ess))
        KV("Meta da reserva", money(target))
        KV("Você tem nas caixinhas", money(have), P.green)
        Progress(if (target > 0) have / target else 0.0, P.green)
        if (ess <= 0) Muted("Cadastre seus gastos fixos e assinaturas para calcular a meta.")
        else Txt("Hoje sua reserva cobre ${"%.1f".format(covered)} mês(es) de gastos essenciais.", bold = true)
        if (target > have) Muted("Faltam ${money(target - have)}.")
    }
    if (s.reserves.isNotEmpty() && target > 0) {
        Card {
            H2("Definir como meta de uma caixinha")
            s.reserves.forEach { r -> GhostButton("${r.name} → meta ${money(target)}", Modifier.fillMaxWidth()) { Store.mutate { _ -> r.targetValue = target }; Ui.show("Meta definida") } }
        }
    }
}

@Composable
private fun RegraBlock() {
    val s = state()
    val r = rule503020(s)
    Card {
        H2("⚖️ Regra 50-30-20 do mês")
        Muted("Sugestão: até 50% da renda em necessidades, 30% em desejos e 20% para o futuro (reserva, dívidas, investimentos).")
        if (r.income <= 0) Muted("Cadastre sua renda para ver a análise.")
        else {
            KV("Renda considerada", money(r.income))
            Txt("Necessidades · ${"%.0f".format(r.needsPct * 100)}% (meta 50%)", bold = true)
            Progress(r.needsPct / 0.5, if (r.needsPct <= 0.5) P.green else P.red); Muted(money(r.needs))
            Txt("Desejos · ${"%.0f".format(r.wantsPct * 100)}% (meta 30%)", bold = true)
            Progress(r.wantsPct / 0.3, if (r.wantsPct <= 0.3) P.green else P.red); Muted(money(r.wants))
            Txt("Futuro · ${"%.0f".format(r.futurePct * 100)}% (meta 20%)", bold = true)
            Progress(r.futurePct / 0.2, if (r.futurePct >= 0.2) P.green else P.brand); Muted(money(r.future))
            Muted("É só uma referência: ajuste à sua realidade.")
        }
    }
}
