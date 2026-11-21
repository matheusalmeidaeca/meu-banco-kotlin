package com.meubanco.patrimonio.domain

/* ============================================================
   Carteira: compras, vendas, sobras, motor mensal e IR
   ============================================================ */

fun brokerageFee(s: AppState) = Math.max(0.0, s.settings.brokerageFee)

fun assetCostBasis(a: Asset): Double = a.costBasis ?: round2(a.quantity * a.price)
fun avgPrice(a: Asset): Double = if (a.quantity > 0) assetCostBasis(a) / a.quantity else 0.0

class Risk(val label: String, val cls: String)
fun riskOf(a: Asset): Risk {
    val v = a.volatility
    return when {
        v <= 0.012 -> Risk("Baixo", "low")
        v <= 0.02 -> Risk("Moderado", "mid")
        else -> Risk("Alto", "high")
    }
}

fun buyShares(s: AppState, a: Asset, qty: Int): Double {
    val fee = brokerageFee(s)
    val cost = round2(qty * a.price + fee)
    a.costBasis = round2(assetCostBasis(a) + cost)
    a.quantity += qty
    s.leftover = round2(s.leftover - cost)
    s.totalInvestedCost += cost
    addHistory(s, "compra", "Compra de $qty cota(s) de ${a.ticker}" + (if (fee > 0) " (corretagem de ${formatBRL(fee)} inclusa)" else ""), -cost)
    return cost
}

/** Usa as sobras: escolhe o ativo que deixa o saldo restante o mais próximo possível de zero. */
fun sweepLeftover(s: AppState, budget: Double): Double {
    val fee = brokerageFee(s)
    var spent = 0.0
    for (guard in 0 until 60) {
        val remaining = round2(budget - spent)
        var bestA: Asset? = null
        var bestQty = 0
        var bestCost = 0.0
        s.assets.forEach { a ->
            if (!(a.price > 0)) return@forEach
            val qty = Math.floor((remaining - fee) / a.price).toInt()
            if (qty < 1) return@forEach
            val cost = qty * a.price + fee
            if (bestA == null || cost > bestCost) { bestA = a; bestQty = qty; bestCost = cost }
        }
        val ba = bestA ?: break
        spent = round2(spent + buyShares(s, ba, bestQty))
    }
    return spent
}

/** Compra automática: respeita os % de alocação e depois aproveita as sobras. */
fun autoBuy(s: AppState, budget: Double): Double {
    val fee = brokerageFee(s)
    var spent = 0.0
    val allocAssets = s.assets.filter { (it.allocPct ?: 0.0) > 0 && it.price > 0 }
    if (allocAssets.isNotEmpty()) {
        val totalPct = allocAssets.sumOf { it.allocPct ?: 0.0 }
        allocAssets.forEach { a ->
            val share = budget * ((a.allocPct ?: 0.0) / totalPct)
            val qty = Math.floor((share - fee) / a.price).toInt()
            if (qty >= 1) {
                val cost = qty * a.price + fee
                if (cost <= budget - spent + 0.001) spent = round2(spent + buyShares(s, a, qty))
            }
        }
    }
    spent = round2(spent + sweepLeftover(s, round2(budget - spent)))
    return spent
}

