package com.meubanco.patrimonio.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

val GOALS = listOf(
    "controle" to "📒 Controlar meus gastos",
    "dividas" to "🔗 Sair das dívidas",
    "reserva" to "🛟 Montar minha reserva de emergência",
    "investir" to "📈 Começar a investir",
    "renda" to "🏝 Viver de renda (dividendos)",
)

@Composable
fun OnboardingScreen() {
    val s = state()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val d = remember { newOnboardingDraft(s) }
    var idx by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    val steps = onboardingSteps(d)
    val step = steps.getOrElse(idx) { "done" }
    var err by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var newExp by remember { mutableStateOf("") }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    BackHandler(enabled = idx > 0) { idx-- }

    fun next() {
        err = ""
        when (step) {
            "name" -> if (d.name.isBlank()) { err = "Digite seu nome para continuar."; return }
            "hireDate" -> if (d.hireDateISO.isBlank()) { err = "Escolha a data de admissão (ou volte e mude o tipo de renda)."; return }
            "income" -> if (d.incomeType == "empregado" && d.baseSalary <= 0) { err = "Informe o salário bruto."; return }
                else if (d.incomeType == "renda" && d.beneficioAmount <= 0) { err = "Informe o valor mensal."; return }
            "secondaryIncomeDetails" -> if (d.secondaryAmount <= 0) { err = "Informe o valor da outra renda."; return }
            "cardLimit" -> if (d.cardLimit <= 0) { err = "Informe o limite do cartão."; return }
            "debts" -> if (d.debts.none { it.balance > 0 }) { err = "Informe ao menos uma dívida (ou volte e escolha \"Não\")."; return }
            "security" -> if (pin.isNotEmpty() && pin.length < 4) { err = "O PIN precisa ter ao menos 4 dígitos."; return }
                else if (pin.isNotEmpty() && pin != pin2) { err = "Os dois PINs precisam ser iguais."; return }
        }
        if (idx < steps.size - 1) idx++
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted("Passo ${idx + 1} de ${steps.size}")
        Progress((idx + 1).toDouble() / steps.size)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenTransition(step, steps) { stp -> when (stp) {
                "welcome" -> {
                    Txt("Bem-vindo! 👋", style = T.big, bold = true)
                    Txt("Em poucos passos você monta o seu banco pessoal: contas, cartão, reservas, dívidas e investimentos.")
                    Muted("Leva cerca de 3 minutos. Tudo fica só no seu celular e você pode mudar qualquer resposta depois em Configurações.")
                    Card {
                        H2("🔐 Entre com Google")
                        Muted("Se você já usava o app, seus dados voltam sozinhos. Se é a primeira vez, é só continuar o cadastro.")
                        GoogleAccountBox()
                    }
                }
                "name" -> { LaunchedEffect(Unit) { if (d.name.isBlank()) { val g = com.meubanco.patrimonio.data.GoogleDrive.name(ctx)?.trim()?.split(" ")?.firstOrNull().orEmpty(); if (g.isNotEmpty()) { d.name = g; tick++ } } }
                    H2("Como podemos te chamar?"); Field(d.name, { d.name = it; tick++ }, "Nome") }
                "goal" -> {
                    H2("O que você mais quer agora?")
                    Muted("Vamos priorizar as dicas e a tela inicial para esse objetivo.")
                    GOALS.forEach { (k, label) -> Chip(label, d.mainGoal == k) { d.mainGoal = k; tick++ } }
                }
                "birthSex" -> {
                    H2("Nascimento e sexo (opcional)")
                    Muted("Usado para parabéns no seu aniversário e dicas de aposentadoria.")
                    DateField("Nascimento", d.birthDateISO, { d.birthDateISO = it; tick++ })
                    ChipRow(listOf("" to "Prefiro não dizer", "F" to "Feminino", "M" to "Masculino"), d.sex) { d.sex = it; tick++ }
                }
                "incomeType" -> {
                    H2("Qual é a sua fonte de renda?")
                    ChipRow(listOf("empregado" to "Empregado (CLT)", "autonomo" to "Autônomo / MEI", "renda" to "Benefício / aposentadoria"), d.incomeType) { d.incomeType = it; tick++ }
                    Muted(when (d.incomeType) {
                        "empregado" -> "Calculamos INSS, IR, 13º, férias e FGTS automaticamente."
                        "autonomo" -> "Você registra as entradas (Pix recebido) e o app acompanha o resto."
                        else -> "Um valor fixo todo mês, no dia que você escolher."
                    })
                }
                "hireDate" -> { H2("Quando você foi contratado?"); DateField("Admissão", d.hireDateISO, { d.hireDateISO = it; tick++ }, defaultYear = 2022); Muted("Usada para férias e FGTS.") }
                "income" -> {
                    if (d.incomeType == "empregado") { H2("Salário base bruto"); NumField(d.baseSalary, { d.baseSalary = it ?: 0.0 }, "Salário bruto", suffix = "R$") }
                    else {
                        H2("Benefício / renda mensal")
                        Field(d.beneficioLabel, { d.beneficioLabel = it; tick++ }, "Nome (ex.: Aposentadoria)")
                        NumField(d.beneficioAmount, { d.beneficioAmount = it ?: 0.0 }, "Valor mensal", suffix = "R$")
                    }
                }
                "hasSecondaryIncome" -> { H2("Tem outra renda?"); Muted("Bico, aluguel, pensão…"); ChipRow(listOf("n" to "Não", "s" to "Sim"), if (d.hasSecondaryIncome) "s" else "n") { d.hasSecondaryIncome = it == "s"; tick++ } }
                "secondaryIncomeDetails" -> {
                    H2("Outra renda")
                    Field(d.secondaryLabel, { d.secondaryLabel = it; tick++ }, "Nome")
                    NumField(d.secondaryAmount, { d.secondaryAmount = it ?: 0.0 }, "Valor mensal", suffix = "R$")
                    NumField(d.secondaryPayDay.toDouble(), { d.secondaryPayDay = clampI((it ?: 5.0).toInt(), 1, 28) }, "Dia do mês que cai", decimals = false)
                }
                "hasCard" -> { H2("Você usa cartão de crédito?"); ChipRow(listOf("n" to "Não", "s" to "Sim"), if (d.hasCard) "s" else "n") { d.hasCard = it == "s"; tick++ } }
                "cardLimit" -> { H2("Limite do cartão"); NumField(d.cardLimit, { d.cardLimit = it ?: 0.0 }, "Limite", suffix = "R$") }
                "wantExpenses" -> { H2("Quer cadastrar seus gastos fixos agora?"); Muted("Aluguel, luz, internet… Dá para fazer depois."); ChipRow(listOf("n" to "Depois", "s" to "Sim"), if (d.wantExpenses) "s" else "n") { d.wantExpenses = it == "s"; tick++ } }
                "expenses" -> {
                    H2("Gastos fixos do mês")
                    Muted("Toque para adicionar os mais comuns e preencha o valor. Pode deixar o resto para depois.")
                    ChipRow(COMMON_EXPENSES.map { it to it }, "") { n -> if (d.expenses.none { e -> e.name.equals(n, true) }) { d.expenses.add(ObExpense(n, 0.0, "Outros")); tick++ } }
                    d.expenses.forEachIndexed { i, e ->
                        Card { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Txt(e.name, bold = true); androidx.compose.material3.TextButton({ d.expenses.removeAt(i); tick++ }) { androidx.compose.material3.Text("Remover") } }
                            NumField(e.amount, { e.amount = it ?: 0.0; tick++ }, "Valor", suffix = "R$") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(newExp, { newExp = it }, "Outro gasto fixo", Modifier.weight(1f))
                        GhostButton("＋", enabled = newExp.isNotBlank()) { d.expenses.add(ObExpense(newExp.trim(), 0.0, "Outros")); newExp = ""; tick++ }
                    }
                    val ins = onboardingInsights(d)
                    if (ins.fixedTotal > 0) Muted("Total: ${formatBRL(ins.fixedTotal)}" + (ins.fixedPct?.let { " · ${fmtPlain(Math.round(it * 10) / 10.0)}% da renda informada" } ?: ""))
                }
                "balance" -> { H2("Saldo atual na conta"); Muted("Pode ser aproximado. Você ajusta quando quiser."); NumField(d.cash, { d.cash = it ?: 0.0 }, "Saldo", suffix = "R$") }
                "reserve" -> {
                    H2("Você já tem dinheiro guardado?")
                    Muted("Reserva de emergência, poupança, CDB… Vai para a caixinha \"Reserva de emergência\". Pode ser zero.")
                    NumField(d.reserveStart, { d.reserveStart = it ?: 0.0; tick++ }, "Valor guardado", suffix = "R$")
                    val ins = onboardingInsights(d)
                    if (ins.fixedTotal > 0) {
                        Muted("Uma meta comum é guardar 6 meses de gastos fixos: ${formatBRL(ins.reserveTarget)}.")
                        ins.reserveMonths?.let { Progress(it / 6.0); Muted("Hoje você cobre ${fmtPlain(Math.round(it * 10) / 10.0)} mes(es) de gastos fixos.") }
                    }
                }
                "hasDebts" -> {
                    H2("Você tem dívidas?")
                    Muted("Cartão rotativo, empréstimo, cheque especial, financiamento. Vamos montar um plano para quitar.")
                    ChipRow(listOf("n" to "Não", "s" to "Sim"), if (d.hasDebts) "s" else "n") {
                        d.hasDebts = it == "s"; if (d.hasDebts && d.debts.isEmpty()) d.debts.add(ObDebt()); tick++
                    }
                }
                "debts" -> {
                    H2("Suas dívidas")
                    d.debts.forEachIndexed { i, db ->
                        Card {
                            Field(db.name, { db.name = it; tick++ }, "Nome (ex.: Cartão Nubank)")
                            ChipRow(DEBT_KINDS.map { it.key to it.value }, db.kind) { db.kind = it; tick++ }
                            NumField(db.balance, { db.balance = it ?: 0.0 }, "Quanto você deve", suffix = "R$")
                            NumField(db.ratePct, { db.ratePct = it ?: 0.0 }, "Juros ao mês (se não souber, deixe vazio)", suffix = "%")
                            NumField(db.minPayment, { db.minPayment = it ?: 0.0 }, "Parcela mínima por mês", suffix = "R$")
                            if (d.debts.size > 1) GhostButton("Remover", danger = true) { d.debts.removeAt(i); tick++ }
                        }
                    }
                    GhostButton("＋ Outra dívida") { d.debts.add(ObDebt()); tick++ }
                }
                "security" -> {
                    H2("Proteja o seu app")
                    Muted("Opcional. Um PIN impede que outra pessoa abra o app se pegar o seu celular.")
                    Field(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, "PIN (4 a 8 dígitos)", keyboard = KeyboardType.NumberPassword)
                    if (pin.isNotEmpty()) Field(pin2, { pin2 = it.filter { c -> c.isDigit() }.take(8) }, "Repita o PIN", keyboard = KeyboardType.NumberPassword)
                    if (Build.VERSION.SDK_INT >= 33) {
                        Spacer8()
                        Muted("Avisos de fatura, orçamento e backup precisam da permissão de notificações.")
                        GhostButton("Permitir avisos") { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    }
                }
                "summary" -> {
                    H2("Confira o resumo")
                    Card {
                        KV("Nome", d.name)
                        KV("Objetivo", GOALS.firstOrNull { it.first == d.mainGoal }?.second ?: "—")
                        KV("Renda", when (d.incomeType) { "empregado" -> "CLT · " + formatBRL(d.baseSalary); "renda" -> formatBRL(d.beneficioAmount); else -> "Autônomo" })
                        if (d.hasSecondaryIncome) KV("Outra renda", formatBRL(d.secondaryAmount))
                        KV("Cartão", if (d.hasCard) "limite " + formatBRL(d.cardLimit) else "não usa")
                        KV("Saldo na conta", formatBRL(d.cash))
                        KV("Reserva guardada", formatBRL(d.reserveStart))
                        KV("Dívidas", if (d.hasDebts) formatBRL(d.debts.sumOf { it.balance }) else "nenhuma")
                        KV("PIN", if (pin.length >= 4) "ativado" else "não")
                    }
                    val ins = onboardingInsights(d)
                    Card {
                        H2("📊 Seu ponto de partida")
                        if (ins.fixedTotal > 0) KV("Gastos fixos", formatBRL(ins.fixedTotal) + (ins.fixedPct?.let { " (${fmtPlain(Math.round(it * 10) / 10.0)}% da renda)" } ?: ""))
                        ins.reserveMonths?.let { KV("Reserva cobre", fmtPlain(Math.round(it * 10) / 10.0) + " mes(es)") }
                        if (ins.debtTotal > 0) KV("Dívidas", formatBRL(ins.debtTotal))
                        ins.tips.forEach { Muted("• $it") }
                    }
                    Muted("Você pode voltar e corrigir qualquer passo.")
                }
                "done" -> { Txt("Tudo pronto! 🎉", style = T.big, bold = true); Txt("Seu banco será criado agora.") }
            } }
            if (err.isNotEmpty()) Txt(err, color = P.red)
        }
        if (step == "done") PrimaryButton("Começar") {
            Store.mutate { st ->
                finishOnboarding(st, d)
                if (pin.length >= 4) { st.settings.pinHash = hashPin(pin); st.settings.pinEnabled = true }
                st.activeMainTab = "conta"
            }
        } else PrimaryButton(if (step == "welcome") "Vamos lá" else "Continuar") { next() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (idx > 0) GhostButton("Voltar", Modifier.weight(1f)) { idx-- }
            if (step in setOf("birthSex", "hasSecondaryIncome", "reserve", "security", "wantExpenses")) GhostButton("Pular", Modifier.weight(1f)) { if (idx < steps.size - 1) idx++ }
        }
    }
}
