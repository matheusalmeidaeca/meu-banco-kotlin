package com.meubanco.patrimonio.domain

import kotlinx.serialization.json.Json

/** Configuração do JSON: tolerante com campos extras/ausentes, para aceitar backups do app em HTML. */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    isLenient = true
    prettyPrint = false
}
val AppJsonPretty = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    prettyPrint = true
}

fun decodeState(text: String): AppState {
    val st = AppJson.decodeFromString(AppState.serializer(), text)
    migrate(st)
    return st
}

fun encodeState(s: AppState, pretty: Boolean = false): String =
    (if (pretty) AppJsonPretty else AppJson).encodeToString(AppState.serializer(), s)

/** Porte do mergeLoadedState() do app em HTML: garante que dados antigos continuem válidos. */
fun migrate(m: AppState) {
    rebuildFlowsIfNeeded(m)
    m.market.autoAssets = true

    // Quem tinha uma conta salva antes do cadastro inicial existir não deve ser jogado nele.
    if (m.onboardingCompleted == null) m.onboardingCompleted = true

    // "Reserva de emergência (CDB)" separada virou só mais uma reserva comum
    val cdb = m.cdb
    if (cdb != null && (cdb.balance > 0 || cdb.monthlyContribution > 0)) {
        val targetValue = round2((cdb.monthlyExpenseRef ?: cdb.monthlyExpense ?: 0.0) * (cdb.monthsTarget ?: 0.0))
        val r = Reserve(
            id = m.nextReserveId++, name = "Reserva de emergência", balance = round2(cdb.balance),
            monthlyContribution = cdb.monthlyContribution, targetValue = targetValue,
            totalContributed = round2(cdb.balance), totalInterest = 0.0, startDateISO = null, createdOn = m.simDateISO,
        )
        m.reserves = (listOf(r) + m.reserves).toMutableList()
        cdb.annualRatePct?.let { if (m.reserveConfig.annualRatePct == 11.0) m.reserveConfig.annualRatePct = it }
    }
    m.cdb = null

    if (m.income.incomeType == "salario") m.income.incomeType = "empregado"
    if (m.income.incomeType == "beneficio") m.income.incomeType = "renda"
    if (m.income.baseMonthKey.isEmpty()) m.income.baseMonthKey = m.simDateISO.substring(0, 7)
    if (m.income.sundayAnchorISO == null) m.income.sundayAnchorISO = nearestSundayOnOrAfter(isoToDate(m.simDateISO)).toString()

    m.expenses.forEach { if (it.category.isEmpty()) it.category = "Outros" }
    m.shoppingList.forEach { if (it.quantity < 1) it.quantity = 1 }

    // o gasto fixo "Alimentação" é sempre o total da lista de compras
    if (m.expenses.none { it.id == m.shoppingLinkedExpenseId }) {
        val existing = m.expenses.find { it.name.trim().lowercase() == "alimentação" }
        if (existing != null) m.shoppingLinkedExpenseId = existing.id
        else {
            val e = Expense(m.nextExpenseId++, "Alimentação", 0.0, "Alimentação")
            m.expenses.add(e)
            m.shoppingLinkedExpenseId = e.id
        }
    }
    syncShoppingToFoodExpense(m)

    m.assets.forEach { if (it.costBasis == null) it.costBasis = round2(it.quantity * it.price) }
    if (m.products.isNotEmpty()) m.nextProductId = Math.max(m.nextProductId, m.products.maxOf { it.id } + 1)
    if (m.card.name.isEmpty()) m.card.name = "Cartão principal"

    // compras antigas (sem parcelas) viram compra de 1 parcela
    (listOf(m.card) + m.extraCards).forEach { c ->
        c.purchases.forEach { p ->
            if (p.installmentsTotal == 0) {
                val amt = p.amount ?: p.totalAmount
                p.totalAmount = amt
                p.installmentsTotal = 1
                p.installmentValue = amt
                p.installmentsPaid = 1
            }
        }
    }

    // versões antigas exigiam apertar play pra sincronizar a data
    if (m.hasEverStarted == false) {
        val t = AppClock.today()
        m.simDateISO = t.toString()
        m.income.sundayAnchorISO = nearestSundayOnOrAfter(t).toString()
        m.income.baseMonthKey = t.toString().substring(0, 7)
        m.income.feriasMonth = t.monthIdx()
    }
    m.hasEverStarted = null
    if (m.income.feriasMonth == null) m.income.feriasMonth = isoToDate(m.simDateISO).monthIdx()

    when (m.activeMainTab) {
        "reserva", "metas", "caixinhas", "inicio" -> m.activeMainTab = "conta"
        "projecao", "vida", "perfil" -> m.activeMainTab = "config"
        "renda" -> m.activeMainTab = "gastos"
    }
    if (m.activeCardIdx > m.extraCards.size) m.activeCardIdx = 0

    if (m.monthlyStats.isEmpty() && m.history.isNotEmpty()) {
        m.history.reversed().forEach { recordStat(it, m.monthlyStats) }
    }
}
