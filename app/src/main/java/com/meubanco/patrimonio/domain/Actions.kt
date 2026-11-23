package com.meubanco.patrimonio.domain

/* ============================================================
   Ações do usuário (Pix, cartão, caixinhas, gastos, compras…)
   ============================================================ */

/* ---------- Pix ---------- */
fun checkSendPix(s: AppState, name: String, amount: Double): Res {
    if (name.isBlank() || amount <= 0) return Res.err("")
    if (amount > s.cash + 0.001) return Res.err("Saldo insuficiente na conta corrente para esse Pix.")
    val limit = s.settings.pixDailyLimit
    if (limit > 0) {
        val todayISO = AppClock.todayISO()
        val sentToday = if (s.pixSentTodayISO == todayISO) s.pixSentTodayTotal else 0.0
        if (sentToday + amount > limit + 0.001)
            return Res.err("Isso passaria do limite diário de Pix (${formatBRL(limit)}). Já enviado hoje: ${formatBRL(sentToday)}.")
    }
    val threshold = s.settings.pixConfirmThreshold
    if (threshold > 0 && amount >= threshold) return Res.ask("Esse Pix é de valor alto — confirma o envio de ${formatBRL(amount)} para $name?")
    return Res.ok()
}

fun executeSendPix(s: AppState, name: String, amount: Double): Res {
    s.cash = round2(s.cash - amount)
    val todayISO = AppClock.todayISO()
    if (s.pixSentTodayISO != todayISO) { s.pixSentTodayISO = todayISO; s.pixSentTodayTotal = 0.0 }
    s.pixSentTodayTotal = round2(s.pixSentTodayTotal + amount)
    addHistory(s, "pix", "Pix enviado para $name", -amount)
    autoRoundUp(s, amount)
    return Res.ok("Pix enviado", "${formatBRL(amount)} para $name")
}

fun receivePix(s: AppState, name: String, amount: Double): Res {
    if (name.isBlank() || amount <= 0) return Res.err("")
    s.cash = round2(s.cash + amount)
    addHistory(s, "pix", "Pix recebido de $name", amount)
    autoSaveOnIncome(s, amount)
    return Res.ok("Pix recebido", "${formatBRL(amount)} de $name")
}

/* ---------- Cartão ---------- */
fun payInvoiceNow(s: AppState, c: Card, allowPartial: Boolean): Res {
    if (c.pendingAmount <= 0) return Res.err("Não há fatura fechada pendente pra pagar agora.")
    if (s.cash <= 0) return Res.err("Seu saldo em conta está zerado.")
    val actual = round2(Math.min(c.pendingAmount, s.cash))
    if (actual < c.pendingAmount - 0.01 && !allowPartial) {
        return Res.ask("Seu saldo (${formatBRL(s.cash)}) é menor que a fatura (${formatBRL(c.pendingAmount)}). Pagar ${formatBRL(actual)} agora, o que der?")
    }
    s.cash = round2(s.cash - actual)
    c.pendingAmount = round2(c.pendingAmount - actual)
    if (c.pendingAmount <= 0.009) { c.pendingAmount = 0.0; c.pendingDueYM = null }
    addHistory(s, "fatura", "Fatura do cartão paga antecipadamente", -actual)
    return Res.ok("Fatura paga", formatBRL(actual))
}

fun addCardPurchase(s: AppState, c: Card, desc: String, amount: Double, installmentsIn: Int): Res {
    val installmentsTotal = clampI(installmentsIn, 1, 24)
    if (desc.isBlank() || amount <= 0) return Res.err("")
    val available = c.limit - c.invoiceCurrent - c.pendingAmount - cardFutureInstallmentsTotal(c)
    if (amount > available + 0.001) return Res.err("Limite do cartão insuficiente pra essa compra (parcelas futuras já comprometidas contam no limite).")
    val installmentValue = round2(amount / installmentsTotal)
    c.purchases.add(Purchase(c.nextPurchaseId++, desc.trim(), amount, installmentsTotal, installmentValue, 1, s.simDateISO))
    c.invoiceCurrent = round2(c.invoiceCurrent + installmentValue)
    val label = if (installmentsTotal > 1) "Compra no cartão: $desc (1/$installmentsTotal — ${formatBRL(installmentValue)}/mês)"
    else "Compra no cartão: $desc (na fatura)"
    addHistory(s, "cartao", label, -installmentValue)
    return Res.ok("Compra lançada", if (installmentsTotal > 1) "${installmentsTotal}x de ${formatBRL(installmentValue)}" else formatBRL(amount))
}

