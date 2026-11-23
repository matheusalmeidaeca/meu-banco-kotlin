package com.meubanco.patrimonio.domain

import java.text.Normalizer

/* ============================================================
   Lançamento inteligente: Pix copia e cola / QR (BR Code) e frases ("gastei 25,90 no mercado")
   ============================================================ */

class PixInfo(val valid: Boolean, val amount: Double?, val name: String, val city: String, val key: String?, val txid: String?)

/** CRC16/CCITT-FALSE usado pelo BR Code do Pix. */
fun pixCrc16(s: String): String {
    var crc = 0xFFFF
    for (b in s.toByteArray(Charsets.UTF_8)) {
        crc = crc xor ((b.toInt() and 0xFF) shl 8)
        repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1; crc = crc and 0xFFFF }
    }
    return "%04X".format(crc)
}

private fun tlv(s: String): Map<String, String>? {
    val out = linkedMapOf<String, String>(); var i = 0
    while (i < s.length) {
        if (i + 4 > s.length) return null
        val id = s.substring(i, i + 2); val len = s.substring(i + 2, i + 4).toIntOrNull() ?: return null
        if (i + 4 + len > s.length) return null
        out[id] = s.substring(i + 4, i + 4 + len); i += 4 + len
    }
    return out
}

/** Lê um código Pix (copia e cola ou conteúdo do QR). Devolve null se não for um BR Code do Pix. */
fun parsePixBr(raw: String): PixInfo? {
    val code = raw.trim().replace("\n", "").replace("\r", "").let { if (it.startsWith("000201")) it else return null }
    val top = tlv(code) ?: return null
    val acct = top["26"]?.let { tlv(it) } ?: top["27"]?.let { tlv(it) } ?: return null
    if (acct["00"]?.lowercase()?.contains("br.gov.bcb.pix") != true) return null
    val crcOk = code.length > 8 && code.takeLast(8).startsWith("6304") && pixCrc16(code.dropLast(4)) == code.takeLast(4).uppercase()
    val amount = top["54"]?.toDoubleOrNull()?.takeIf { it > 0 }
    val txid = top["62"]?.let { tlv(it) }?.get("05")?.takeIf { it != "***" }
    return PixInfo(crcOk, amount, top["59"].orEmpty().trim().ifBlank { "Destinatário Pix" }, top["60"].orEmpty().trim(), acct["01"], txid)
}

class SmartEntry(val kind: String, val amount: Double, val who: String, val category: String)

private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private val NUM = Regex("""(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)""")

fun parseSpokenAmount(text: String): Double? {
    val t = plain(text)
    val m = NUM.find(t.replace("r$", " ")) ?: return null
    var v = m.groupValues[1]
    v = if (v.contains(',')) v.replace(".", "").replace(",", ".") else if (Regex("""^\d{1,3}(\.\d{3})+$""").matches(v)) v.replace(".", "") else v
    var n = v.toDoubleOrNull() ?: return null
    if (Regex("""^\s*mil\b""").containsMatchIn(t.substring(m.range.last + 1))) n *= 1000
    return n.takeIf { it > 0 }
}

private val OUT_W = Regex("""\b(gastei|paguei|comprei|gasto|gastou|pago|despesa|saiu|debito)\b""")
private val IN_W = Regex("""\b(recebi|ganhei|entrou|recebimento|caiu|vendi|deposito|depositaram)\b""")
private val PIX_OUT = Regex("""\b(mandei|enviei|transferi|fiz um pix|pix)\b""")

private fun title(s: String) = s.trim().split(" ").filter { it.isNotBlank() }.take(3).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

/** Entende uma frase curta em português. Devolve null se não achar valor ou intenção clara. */
fun parseSmartSentence(text: String, categories: List<String>): SmartEntry? {
    val t = plain(text); val amount = parseSpokenAmount(text) ?: return null
    val isIn = IN_W.containsMatchIn(t); val isOut = OUT_W.containsMatchIn(t); val isPix = PIX_OUT.containsMatchIn(t)
    val kind = when {
        isIn && !isOut -> "entrada"
        isPix && !isIn && (Regex("""\b(para|pra)\b""").containsMatchIn(t) || !isOut) -> "pix"
        isOut || isPix -> "gasto"
        else -> return null
    }
    val low = text.lowercase()
    fun after(vararg w: String): String {
        val re = Regex("""\b(${w.joinToString("|")})\s+([\p{L}0-9][\p{L}0-9 ]{1,30})""")
        val tail = low.substring(NUM.find(low.replace("r$", "  "))?.range?.last?.plus(1)?.coerceAtMost(low.length) ?: 0)
        val r = re.find(tail) ?: re.find(low)
        return r?.groupValues?.get(2)?.replace(Regex("""\b(reais|real|hoje|ontem|agora)\b.*"""), "")?.trim().orEmpty()
    }
    val who = when (kind) { "entrada" -> after("de", "do", "da"); "pix" -> after("para", "pra"); else -> after("em", "no", "na", "com") }
    val cat = guessCategory(t, categories)
    return SmartEntry(kind, amount, title(who), cat)
}

fun guessCategory(plainText: String, categories: List<String>): String {
    val rules = listOf(
        "lazer" to listOf("cinema", "bar", "festa", "jogo", "show", "passeio", "restaurante", "lanche", "pizza", "ifood", "sorvete", "cerveja", "viagem"),
        "presente" to listOf("presente", "aniversario", "lembranca"),
        "imprevisto" to listOf("conserto", "farmacia", "remedio", "medico", "multa", "reparo", "hospital"),
    )
    for ((cat, kws) in rules) if (kws.any { plainText.contains(it) }) categories.firstOrNull { plain(it).contains(cat) }?.let { return it }
    return categories.firstOrNull { plain(it) == "outro" } ?: categories.firstOrNull() ?: "Outro"
}

/* ============================================================
   Cadastro: leitura rápida do ponto de partida
   ============================================================ */

class OnbInsight(val fixedTotal: Double, val incomeRef: Double, val fixedPct: Double?, val reserveMonths: Double?, val reserveTarget: Double, val debtTotal: Double, val tips: List<String>)

val COMMON_EXPENSES = listOf("Aluguel", "Luz", "Água", "Internet", "Celular", "Mercado", "Transporte", "Saúde", "Escola")

fun onboardingInsights(d: OnboardingDraft): OnbInsight {
    val fixed = if (d.wantExpenses) d.expenses.sumOf { it.amount } else 0.0
    val income = when (d.incomeType) { "empregado" -> d.baseSalary; "renda" -> d.beneficioAmount; else -> 0.0 } + if (d.hasSecondaryIncome) d.secondaryAmount else 0.0
    val pct = if (income > 0 && fixed > 0) fixed / income * 100 else null
    val months = if (fixed > 0) d.reserveStart / fixed else null
    val debt = if (d.hasDebts) d.debts.sumOf { it.balance } else 0.0
    val tips = mutableListOf<String>()
    if (pct != null && pct > 70) tips += "Seus gastos fixos pesam bastante na renda. Vale revisar os maiores primeiro."
    if (months != null && months < 3) tips += "Sua reserva cobre menos de 3 meses de gastos fixos. Uma meta comum é chegar a 6 meses, aos poucos."
    if (debt > 0) tips += "Com dívidas, o app monta um plano de quitação na aba Planejar."
    if (tips.isEmpty()) tips += "Ótima base. Registre seus gastos do dia a dia para o app acompanhar o resto."
    return OnbInsight(fixed, income, pct, months, fixed * 6, debt, tips)
}
