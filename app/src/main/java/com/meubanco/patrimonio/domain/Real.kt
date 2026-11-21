package com.meubanco.patrimonio.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/* ============================================================
   v56 — contas reais via Pluggy (Meu Pluggy). Somente leitura.
   Os dados reais ficam separados da simulação do app.
   ============================================================ */

@Serializable
class RealAccount(
    var id: String = "", var itemId: String = "", var name: String = "",
    var kind: String = "checking", // checking | savings | credit
    var balance: Double = 0.0, var creditLimit: Double? = null, var available: Double? = null, var dueDate: String? = null,
)

@Serializable
class RealTx(
    var id: String = "", var accountId: String = "", var date: String = "", var desc: String = "",
    var amount: Double = 0.0, // com sinal: positivo entrou, negativo saiu
    var category: String = "Outros", var onCard: Boolean = false,
)

@Serializable
class RealLink(var itemId: String = "", var name: String = "", var status: String = "", var lastUpdatedAt: String? = null, var consentExpiresAt: String? = null)

@Serializable
class RealInvest(
    var id: String = "", var itemId: String = "", var name: String = "", var type: String = "OTHER", var subtype: String? = null,
    var balance: Double = 0.0, var invested: Double? = null, var profit: Double? = null, var annualRate: Double? = null,
    var dueDate: String? = null, var code: String? = null, var quantity: Double? = null,
)

fun investTypeLabel(t: String) = when (t) {
    "FIXED_INCOME" -> "Renda fixa"; "EQUITY" -> "Ações"; "ETF" -> "ETFs"; "MUTUAL_FUND" -> "Fundos"
    "COE" -> "COE"; "SECURITY" -> "Previdência/outros"; else -> "Outros"
}

fun parseRealInvest(itemId: String, arr: JsonArray): List<RealInvest> = arr.mapNotNull { e ->
    val o = e as? JsonObject ?: return@mapNotNull null
    val id = o.str("id") ?: return@mapNotNull null
    if (o.str("status") == "TOTAL_WITHDRAWAL") return@mapNotNull null
    RealInvest(id, itemId, (o.str("name") ?: o.str("code") ?: "Investimento").trim().take(70), o.str("type") ?: "OTHER", o.str("subtype"),
        round2(o.num("balance") ?: 0.0), o.num("amountOriginal") ?: o.num("amount"), o.num("amountProfit"),
        o.num("annualRate") ?: o.num("lastTwelveMonthsRate"), o.str("dueDate")?.take(10), o.str("code"), o.num("quantity"))
}

fun realInvestTotal(s: AppState) = round2(s.realInvest.sumOf { it.balance })
fun realInvestByType(s: AppState) = s.realInvest.groupBy { it.type }.map { (k, v) -> k to round2(v.sumOf { it.balance }) }.sortedByDescending { it.second }

private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

fun parseRealAccounts(itemId: String, arr: JsonArray): List<RealAccount> = arr.mapNotNull { e ->
    val o = e as? JsonObject ?: return@mapNotNull null
    val id = o.str("id") ?: return@mapNotNull null
    val kind = when {
        o.str("subtype") == "CREDIT_CARD" || o.str("type") == "CREDIT" -> "credit"
        o.str("subtype") == "SAVINGS_ACCOUNT" -> "savings"
        else -> "checking"
    }
    val cd = o["creditData"] as? JsonObject
    RealAccount(id, itemId, o.str("marketingName") ?: o.str("name") ?: "Conta", kind, round2(o.num("balance") ?: 0.0),
        cd?.num("creditLimit"), cd?.num("availableCreditLimit"), cd?.str("balanceDueDate")?.take(10))
}

private val PLUGGY_CATS = listOf(
    "grocer" to "Alimentação", "eating" to "Alimentação", "restaurant" to "Alimentação", "food" to "Alimentação",
    "transport" to "Transporte", "taxi" to "Transporte", "gas station" to "Transporte", "fuel" to "Transporte",
    "health" to "Saúde", "pharma" to "Saúde", "entertain" to "Lazer", "leisure" to "Lazer", "streaming" to "Lazer",
    "educat" to "Educação", "rent" to "Moradia", "housing" to "Moradia", "utilit" to "Contas", "telecom" to "Contas", "bills" to "Contas",
    "alimenta" to "Alimentação", "saúde" to "Saúde", "lazer" to "Lazer", "moradia" to "Moradia", "educa" to "Educação",
)

