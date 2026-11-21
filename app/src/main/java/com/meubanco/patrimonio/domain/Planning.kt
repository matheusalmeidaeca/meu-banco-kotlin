package com.meubanco.patrimonio.domain

/* ===================== renda estimada ===================== */

fun estimatedMonthlyIncome(s: AppState): Double {
    val d = isoToDate(s.simDateISO)
    var base = when (s.income.incomeType) {
        "empregado" -> computeMonthlyIncome(s, d.year, d.monthIdx()).net
        "renda" -> s.income.beneficioAmount
        else -> {
            val last = (1..3).map { s.monthlyStats[shiftYm(ymKey(s.simDateISO), -it)]?.income ?: 0.0 }.filter { it > 0 }
            if (last.isEmpty()) 0.0 else last.average()
        }
    }
    if (s.secondaryIncome.enabled) base += s.secondaryIncome.amount
    return round2(base)
}

/* ===================== dívidas ===================== */

val DEBT_KINDS = linkedMapOf("cartao" to "Cartão / rotativo", "emprestimo" to "Empréstimo", "cheque" to "Cheque especial",
    "financiamento" to "Financiamento", "outro" to "Outra")

class DebtPlan(
    val months: Int?,            // null = nunca quita com esse pagamento
    val totalInterest: Double,
    val totalPaid: Double,
    val payoffOrder: List<Pair<String, Int>>, // nome → mês em que quita
)

fun debtTotal(s: AppState) = s.debts.sumOf { it.balance }
fun debtMinTotal(s: AppState) = s.debts.sumOf { Math.min(it.minPayment, it.balance) }

/** Simula o pagamento: mínimos em todas + `extra` por mês na dívida-alvo. strategy: "avalanche" (maior juro) ou "snowball" (menor saldo). */
fun debtPlan(debts: List<Debt>, extra: Double, strategy: String): DebtPlan {
    class D(val name: String, var bal: Double, val rate: Double, val min: Double)
    val ds = debts.filter { it.balance > 0 }.map { D(it.name, it.balance, it.ratePct / 100, it.minPayment) }
    if (ds.isEmpty()) return DebtPlan(0, 0.0, 0.0, emptyList())
    val budget = ds.sumOf { it.min } + Math.max(0.0, extra)
    var interest = 0.0; var paid = 0.0
    val order = mutableListOf<Pair<String, Int>>()
    var month = 0
    while (month < 600) {
        month++
        ds.forEach { if (it.bal > 0) { val i = it.bal * it.rate; it.bal += i; interest += i } }
        var left = budget
        // mínimos
        ds.filter { it.bal > 0 }.forEach { val p = Math.min(it.min, it.bal); it.bal -= p; left -= p; paid += p }
        // sobra para a dívida-alvo
        val targets = ds.filter { it.bal > 0.005 }.let { l ->
            if (strategy == "snowball") l.sortedBy { it.bal } else l.sortedByDescending { it.rate }
        }
        for (t in targets) {
            if (left <= 0.0) break
            val p = Math.min(left, t.bal); t.bal -= p; left -= p; paid += p
        }
        ds.forEach { if (it.bal <= 0.005 && order.none { o -> o.first == it.name }) { it.bal = 0.0; order.add(it.name to month) } }
        if (ds.all { it.bal <= 0.005 }) return DebtPlan(month, round2(interest), round2(paid), order)
        // saldo crescendo sem chance de quitar
        if (month > 24 && ds.sumOf { it.bal } > debts.sumOf { it.balance } * 3) break
    }
    return DebtPlan(null, round2(interest), round2(paid), order)
}

fun debtInsights(s: AppState): List<Insight> {
    if (s.debts.none { it.balance > 0 }) return emptyList()
    val out = mutableListOf<Insight>()
    val worst = s.debts.filter { it.balance > 0 }.maxByOrNull { it.ratePct }!!
    out.add(Insight("🔗", "Priorize a dívida mais cara: ${worst.name}",
        "Juros de ${fmtPct(worst.ratePct)}% ao mês sobre ${formatBRL(worst.balance)}. Cada real extra aqui rende mais que qualquer investimento.", "Planejamento", "plano"))
    val inc = estimatedMonthlyIncome(s)
    if (inc > 0 && debtMinTotal(s) / inc >= 0.3)
        out.add(Insight("🚨", "Dívidas pesadas no orçamento", "As parcelas mínimas já levam ${Math.round(debtMinTotal(s) / inc * 100)}% da sua renda."))
    for (c in allCards(s)) if (c.enabled && c.pendingAmount > s.cash + 0.01 && c.pendingAmount > 0)
        out.add(Insight("💳", "Fatura maior que o saldo", "A fatura de ${formatBRL(c.pendingAmount)} passa do saldo em conta. Pagar só uma parte joga o resto no rotativo, que tem juros altíssimos."))
    return out
}