fun processCarteiraMonthlyEvents(s: AppState) {
    var dividends = 0.0
    val perAsset = linkedMapOf<String, Double>()
    s.assets.forEach { a ->
        val v = a.quantity * a.price * (a.yieldPct / 100)
        dividends += v
        if (v > 0) perAsset[a.ticker] = round2(v)
    }
    dividends = round2(dividends)
    if (perAsset.isNotEmpty()) s.dividendBreakdown[ymKey(s.simDateISO)] = perAsset
    s.totalDividendsReceived += dividends
    if (dividends > 0) {
        s.leftover = round2(s.leftover + dividends)
        addHistory(s, "dividendo", "Dividendos recebidos", dividends)
    }

    val withdrawn = round2(Math.min(s.monthlyWithdrawal, Math.max(0.0, s.leftover)))
    if (withdrawn > 0) {
        s.leftover = round2(s.leftover - withdrawn)
        s.cash = round2(s.cash + withdrawn)
        s.totalWithdrawn += withdrawn
        addHistory(s, "saque", "Retirada da conta de investimentos para a conta principal", withdrawn)
        if (withdrawn < s.monthlyWithdrawal - 0.01) {
            addHistory(s, "aviso", "Retirada parcial: a conta de investimentos não tinha saldo pro valor pedido", 0.0)
        }
    }

    var contributed = 0.0
    if (s.recurringInvestEnabled && s.monthlyContribution > 0) {
        contributed = round2(Math.min(s.monthlyContribution, Math.max(0.0, s.cash)))
        s.cash = round2(s.cash - contributed)
        s.leftover = round2(s.leftover + contributed)
        s.totalContributed += contributed
        if (contributed > 0) addHistory(s, "aporte", "Aporte recorrente na conta de investimentos", -contributed)
        if (contributed < s.monthlyContribution - 0.01) {
            addHistory(s, "aviso", "Saldo insuficiente: aporte recorrente reduzido de ${formatBRL(s.monthlyContribution)} para ${formatBRL(contributed)}", 0.0)
        }
    }

    val buyingFlagged = (contributed > 0) || (s.reinvestDividends && dividends > 0)
    if (buyingFlagged) {
        var budget = s.investSobra + contributed + (if (s.reinvestDividends) dividends else 0.0)
        budget = round2(Math.min(budget, Math.max(0.0, s.leftover)))
        val spent = autoBuy(s, budget)
        s.investSobra = round2(Math.max(0.0, budget - spent))
    }
    s.investSobra = round2(Math.min(s.investSobra, Math.max(0.0, s.leftover)))
}

fun transferInvestAccounts(s: AppState, toInvest: Boolean, amountIn: Double): Res {
    val amount = round2(amountIn)
    if (!(amount > 0)) return Res.err("Digite um valor.")
    if (toInvest) {
        if (amount > s.cash + 0.001) return Res.err("Saldo insuficiente na conta principal.")
        s.cash = round2(s.cash - amount)
        s.leftover = round2(s.leftover + amount)
        addHistory(s, "transferencia", "Transferência para a conta de investimentos", -amount)
    } else {
        if (amount > s.leftover + 0.001) return Res.err("Saldo insuficiente na conta de investimentos.")
        s.leftover = round2(s.leftover - amount)
        s.cash = round2(s.cash + amount)
        s.investSobra = Math.min(s.investSobra, Math.max(0.0, s.leftover))
        addHistory(s, "transferencia", "Transferência da conta de investimentos para a principal", amount)
    }
    return Res.ok("Transferência feita", formatBRL(amount))
}

/* ---------- IR sobre ganho de capital (swing trade) ---------- */
const val TAX_ACAO_RATE = 0.15
const val TAX_ACAO_EXEMPT_LIMIT = 20000.0
const val TAX_FII_RATE = 0.20

fun assetTaxClass(a: Asset) = if (a.category == "acao") "acao" else "fii"

class TaxMonth(
    val ym: String, val salesAcao: Double, val gainAcao: Double, val salesFii: Double, val gainFii: Double,
    val exempt: Boolean, val taxAcao: Double, val taxFii: Double, val tax: Double,
    val carryAcao: Double, val carryFii: Double, val dueISO: String, val paid: Boolean,
)