fun removeCardPurchase(c: Card, id: Int) {
    val p = c.purchases.find { it.id == id } ?: return
    c.purchases.removeAll { it.id == id }
    c.invoiceCurrent = round2(Math.max(0.0, c.invoiceCurrent - p.installmentValue))
}

fun addExtraCard(s: AppState) {
    val n = s.extraCards.size + 2
    s.extraCards.add(Card(limit = 1000.0, name = "Cartão $n"))
    s.activeCardIdx = s.extraCards.size
}

/** false = precisa confirmar (fatura em aberto). */
fun removeActiveCard(s: AppState) {
    val idx = s.activeCardIdx
    if (idx == 0) {
        if (s.extraCards.isEmpty()) return
        val first = s.extraCards.removeAt(0)
        first.enabled = true
        s.card = first
    } else {
        s.extraCards.removeAt(idx - 1)
    }
    s.activeCardIdx = 0
}

/* ---------- Caixinhas (reservas e metas) ---------- */
fun addReserve(s: AppState, name: String, monthly: Double, target: Double) {
    if (name.isBlank()) return
    s.reserves.add(Reserve(s.nextReserveId++, name.trim(), 0.0, monthly, target, 0.0, 0.0, null, s.simDateISO))
    addHistory(s, "cdb", "Nova reserva criada: ${name.trim()}", 0.0)
}

fun removeReserve(s: AppState, id: Int) { s.reserves.removeAll { it.id == id } }
fun removeGoal(s: AppState, id: Int) { s.goals.removeAll { it.id == id } }

fun addExtraToReserve(s: AppState, id: Int, amount: Double) {
    val r = s.reserves.find { it.id == id }
    if (r == null || amount <= 0) return
    val actual = round2(Math.min(amount, Math.max(0.0, s.cash)))
    if (actual <= 0) return
    s.cash -= actual
    if (r.balance <= 0 && r.startDateISO == null) r.startDateISO = s.simDateISO
    r.balance += actual
    r.totalContributed += actual
    addHistory(s, "cdb", "Aporte extra na reserva \"${r.name}\"", -actual)
    if (actual < amount - 0.01) {
        addHistory(s, "aviso", "Caixa livre insuficiente: aporte extra na reserva \"${r.name}\" reduzido de ${formatBRL(amount)} para ${formatBRL(actual)}", 0.0)
    }
}

fun addExtraToGoal(s: AppState, id: Int, amount: Double) {
    val g = s.goals.find { it.id == id }
    if (g == null || g.completed || amount <= 0) return
    val actual = round2(Math.min(amount, Math.max(0.0, s.cash)))
    if (actual <= 0) return
    s.cash -= actual
    g.saved += actual
    addHistory(s, "meta", "Aporte extra na meta \"${g.name}\"", -actual)
    if (actual < amount - 0.01) {
        addHistory(s, "aviso", "Caixa livre insuficiente: aporte extra na meta \"${g.name}\" reduzido de ${formatBRL(amount)} para ${formatBRL(actual)}", 0.0)
    }
    if (g.saved >= g.target) {
        g.completed = true
        g.completedOn = s.simDateISO
        addHistory(s, "conquista", "Meta concluída: ${g.name}", g.target)
    }
}

/** IR regressivo de renda fixa (Lei 11.033/2004). */
fun reserveIrAliquota(daysHeld: Int) = when {
    daysHeld <= 180 -> 0.225
    daysHeld <= 360 -> 0.20
    daysHeld <= 720 -> 0.175
    else -> 0.15
}

class WithdrawEstimate(val net: Double, val ir: Double, val aliquota: Double, val yieldPortion: Double, val daysHeld: Int)

fun reserveWithdrawEstimate(s: AppState, r: Reserve, amount: Double): WithdrawEstimate {
    val start = r.startDateISO
    if (r.balance <= 0 || r.totalInterest <= 0 || start == null) return WithdrawEstimate(amount, 0.0, 0.0, 0.0, 0)
    val daysHeld = Math.max(0, daysBetweenISO(start, s.simDateISO))
    val aliquota = reserveIrAliquota(daysHeld)
    val yieldShare = clamp(r.totalInterest / r.balance, 0.0, 1.0)
    val yieldPortion = round2(amount * yieldShare)
    val ir = round2(yieldPortion * aliquota)
    return WithdrawEstimate(round2(amount - ir), ir, aliquota, yieldPortion, daysHeld)
}