fun fmtPct(v: Double): String = (Math.round(v * 100) / 100.0).toString().replace('.', ',').removeSuffix(",0")

/* ===================== saúde financeira ===================== */

class HealthPart(val title: String, val points: Int, val max: Int, val tip: String)
class Health(val score: Int, val label: String, val parts: List<HealthPart>)

fun essentialMonthly(s: AppState) = fixedExpensesTotal(s) + subscriptionsMonthlyTotal(s)

fun emergencyTarget(s: AppState, months: Double = s.emergencyMonthsTarget) = round2(essentialMonthly(s) * months)
fun emergencyMonthsCovered(s: AppState): Double { val e = essentialMonthly(s); return if (e > 0) reservesTotalBalance(s) / e else 0.0 }

private fun lerp01(v: Double, bad: Double, good: Double): Double = clamp((v - bad) / (good - bad), 0.0, 1.0)

fun financialHealth(s: AppState): Health {
    val inc = estimatedMonthlyIncome(s)
    val parts = mutableListOf<HealthPart>()
    // 1 reserva
    val months = emergencyMonthsCovered(s)
    val p1 = if (essentialMonthly(s) <= 0) 10 else Math.round(lerp01(months, 0.0, 6.0) * 20).toInt()
    parts.add(HealthPart("Reserva de emergência", p1, 20,
        if (essentialMonthly(s) <= 0) "Cadastre seus gastos fixos para medir." else "Você cobre ${fmtPct(Math.round(months * 10) / 10.0)} mês(es) de gastos. A meta comum é 6."))
    // 2 poupança
    val prev = (1..3).mapNotNull { s.monthlyStats[shiftYm(ymKey(s.simDateISO), -it)] }
    val invested = if (prev.isEmpty()) 0.0 else prev.map { it.invested }.average()
    val p2 = if (inc <= 0) 10 else Math.round(lerp01(invested / inc, 0.0, 0.2) * 20).toInt()
    parts.add(HealthPart("Hábito de guardar", p2, 20, "Você guarda ${Math.round((if (inc > 0) invested / inc else 0.0) * 100)}% da renda (média de 3 meses). Boa meta: 20%."))
    // 3 dívidas
    val debtShare = if (inc > 0) debtMinTotal(s) / inc else 0.0
    val pendingCard = allCards(s).sumOf { it.pendingAmount }
    var p3 = Math.round((1 - lerp01(debtShare, 0.0, 0.3)) * 20).toInt()
    if (pendingCard > s.cash + 0.01) p3 = Math.min(p3, 8)
    parts.add(HealthPart("Dívidas", p3, 20, if (s.debts.isEmpty() && pendingCard <= 0) "Sem dívidas cadastradas. 👏" else "Parcelas mínimas: ${Math.round(debtShare * 100)}% da renda."))
    // 4 cartão
    val c = activeCard(s)
    val usage = if (c.enabled && c.limit > 0) (c.invoiceCurrent + c.pendingAmount) / c.limit else 0.0
    val p4 = Math.round((1 - lerp01(usage, 0.3, 0.8)) * 20).toInt()
    parts.add(HealthPart("Uso do cartão", p4, 20, if (!c.enabled) "Sem cartão." else "Usando ${Math.round(usage * 100)}% do limite. Abaixo de 30% é o ideal."))
    // 5 gastos
    val last = s.monthlyStats[shiftYm(ymKey(s.simDateISO), -1)]
    val ratio = if (inc > 0 && last != null) last.spent / inc else 0.0
    val p5 = if (inc <= 0 || last == null) 10 else Math.round((1 - lerp01(ratio, 0.7, 1.0)) * 20).toInt()
    parts.add(HealthPart("Gastos x renda", p5, 20, if (last == null) "Ainda sem um mês completo para comparar." else "No mês passado você gastou ${Math.round(ratio * 100)}% da renda."))
    val score = parts.sumOf { it.points }
    val label = when { score >= 80 -> "Excelente"; score >= 60 -> "Boa"; score >= 40 -> "Atenção"; else -> "Crítica" }
    return Health(score, label, parts)
}