fun taxReport(s: AppState, extra: TaxEntry? = null): List<TaxMonth> {
    val ledger = s.taxLedger + (if (extra != null) listOf(extra) else emptyList())
    val months = ledger.map { it.ym }.toSortedSet().toList()
    var carryAcao = 0.0
    var carryFii = 0.0
    return months.map { ym ->
        val es = ledger.filter { it.ym == ym }
        fun sum(cls: String, f: (TaxEntry) -> Double) = round2(es.filter { it.cls == cls }.sumOf(f))
        val salesAcao = sum("acao") { it.gross }
        val gainAcao = sum("acao") { it.gain }
        val gainFii = sum("fii") { it.gain }
        val salesFii = sum("fii") { it.gross }
        var taxA = 0.0
        var exempt = false
        if (salesAcao > 0) {
            if (salesAcao <= TAX_ACAO_EXEMPT_LIMIT) {
                exempt = true
                if (gainAcao < 0) carryAcao = round2(carryAcao - gainAcao)
            } else {
                val net = round2(gainAcao - carryAcao)
                if (net > 0) { taxA = round2(net * TAX_ACAO_RATE); carryAcao = 0.0 } else { carryAcao = round2(-net) }
            }
        }
        var taxF = 0.0
        if (es.any { it.cls == "fii" }) {
            val net = round2(gainFii - carryFii)
            if (net > 0) { taxF = round2(net * TAX_FII_RATE); carryFii = 0.0 } else { carryFii = round2(-net) }
        }
        TaxMonth(ym, salesAcao, gainAcao, salesFii, gainFii, exempt, taxA, taxF, round2(taxA + taxF), carryAcao, carryFii, taxDueISO(ym), s.taxPaid[ym] == true)
    }
}

fun pendingDarfs(s: AppState) = taxReport(s).filter { it.tax > 0.005 && !it.paid }

class SaleEstimate(val cls: String, val gain: Double, val delta: Double, val after: TaxMonth, val dueISO: String)

fun estimateSaleTax(s: AppState, assetId: String, qty: Int): SaleEstimate? {
    val a = s.assets.find { it.id == assetId }
    if (a == null || qty < 1 || a.quantity <= 0) return null
    val fee = brokerageFee(s)
    val gross = round2(qty * a.price)
    val proceeds = round2(Math.max(0.0, gross - fee))
    val gain = round2(proceeds - assetCostBasis(a) * qty / a.quantity)
    val ym = ymKey(s.simDateISO)
    val before = taxReport(s).find { it.ym == ym }
    val after = taxReport(s, TaxEntry(ym, a.ticker, assetTaxClass(a), gross, gain)).first { it.ym == ym }
    return SaleEstimate(assetTaxClass(a), gain, round2(after.tax - (before?.tax ?: 0.0)), after, after.dueISO)
}

fun payDarf(s: AppState, ym: String): Res {
    val r = taxReport(s).find { it.ym == ym }
    if (r == null || r.tax <= 0.005 || r.paid) return Res.err("")
    if (s.cash + 0.001 < r.tax) return Res.err("Saldo insuficiente na conta principal pra pagar o DARF de ${formatBRL(r.tax)}.")
    s.cash = round2(s.cash - r.tax)
    s.taxPaid[ym] = true
    addHistory(s, "imposto", "DARF 6015 — IR sobre ganho de capital de ${formatMonthLabel(ym)}", -r.tax)
    return Res.ok("DARF pago", formatBRL(r.tax))
}

class SaleResult(val proceeds: Double, val gain: Double)

fun sellShares(s: AppState, assetId: String, qtyIn: Int): Pair<Res, SaleResult?> {
    val a = s.assets.find { it.id == assetId }
    val qty = qtyIn
    if (a == null || qty < 1) return Res.err("") to null
    if (qty > a.quantity) return Res.err("Você só tem ${a.quantity} cota(s) de ${a.ticker}.") to null
    val fee = brokerageFee(s)
    val gross = round2(qty * a.price)
    val proceeds = round2(Math.max(0.0, gross - fee))
    val costPart = round2(assetCostBasis(a) * qty / a.quantity)
    val gain = round2(proceeds - costPart)
    a.costBasis = round2(Math.max(0.0, assetCostBasis(a) - costPart))
    a.quantity -= qty
    if (a.quantity == 0) { a.costBasis = 0.0; a.allocPct = 0.0 }
    s.leftover = round2(s.leftover + proceeds)
    s.totalInvestedCost = round2(Math.max(0.0, s.totalInvestedCost - costPart))
    s.realizedGain = round2(s.realizedGain + gain)
    s.taxLedger.add(TaxEntry(ymKey(s.simDateISO), a.ticker, assetTaxClass(a), gross, gain))
    s.investSobra = Math.min(s.investSobra, Math.max(0.0, s.leftover))
    addHistory(s, "venda", "Venda de $qty cota(s) de ${a.ticker} (${if (gain >= 0) "lucro " else "prejuízo "}${formatBRL(Math.abs(gain))})", proceeds)
    return Res.ok("Venda realizada", "$qty cota(s) de ${a.ticker}") to SaleResult(proceeds, gain)
}