fun withdrawFromReserve(s: AppState, id: Int, amount: Double) {
    val r = s.reserves.find { it.id == id }
    if (r == null || amount <= 0) return
    val actual = round2(Math.min(amount, Math.max(0.0, r.balance)))
    if (actual <= 0) return
    val est = reserveWithdrawEstimate(s, r, actual)
    r.balance -= actual
    r.totalInterest = round2(Math.max(0.0, r.totalInterest - est.yieldPortion))
    if (r.balance <= 0) r.startDateISO = null
    s.cash += est.net
    if (est.ir > 0) addHistory(s, "cdb", "Resgate da reserva \"${r.name}\" (IR de ${formatBRL(est.ir)} sobre o rendimento)", est.net)
    else addHistory(s, "cdb", "Resgate da reserva \"${r.name}\"", est.net)
    if (actual < amount - 0.01) {
        addHistory(s, "aviso", "Saldo insuficiente: resgate da reserva \"${r.name}\" reduzido de ${formatBRL(amount)} para ${formatBRL(actual)}", 0.0)
    }
}

fun dailyPctFromAnnual(annualPct: Double) = (Math.pow(1 + annualPct / 100, 1.0 / 365) - 1) * 100

fun estimateMonthsToGoal(g: Goal): Double {
    if (g.completed) return 0.0
    if (g.saved >= g.target) return 0.0
    if (g.monthly <= 0 && g.rate <= 0) return Double.POSITIVE_INFINITY
    var bal = g.saved
    val monthlyRate = Math.pow(1 + g.rate / 100, 1.0 / 12) - 1
    for (m in 1..1200) {
        bal += bal * monthlyRate + g.monthly
        if (bal >= g.target) return m.toDouble()
    }
    return Double.POSITIVE_INFINITY
}

class CaixinhaOpt(val id: String, val label: String)
fun listCaixinhaOptions(s: AppState): List<CaixinhaOpt> =
    s.reserves.map { CaixinhaOpt("reserve:${it.id}", it.name) } + s.goals.filter { !it.completed }.map { CaixinhaOpt("goal:${it.id}", it.name) }

/* ---------- Gastos fixos e gasto livre ---------- */
fun spendFree(s: AppState, amount: Double, category: String): Res {
    if (amount <= 0) return Res.err("")
    val actual = round2(Math.min(amount, Math.max(0.0, s.cash)))
    if (actual <= 0) return Res.err("")
    s.cash -= actual
    val cat = if (s.freeSpendCategories.contains(category)) category else "Outro"
    addHistory(s, "gastolivre", "Gasto livre — $cat", -actual, category = cat)
    if (actual < amount - 0.01) {
        addHistory(s, "aviso", "Caixa livre insuficiente: gasto reduzido de ${formatBRL(amount)} para ${formatBRL(actual)}", 0.0)
    }
    autoRoundUp(s, actual)
    return Res.ok("Gasto registrado", "${formatBRL(actual)} · $cat")
}

fun freeSpendThisMonth(s: AppState): Double {
    val ym = ymKey(s.simDateISO)
    return s.history.filter { it.type == "gastolivre" && ymKey(it.ts) == ym }.sumOf { Math.abs(it.amount) }
}

fun freeSpendThisMonthByCategory(s: AppState): Map<String, Double> {
    val ym = ymKey(s.simDateISO)
    val out = linkedMapOf<String, Double>()
    s.freeSpendCategories.forEach { out[it] = 0.0 }
    s.history.filter { it.type == "gastolivre" && ymKey(it.ts) == ym }.forEach { h ->
        val cat = if (h.category != null && out.containsKey(h.category)) h.category!! else "Outro"
        out[cat] = (out[cat] ?: 0.0) + Math.abs(h.amount)
    }
    return out
}

fun addExpense(s: AppState, name: String, amount: Double, category: String) {
    if (name.isBlank() || amount <= 0) return
    s.expenses.add(Expense(s.nextExpenseId++, name.trim(), amount, category.ifEmpty { "Outros" }))
}
fun removeExpense(s: AppState, id: Int) { s.expenses.removeAll { it.id == id } }