/* ===================== 50-30-20 ===================== */

val NEED_CATEGORIES = setOf("Moradia", "Contas", "Alimentação", "Transporte", "Saúde", "Educação", "Gastos fixos", "Cartão")

class Rule503020(val income: Double, val needs: Double, val wants: Double, val future: Double) {
    val needsPct get() = if (income > 0) needs / income else 0.0
    val wantsPct get() = if (income > 0) wants / income else 0.0
    val futurePct get() = if (income > 0) future / income else 0.0
}

fun rule503020(s: AppState): Rule503020 {
    val st = s.monthlyStats[ymKey(s.simDateISO)]
    var needs = 0.0; var wants = 0.0
    st?.cats?.forEach { (c, v) -> if (c in NEED_CATEGORIES) needs += v else wants += v }
    val income = Math.max(st?.income ?: 0.0, estimatedMonthlyIncome(s))
    return Rule503020(income, round2(needs), round2(wants), round2(st?.invested ?: 0.0))
}

/* ===================== simulador de aporte ===================== */

fun futureValue(start: Double, monthly: Double, annualPct: Double, years: Int): Double {
    val r = Math.pow(1 + annualPct / 100, 1.0 / 12) - 1
    var v = start
    repeat(years * 12) { v = v * (1 + r) + monthly }
    return round2(v)
}

/* ===================== Pix ===================== */

fun pixRecents(s: AppState, max: Int = 5): List<String> =
    s.history.filter { it.type == "pix" && it.label.startsWith("Pix enviado para ") }
        .map { it.label.removePrefix("Pix enviado para ") }.distinct().take(max)

fun pixIsNewRecipient(s: AppState, name: String) =
    s.history.none { it.type == "pix" && it.label == "Pix enviado para ${name.trim()}" }

val SCAM_TIPS = listOf(
    "Nunca faça Pix para \"liberar\" empréstimo, prêmio ou cashback. Quem pede dinheiro antes de entregar algo está aplicando golpe.",
    "Desconfie de pressa: golpistas criam urgência (\"é agora ou perde\"). Pare e confirme por outro canal.",
    "Se alguém mandou mensagem pedindo dinheiro \"de um número novo\", ligue para a pessoa antes de enviar.",
    "Seu banco nunca pede senha, token ou código por mensagem, telefone ou link.",
    "Confira sempre o nome do destinatário que aparece antes de confirmar o Pix.",
    "Defina um limite diário de Pix em Configurações para reduzir o estrago se algo der errado.",
)

/* ===================== exportação ===================== */

fun historyToCsv(entries: List<HistoryEntry>): String {
    val sb = StringBuilder("Data;Tipo;Descrição;Valor;Categoria\n")
    for (e in entries) {
        val v = String.format(java.util.Locale("pt", "BR"), "%.2f", e.amount)
        sb.append(e.ts.take(10)).append(';').append(HIST_TYPE_LABELS[e.type] ?: e.type).append(';')
            .append('"').append(e.label.replace("\"", "'")).append('"').append(';').append(v).append(';')
            .append(e.category ?: "").append('\n')
    }
    return sb.toString()
}

/* ===================== ponto: resumo mensal ===================== */

class PontoMonth(val days: Int, val hours: Double, val extraHours: Double)

fun pontoMonthSummary(s: AppState, ym: String): PontoMonth {
    val es = s.timeClock.filter { it.date.startsWith(ym) && it.hours > 0 }
    val d = isoToDate("$ym-01")
    return PontoMonth(es.size, round2(es.sumOf { it.hours }), round2(pontoExtraHoursForMonth(s, d.year, d.monthIdx())))
}

fun pontoShareText(s: AppState, ym: String): String {
    val sb = StringBuilder("Espelho de ponto — ${formatMonthLabel(ym)}\n")
    s.timeClock.filter { it.date.startsWith(ym) }.sortedBy { it.date }.forEach {
        sb.append("${it.date.substring(8)}/${it.date.substring(5, 7)}  ${it.in1.ifBlank { "--" }} ${it.out1.ifBlank { "--" }} ${it.in2.ifBlank { "--" }} ${it.out2.ifBlank { "--" }}  = ${it.hours.toString().replace('.', ',')} h\n")
    }
    val m = pontoMonthSummary(s, ym)
    sb.append("Total: ${m.hours.toString().replace('.', ',')} h em ${m.days} dia(s) · extras ${m.extraHours.toString().replace('.', ',')} h")
    return sb.toString()
}