fun manualInvest(s: AppState, assetId: String, qtyIn: Int): Res {
    val a = s.assets.find { it.id == assetId }
    val qty = qtyIn
    if (a == null || qty < 1) return Res.err("")
    val cost = round2(qty * a.price + brokerageFee(s))
    if (cost > s.leftover + 0.001) return Res.err("Saldo insuficiente na conta de investimentos. Transfira mais da conta principal.")
    buyShares(s, a, qty)
    s.investSobra = Math.min(s.investSobra, Math.max(0.0, s.leftover))
    return Res.ok("Compra realizada", "$qty cota(s) de ${a.ticker}")
}

fun investMaxQty(s: AppState, a: Asset?, sell: Boolean): Int {
    if (a == null) return 0
    if (sell) return a.quantity
    return Math.max(0, Math.floor((s.leftover - brokerageFee(s)) / a.price).toInt())
}

/* ---------- Alocação e perfil da carteira ---------- */
class Slice(val label: String, val value: Double, val color: Long)

fun allocationSlices(s: AppState): List<Slice> {
    val sums = mutableMapOf("acao" to 0.0, "tijolo" to 0.0, "papel" to 0.0)
    s.assets.forEach { a -> if (a.quantity > 0) sums[a.category] = (sums[a.category] ?: 0.0) + a.quantity * a.price }
    val fixed = s.reserves.sumOf { it.balance }
    return listOf(
        Slice("Ações", sums["acao"] ?: 0.0, 0xFF4F7CFF),
        Slice("FIIs de tijolo", sums["tijolo"] ?: 0.0, 0xFFD4A24C),
        Slice("FIIs de papel", sums["papel"] ?: 0.0, 0xFF8B6CFF),
        Slice("Renda fixa (caixinhas)", fixed, 0xFF34C98F),
        Slice("Caixa de investimento", s.leftover, 0xFF8996AB),
    ).filter { it.value > 0.005 }
}

class PortfolioProfile(val label: String, val cls: String)

fun portfolioRiskProfile(s: AppState): PortfolioProfile? {
    val w = mapOf("acao" to 1.0, "tijolo" to 0.5, "papel" to 0.35)
    var total = 0.0
    var score = 0.0
    s.assets.forEach { a ->
        if (a.quantity > 0) {
            val v = a.quantity * a.price
            total += v
            score += v * (w[a.category] ?: 1.0)
        }
    }
    val fixed = s.reserves.sumOf { it.balance }
    total += fixed
    score += fixed * 0.1
    if (total <= 0) return null
    val sc = score / total
    return when {
        sc < 0.3 -> PortfolioProfile("Conservador", "low")
        sc < 0.6 -> PortfolioProfile("Moderado", "mid")
        else -> PortfolioProfile("Arrojado", "high")
    }
}

/* ---------- Perfil de investidor (suitability) ---------- */
class QuizQ(val q: String, val o: List<String>, val key: String? = null)