/* ---------- Lista de compras e produtos ---------- */
fun shoppingDiscountFor(s: AppState, sub: Double): Double {
    val full = s.shoppingList.sumOf { it.price * it.quantity }
    val d = s.shoppingDiscount
    val v = Math.max(0.0, d.value)
    if (v == 0.0 || sub <= 0 || full <= 0) return 0.0
    return round2(if (d.type == "valor") Math.min(sub, v * sub / full) else sub * Math.min(v, 100.0) / 100)
}

fun shoppingTotal(s: AppState): Double {
    val sub = s.shoppingList.filter { it.checked }.sumOf { it.price * it.quantity }
    return round2(sub - shoppingDiscountFor(s, sub))
}

fun shoppingListFullTotal(s: AppState): Double {
    val sub = s.shoppingList.sumOf { it.price * it.quantity }
    return round2(sub - shoppingDiscountFor(s, sub))
}

/** O gasto fixo "Alimentação" sempre espelha o total da lista de compras. */
fun syncShoppingToFoodExpense(s: AppState) {
    var exp = s.expenses.find { it.id == s.shoppingLinkedExpenseId }
    if (exp == null) {
        exp = Expense(s.nextExpenseId++, "Alimentação", 0.0, "Alimentação")
        s.expenses.add(exp)
        s.shoppingLinkedExpenseId = exp.id
    }
    exp.amount = round2(shoppingListFullTotal(s))
}

fun addShopItem(s: AppState, name: String, category: String, price: Double, qty: Int) {
    if (name.isBlank()) return
    s.shoppingList.add(ShopItem(s.nextShoppingId++, name.trim(), category.trim().ifEmpty { "Geral" }, price, Math.max(1, qty)))
    syncShoppingToFoodExpense(s)
}

fun addProductToList(s: AppState, productId: Int): Boolean {
    val p = s.products.find { it.id == productId } ?: return false
    if (p.byWeight) return false // abre a balança na interface
    val existing = s.shoppingList.find { it.productId == p.id }
    if (existing != null) existing.quantity = existing.quantity + 1
    else s.shoppingList.add(ShopItem(s.nextShoppingId++, p.name, p.category.ifEmpty { "Geral" }, p.price, 1, false, p.id, p.barcode, p.variant))
    syncShoppingToFoodExpense(s)
    return true
}

fun addWeighedProduct(s: AppState, productId: Int, kg: Double): Boolean {
    val p = s.products.find { it.id == productId } ?: return false
    if (kg <= 0) return false
    val kgR = Math.round(kg * 1000) / 1000.0
    val existing = s.shoppingList.find { it.productId == p.id && it.weightKg != null }
    if (existing != null) {
        existing.weightKg = Math.round(((existing.weightKg ?: 0.0) + kgR) * 1000) / 1000.0
        existing.pricePerKg = p.price
        existing.price = round2(p.price * (existing.weightKg ?: 0.0))
    } else {
        s.shoppingList.add(
            ShopItem(s.nextShoppingId++, p.name, p.category.ifEmpty { "Geral" }, round2(p.price * kgR), 1, false, p.id, p.barcode, p.variant, kgR, p.price),
        )
    }
    syncShoppingToFoodExpense(s)
    return true
}

fun changeShopQty(s: AppState, id: Int, delta: Int) {
    val item = s.shoppingList.find { it.id == id } ?: return
    item.quantity = Math.max(1, item.quantity + delta)
    syncShoppingToFoodExpense(s)
}
fun removeShopItem(s: AppState, id: Int) { s.shoppingList.removeAll { it.id == id }; syncShoppingToFoodExpense(s) }
fun toggleShopItem(s: AppState, id: Int) { s.shoppingList.find { it.id == id }?.let { it.checked = !it.checked } }
fun resetShoppingChecks(s: AppState) { s.shoppingList.forEach { it.checked = false } }

fun saveEditShop(s: AppState, id: Int, name: String, cat: String, qty: Int?, price: Double?, kg: Double?) {
    val item = s.shoppingList.find { it.id == id } ?: return
    if (name.isBlank()) return
    item.name = name.trim()
    item.category = cat.trim().ifEmpty { "Geral" }
    if (item.weightKg != null) {
        val k = Math.round(Math.max(0.001, kg ?: item.weightKg!!) * 1000) / 1000.0
        item.weightKg = k
        item.price = round2((item.pricePerKg ?: 0.0) * k)
    } else {
        item.quantity = Math.max(1, qty ?: item.quantity)
        item.price = Math.max(0.0, price ?: item.price)
    }
    syncShoppingToFoodExpense(s)
}