fun shoppingShareText(s: AppState): String {
    val sb = StringBuilder("🛒 Lista de compras\n")
    s.shoppingList.forEach {
        val q = if (it.weightKg != null) "${it.weightKg.toString().replace('.', ',')} kg" else "${it.quantity}x"
        sb.append(if (it.checked) "✅ " else "⬜ ").append("$q ${it.name}\n")
    }
    sb.append("Total estimado: ${formatBRL(shoppingTotal(s))}")
    return sb.toString()
}

/* ===================== primeiros passos ===================== */

class Step(val label: String, val done: Boolean, val tab: String)

fun firstSteps(s: AppState): List<Step> = listOf(
    Step("Cadastrar seus gastos fixos", s.expenses.any { it.amount > 0 && it.id != s.shoppingLinkedExpenseId }, "gastos"),
    Step("Guardar algo em uma caixinha", s.reserves.any { it.balance > 0 }, "conta"),
    Step("Definir um teto de orçamento", s.budgets.any { it.value > 0 }, "plano"),
    Step("Registrar um gasto livre", s.history.any { it.type == "gastolivre" }, "gastos"),
    Step("Fazer um backup", s.lastBackupAt != null, "config"),
)

/* ===================== conteúdo educativo ===================== */

val GLOSSARY = listOf(
    "CDI" to "Taxa de referência dos investimentos de renda fixa. Suas caixinhas rendem em cima dela.",
    "Rotativo do cartão" to "Quando você paga menos que o total da fatura, o resto entra no rotativo, com juros muito altos. Evite.",
    "Reserva de emergência" to "Dinheiro separado para imprevistos (perda de renda, saúde, conserto). Meta comum: 3 a 6 meses dos seus gastos essenciais.",
    "Regra 50-30-20" to "Metade da renda para necessidades, 30% para desejos e 20% para o futuro (reserva, dívidas, investimentos).",
    "Bola de neve" to "Quitar primeiro a menor dívida para ganhar motivação, mantendo o mínimo nas outras.",
    "Avalanche" to "Quitar primeiro a dívida de maior juro. Costuma gastar menos juros no total.",
    "Dividendos" to "Parte do lucro que empresas e fundos imobiliários pagam aos donos das cotas, geralmente todo mês nos FIIs.",
    "FII" to "Fundo de Investimento Imobiliário: você compra cotas e recebe aluguéis ou juros de recebíveis.",
    "DARF" to "Guia para pagar imposto sobre lucro em vendas de ações/FIIs. Vence no último dia útil do mês seguinte à venda.",
    "INSS e IRRF" to "Descontos do salário CLT: previdência e imposto de renda retido na fonte.",
    "FGTS" to "Depósito mensal do empregador (8% do salário) numa conta sua; sacável em situações previstas em lei.",
    "13º salário" to "Pago em duas parcelas: a 1ª até 30/11 (sem descontos) e a 2ª até 20/12 (com INSS e IR).",
    "Juros compostos" to "Juros sobre juros. Por isso começar cedo, mesmo com pouco, faz tanta diferença.",
    "Independência financeira" to "Quando a renda dos seus investimentos paga as suas contas sem você precisar trabalhar.",
)

val FAQ = listOf(
    "Por onde começo?" to "Cadastre seus gastos fixos, guarde um valor pequeno todo mês na reserva e defina um teto de orçamento. Os \"Primeiros passos\" abaixo mostram o que falta.",
    "Meus dados estão seguros?" to "Tudo fica só no seu celular. Nada é enviado para servidores. Faça backups (Configurações → Dados) para não perder se o aparelho quebrar.",
    "Como importo os dados do app antigo (HTML)?" to "Exporte o backup .json no app antigo e importe em Configurações → Dados → Importar backup.",
    "O dinheiro aqui é de verdade?" to "É um controle pessoal e simulador: ele não move dinheiro real. Use o extrato importado ou lance seus gastos para espelhar a sua vida.",
    "As cotações são em tempo real?" to "São atualizadas a cada poucos minutos com o app aberto e uma vez por dia em segundo plano, podendo ter atraso.",
    "O app dá recomendação de investimento?" to "Não. Ele mostra informações e simulações. Decisões de investimento são suas; em caso de dúvida, procure um profissional certificado.",
)