val QUIZ = listOf(
    QuizQ("Qual é o seu principal objetivo ao investir?", listOf("Preservar o que já tenho", "Ter uma renda extra todo mês", "Fazer o patrimônio crescer com o tempo", "Multiplicar o patrimônio, aceitando riscos")),
    QuizQ("Por quanto tempo pretende deixar o dinheiro investido?", listOf("Menos de 1 ano", "De 1 a 3 anos", "De 3 a 5 anos", "Mais de 5 anos"), "horizon"),
    QuizQ("Qual é a sua experiência com investimentos?", listOf("Nunca investi", "Poupança e CDB", "Já investi em fundos ou ações", "Invisto regularmente em renda variável")),
    QuizQ("Se o seu patrimônio caísse 15% em um mês, o que faria?", listOf("Venderia tudo", "Venderia uma parte", "Manteria", "Compraria mais")),
    QuizQ("Quanto você tem de reserva de emergência?", listOf("Nenhuma", "Até 3 meses de gastos", "De 3 a 6 meses", "Mais de 6 meses"), "reserve"),
    QuizQ("Quanto você entende de renda fixa e renda variável?", listOf("Quase nada", "Mais ou menos", "Entendo bem a diferença", "Acompanho o mercado com frequência")),
    QuizQ("Você vai precisar desse dinheiro em breve?", listOf("Sim, nos próximos meses", "Talvez", "Improvável", "Não, é dinheiro de longo prazo"), "need"),
    QuizQ("Qual cenário você aceitaria?", listOf("Ganhar 5%, sem perder nada", "Ganhar até 10%, podendo perder 5%", "Ganhar até 20%, podendo perder 12%", "Ganhar até 40%, podendo perder 25%")),
)

class ProfileInfo(val rank: Int, val cls: String, val text: String, val allows: String)

val PROFILE_INFO = mapOf(
    "Conservador" to ProfileInfo(1, "low", "Você prioriza segurança e previsibilidade. Combina com renda fixa e ativos de risco baixo.", "Baixo"),
    "Moderado" to ProfileInfo(2, "mid", "Você aceita oscilações moderadas em troca de mais retorno. Mistura renda fixa com FIIs e algumas ações.", "Baixo e Moderado"),
    "Arrojado" to ProfileInfo(3, "high", "Você tolera perdas temporárias em busca de crescimento. Pode ter bastante renda variável.", "todos os níveis"),
)

class QuizResult(val score: Int, val label: String, val cap: String)

fun scoreInvestorQuiz(answers: List<Int>): QuizResult {
    val score = answers.sumOf { it + 1 }
    var label = if (score <= 14) "Conservador" else if (score <= 22) "Moderado" else "Arrojado"
    var cap = ""
    fun v(key: String): Int {
        val i = QUIZ.indexOfFirst { it.key == key }
        return if (i >= 0 && i < answers.size) answers[i] + 1 else 4
    }
    if (v("horizon") == 1 || v("need") == 1) {
        if (label != "Conservador") cap = "Como você precisa do dinheiro em breve, seu perfil foi limitado a Conservador."
        label = "Conservador"
    } else if (v("reserve") == 1 && label == "Arrojado") {
        label = "Moderado"
        cap = "Sem reserva de emergência, seu perfil foi limitado a Moderado."
    }
    return QuizResult(score, label, cap)
}

fun profileValidUntil(p: InvestorProfile?): String? {
    if (p == null || p.doneAt.isEmpty()) return null
    return isoToDate(p.doneAt).plusYears(2).toString()
}

fun profileExpired(s: AppState): Boolean {
    val u = profileValidUntil(s.investorProfile)
    return u != null && u < s.simDateISO
}

fun riskRank(a: Asset) = when (riskOf(a).label) { "Baixo" -> 1; "Moderado" -> 2; else -> 3 }

fun suitabilityWarning(s: AppState, a: Asset): String {
    val p = s.investorProfile ?: return "Você ainda não fez o teste de perfil de investidor (Investimentos → Perfil de investidor)."
    val info = PROFILE_INFO[p.label]
    if (info != null && riskRank(a) > info.rank)
        return "⚠️ ${a.ticker} é de risco ${riskOf(a).label}, acima do seu perfil ${p.label} (que combina com risco ${info.allows.lowercase()})."
    return ""
}

fun saveInvestorProfile(s: AppState, answers: List<Int>): QuizResult {
    val r = scoreInvestorQuiz(answers)
    s.investorProfile = InvestorProfile(answers.toList(), r.score, r.label, r.cap, s.simDateISO)
    return r
}