fun seedCommonProducts(s: AppState) {
    COMMON_PRODUCTS.forEach { c ->
        if (s.products.none { it.name.equals(c.name, ignoreCase = true) })
            s.products.add(Product(s.nextProductId++, c.name, c.category, c.price))
    }
}

fun saveProduct(s: AppState, editId: Int?, data: Product): Product {
    if (editId != null) {
        val p = s.products.find { it.id == editId }
        if (p != null) {
            p.name = data.name; p.category = data.category; p.price = data.price; p.barcode = data.barcode
            p.brand = data.brand; p.variant = data.variant; p.byWeight = data.byWeight
            return p
        }
    }
    data.id = s.nextProductId++
    s.products.add(data)
    return data
}

fun deleteProduct(s: AppState, id: Int) { s.products.removeAll { it.id == id } }

/* ---------- Ponto / domingos ---------- */
fun isoIsSunday(iso: String) = isoToDate(iso).jsDay() == 0

/* ---------- Resets ---------- */
fun resetPartial(s: AppState, kind: String) {
    val def = AppState()
    when (kind) {
        "historico" -> { s.history.clear(); s.monthlyStats.clear() }
        "carteira" -> {
            s.assets = def.assets
            s.selectedAssetId = s.assets.firstOrNull()?.id
            s.monthlyContribution = def.monthlyContribution
            s.monthlyWithdrawal = def.monthlyWithdrawal
            s.leftover = 0.0; s.totalContributed = 0.0; s.totalWithdrawn = 0.0
            s.totalInvestedCost = 0.0; s.totalDividendsReceived = 0.0
            s.taxLedger.clear(); s.taxPaid.clear(); s.realizedGain = 0.0
        }
        "caixinhas" -> { s.reserves.clear(); s.goals.clear(); s.nextReserveId = 1; s.nextGoalId = 1 }
        "gastos" -> { s.expenses = def.expenses; s.shoppingList.clear(); s.nextShoppingId = 1 }
    }
}

/* ---------- Categorias de gasto livre ---------- */
fun toggleFreeCatalogCategory(s: AppState, name: String) {
    if (s.freeSpendCategories.contains(name)) s.freeSpendCategories.remove(name) else s.freeSpendCategories.add(name)
}
fun addFreeSpendCategory(s: AppState, name: String) {
    val n = name.trim()
    if (n.isEmpty() || s.freeSpendCategories.contains(n)) return
    s.freeSpendCategories.add(n)
}
fun removeFreeSpendCategory(s: AppState, name: String): Res {
    if (s.freeSpendCategories.size <= 1) return Res.err("Precisa sobrar pelo menos uma categoria.")
    s.freeSpendCategories.removeAll { it == name }
    return Res.ok()
}

/* ---------- Cadastro inicial ---------- */
class ObExpense(var name: String, var amount: Double, var category: String)
class ObDebt(var name: String = "", var kind: String = "cartao", var balance: Double = 0.0, var ratePct: Double = 0.0, var minPayment: Double = 0.0)

class OnboardingDraft(
    var name: String = "",
    var birthDateISO: String = "",
    var sex: String = "",
    var incomeType: String = "empregado",
    var hireDateISO: String = "",
    var baseSalary: Double = 0.0,
    var beneficioLabel: String = "",
    var beneficioAmount: Double = 0.0,
    var hasSecondaryIncome: Boolean = false,
    var secondaryLabel: String = "",
    var secondaryType: String = "salario",
    var secondaryAmount: Double = 0.0,
    var secondaryPayDay: Int = 5,
    var hasCard: Boolean = true,
    var cardLimit: Double = 0.0,
    var wantExpenses: Boolean = false,
    var expenses: MutableList<ObExpense> = mutableListOf(),
    var cash: Double = 0.0,
    var mainGoal: String = "",
    var reserveStart: Double = 0.0,
    var hasDebts: Boolean = false,
    var debts: MutableList<ObDebt> = mutableListOf(),
)