fun categorizeReal(desc: String, pluggyCategory: String?): String {
    val g = guessCategory(desc)
    if (g != "Outros") return g
    val c = pluggyCategory?.lowercase().orEmpty()
    return PLUGGY_CATS.firstOrNull { c.contains(it.first) }?.second ?: "Outros"
}

fun parseRealTx(accountId: String, onCard: Boolean, arr: JsonArray): List<RealTx> = arr.mapNotNull { e ->
    val o = e as? JsonObject ?: return@mapNotNull null
    if (o.str("status") == "PENDING") return@mapNotNull null
    val id = o.str("id") ?: return@mapNotNull null
    val date = o.str("date")?.take(10)?.takeIf { it.length == 10 } ?: return@mapNotNull null
    val amount = o.num("amount") ?: return@mapNotNull null
    val desc = (o.str("description") ?: o.str("descriptionRaw") ?: "Lançamento").trim().take(80)
    val signed = if (o.str("type") == "CREDIT") Math.abs(amount) else -Math.abs(amount)
    RealTx(id, accountId, date, desc, round2(signed), categorizeReal(desc, o.str("category")), onCard)
}

private val INTERNAL = Regex("""fatura|pagamento (de )?(cart|fat)|pgto\.? ?(de )?fat|pagto\.? ?(de )?(cart|fat)|pagamento recebido|saldo anterior|entre contas""", RegexOption.IGNORE_CASE)

/** Pagamento de fatura e movimentos entre contas próprias: não são gasto nem renda (a compra já aparece no cartão). */
fun isInternalMovement(desc: String) = INTERNAL.containsMatchIn(desc)

class RealSummary(val income: Double, val spent: Double, val byCategory: List<Pair<String, Double>>)

fun realMonthSummary(s: AppState, ym: String): RealSummary {
    val tx = s.realTx.filter { it.date.startsWith(ym) && !isInternalMovement(it.desc) }
    val cats = linkedMapOf<String, Double>()
    tx.filter { it.amount < 0 }.forEach { cats[it.category] = (cats[it.category] ?: 0.0) - it.amount }
    return RealSummary(round2(tx.filter { it.amount > 0 }.sumOf { it.amount }), round2(tx.filter { it.amount < 0 }.sumOf { -it.amount }),
        cats.toList().sortedByDescending { it.second }.map { it.first to round2(it.second) })
}

class RealBalances(val cash: Double, val cardOwed: Double, val cardLimit: Double)

fun realBalances(s: AppState): RealBalances {
    val cash = s.realAccounts.filter { it.kind != "credit" }.sumOf { it.balance }
    val cards = s.realAccounts.filter { it.kind == "credit" }
    return RealBalances(round2(cash), round2(cards.sumOf { it.balance }), round2(cards.sumOf { it.creditLimit ?: 0.0 }))
}

/**
 * Leva os lançamentos reais para o histórico do app (orçamentos, 50-30-20, saúde financeira),
 * SEM mexer no saldo simulado. Cada lançamento entra uma única vez.
 */
fun importRealToHistory(s: AppState): Int {
    val known = s.importedKeys.toHashSet(); var added = 0
    for (t in s.realTx.sortedBy { it.date }) {
        val k = "pluggy|" + t.id
        if (t.amount == 0.0 || isInternalMovement(t.desc) || !known.add(k)) continue
        val e = if (t.amount > 0) HistoryEntry(t.date, "renda", "Banco: ${t.desc}", t.amount)
        else HistoryEntry(t.date, "gastolivre", "Banco: ${t.desc}", t.amount, category = t.category)
        s.history.add(0, e)
        recordStat(e, s.monthlyStats)
        s.importedKeys.add(k); added++
    }
    while (s.history.size > 500) s.history.removeAt(s.history.size - 1)
    while (s.importedKeys.size > 6000) s.importedKeys.removeAt(0)
    return added
}