fun newOnboardingDraft(s: AppState) = OnboardingDraft(
    name = s.settings.displayName,
    birthDateISO = s.profile.birthDateISO ?: "",
    sex = s.profile.sex ?: "",
    incomeType = s.income.incomeType,
    hireDateISO = s.income.hireDateISO ?: "",
    baseSalary = s.income.baseSalary,
    beneficioLabel = s.income.beneficioLabel,
    beneficioAmount = s.income.beneficioAmount,
    hasSecondaryIncome = s.secondaryIncome.enabled,
    secondaryLabel = s.secondaryIncome.label,
    secondaryType = s.secondaryIncome.type,
    secondaryAmount = s.secondaryIncome.amount,
    secondaryPayDay = s.secondaryIncome.payDay,
    hasCard = s.card.enabled,
    cardLimit = s.card.limit,
    expenses = s.expenses.filter { it.id != s.shoppingLinkedExpenseId }.map { ObExpense(it.name, it.amount, it.category) }.toMutableList(),
    cash = s.cash,
)

fun onboardingSteps(d: OnboardingDraft): List<String> {
    val steps = mutableListOf("welcome", "name", "goal", "birthSex", "incomeType")
    if (d.incomeType == "empregado") steps.add("hireDate")
    if (d.incomeType != "autonomo") steps.add("income")
    steps.add("hasSecondaryIncome")
    if (d.hasSecondaryIncome) steps.add("secondaryIncomeDetails")
    steps.add("hasCard")
    if (d.hasCard) steps.add("cardLimit")
    steps.add("wantExpenses")
    if (d.wantExpenses) steps.add("expenses")
    steps.add("balance"); steps.add("reserve"); steps.add("hasDebts")
    if (d.hasDebts) steps.add("debts")
    steps.add("security"); steps.add("summary"); steps.add("done")
    return steps
}

fun finishOnboarding(s: AppState, d: OnboardingDraft) {
    s.settings.displayName = d.name
    s.profile.birthDateISO = d.birthDateISO.ifEmpty { null }
    s.profile.sex = d.sex.ifEmpty { null }
    s.income.incomeType = d.incomeType
    when (d.incomeType) {
        "renda" -> {
            s.income.beneficioLabel = d.beneficioLabel.ifEmpty { "Benefício social" }
            s.income.beneficioAmount = d.beneficioAmount
            if (s.activeMainTab == "ponto") s.activeMainTab = "conta"
        }
        "autonomo" -> if (s.activeMainTab == "ponto") s.activeMainTab = "conta"
        else -> {
            if (d.baseSalary > 0) s.income.baseSalary = d.baseSalary
            s.income.hireDateISO = d.hireDateISO.ifEmpty { null }
            s.income.hireDateISO?.let { s.income.feriasMonth = isoToDate(it).monthIdx() }
            recomputeFgtsBackfill(s)
        }
    }
    s.secondaryIncome.enabled = d.hasSecondaryIncome
    if (d.hasSecondaryIncome) {
        s.secondaryIncome.label = d.secondaryLabel.ifEmpty { "Renda extra" }
        s.secondaryIncome.type = d.secondaryType.ifEmpty { "salario" }
        s.secondaryIncome.amount = d.secondaryAmount
        s.secondaryIncome.payDay = d.secondaryPayDay
    }
    s.card.enabled = d.hasCard
    if (d.hasCard) s.card.limit = d.cardLimit

    val keepNames = if (d.wantExpenses) d.expenses.map { it.name } else emptyList()
    s.expenses = s.expenses.filter { it.id == s.shoppingLinkedExpenseId || keepNames.contains(it.name) }.toMutableList()
    if (d.wantExpenses) {
        d.expenses.forEach { edited ->
            val target = s.expenses.find { it.name == edited.name }
            if (target != null) { target.amount = edited.amount; target.category = edited.category.ifEmpty { target.category } }
            else s.expenses.add(Expense(s.nextExpenseId++, edited.name, edited.amount, edited.category.ifEmpty { "Outros" }))
        }
    }
    s.cash = d.cash
    s.mainGoal = d.mainGoal
    if (d.reserveStart > 0) s.reserves.firstOrNull()?.let { it.balance = round2(it.balance + d.reserveStart); it.totalContributed = round2(it.totalContributed + d.reserveStart) }
    s.debts.clear()
    if (d.hasDebts) d.debts.filter { it.balance > 0 }.forEach {
        s.debts.add(Debt(s.nextDebtId++, it.name.ifBlank { "Dívida" }, it.kind, it.balance, it.ratePct, it.minPayment))
    }
    s.onboardingCompleted = true
}
