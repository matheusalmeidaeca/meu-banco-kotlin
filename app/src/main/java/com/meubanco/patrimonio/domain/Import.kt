package com.meubanco.patrimonio.domain

import java.text.Normalizer

/* ============================================================
   Importar extrato (OFX ou CSV) com categorização que aprende
   Sem Open Finance, sem permissão sensível: você exporta o arquivo no app do banco e o Admoney lê aqui.
   ============================================================ */

class BankRow(val date: String, val amount: Double, val desc: String, val fitId: String?)

class BankItem(val row: BankRow, val id: String, var category: String, val duplicate: Boolean, var include: Boolean) {
    val isExpense get() = row.amount < 0
}

private fun plainText(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** Valor em formato brasileiro ("1.234,56") ou internacional ("-1234.56"). */
fun bkAmount(raw: String): Double? {
    var t = raw.trim().replace("R$", "").replace(" ", "").replace(" ", "")
    if (t.isEmpty()) return null
    var neg = false
    if (t.startsWith("(") && t.endsWith(")")) { neg = true; t = t.substring(1, t.length - 1) }
    if (t.endsWith("-")) { neg = true; t = t.dropLast(1) }
    val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
    t = if (lastComma >= 0 && lastDot >= 0) { if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "") }
        else if (lastComma >= 0) t.replace(',', '.')
        else if (lastDot >= 0 && t.count { it == '.' } > 1) t.replace(".", "")
        else t
    val v = t.toDoubleOrNull() ?: return null
    return if (neg) -Math.abs(v) else v
}

/** Aceita dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd e yyyyMMdd (com ou sem hora). Devolve yyyy-MM-dd. */
fun bkDate(raw: String): String? {
    val t = raw.trim()
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(t)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
    Regex("^(\\d{2})[/-](\\d{2})[/-](\\d{4})").find(t)?.let { return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }
    Regex("^(\\d{4})(\\d{2})(\\d{2})").find(t)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
    return null
}

fun parseOfx(text: String): List<BankRow> {
    fun tag(block: String, name: String) = Regex("<$name>([^<\\r\\n]*)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim()
    return Regex("<STMTTRN>(.*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(text).mapNotNull { m ->
        val b = m.groupValues[1]
        val date = bkDate(tag(b, "DTPOSTED") ?: return@mapNotNull null) ?: return@mapNotNull null
        val amt = bkAmount(tag(b, "TRNAMT") ?: return@mapNotNull null) ?: return@mapNotNull null
        val desc = (tag(b, "MEMO")?.takeIf { it.isNotBlank() } ?: tag(b, "NAME") ?: "").replace(Regex("\\s+"), " ")
        BankRow(date, amt, desc.ifBlank { "Lançamento" }, tag(b, "FITID")?.takeIf { it.isNotBlank() })
    }.toList()
}

private fun splitCsvLine(line: String, sep: Char): List<String> {
    val out = mutableListOf<String>(); val cur = StringBuilder(); var q = false
    for (c in line) when { c == '"' -> q = !q; c == sep && !q -> { out += cur.toString(); cur.clear() }; else -> cur.append(c) }
    out += cur.toString(); return out.map { it.trim() }
}

fun parseCsv(text: String): List<BankRow> {
    val lines = text.replace("﻿", "").lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val sep = listOf(';', ',', '\t').maxByOrNull { c -> lines.take(5).sumOf { l -> l.count { it == c } } } ?: ';'
    // acha o cabeçalho: primeira linha com uma coluna de data e uma de valor/crédito/débito
    var hi = -1; var cols = emptyList<String>()
    for (i in 0 until Math.min(lines.size, 15)) {
        val c = splitCsvLine(lines[i], sep).map { plainText(it) }
        if (c.any { it.startsWith("data") || it == "date" } && c.any { it.contains("valor") || it.contains("amount") || it.contains("credito") || it.contains("debito") }) { hi = i; cols = c; break }
    }
    if (hi < 0) return emptyList()
    fun idx(vararg k: String) = cols.indexOfFirst { c -> k.any { c.contains(it) } }
    val iDate = cols.indexOfFirst { it.startsWith("data") || it == "date" }
    val iDesc = idx("descri", "historico", "lancamento", "title", "estabelecimento", "memo", "nome").let { if (it == iDate) -1 else it }
    val iVal = idx("valor", "amount"); val iCred = idx("credito"); val iDeb = idx("debito")
    val iId = idx("identificador", "documento", "id")
    return lines.drop(hi + 1).mapNotNull { l ->
        val f = splitCsvLine(l, sep)
        val date = bkDate(f.getOrNull(iDate) ?: return@mapNotNull null) ?: return@mapNotNull null
        val amt = if (iVal >= 0) bkAmount(f.getOrNull(iVal) ?: "")
        else { val c = if (iCred >= 0) bkAmount(f.getOrNull(iCred) ?: "") else null; val d = if (iDeb >= 0) bkAmount(f.getOrNull(iDeb) ?: "") else null
            if (c != null && c != 0.0) Math.abs(c) else if (d != null && d != 0.0) -Math.abs(d) else null }
        if (amt == null || amt == 0.0) return@mapNotNull null
        BankRow(date, amt, (f.getOrNull(iDesc) ?: "").ifBlank { "Lançamento" }, if (iId >= 0) f.getOrNull(iId)?.takeIf { it.length >= 6 } else null)
    }
}

fun parseBankFile(text: String): List<BankRow> =
    if (text.contains("<STMTTRN>", ignoreCase = true) || text.contains("<OFX", ignoreCase = true)) parseOfx(text) else parseCsv(text)

/** Chave estável do estabelecimento: sem números, datas nem pontuação, só as 2 primeiras palavras úteis. */
fun merchantKey(desc: String): String =
    plainText(desc).replace(Regex("[^a-z ]"), " ").split(Regex("\\s+")).filter { it.length > 2 && it !in setOf("compra", "debito", "credito", "pix", "transferencia", "pagamento", "enviada", "recebida", "cartao", "ted", "doc") }.take(2).joinToString(" ")

private val KEYWORD_CATS = listOf(
    "Mercado" to listOf("mercado", "supermerc", "atacad", "assai", "carrefour", "extra", "padaria", "hortifruti", "acougue"),
    "Alimentação" to listOf("ifood", "restaurante", "lanchon", "pizza", "burger", "cafe", "sorvete", "lanche"),
    "Transporte" to listOf("uber", "99 ", "99app", "posto", "combustiv", "gasolina", "estaciona", "pedagio", "onibus", "metro"),
    "Saúde" to listOf("farmacia", "drogaria", "droga", "clinica", "hospital", "medic", "laborat"),
    "Assinaturas" to listOf("netflix", "spotify", "amazon prime", "disney", "youtube", "icloud", "google one", "hbo"),
    "Contas" to listOf("energia", "enel", "equatorial", "agua", "saneamento", "internet", "vivo", "claro", "tim ", "oi "),
    "Lazer" to listOf("cinema", "ingresso", "bar ", "show", "steam", "playstation", "xbox"),
)

/** Primeiro o que você já corrigiu antes (aprendido), depois palavras-chave, por fim "Outro". */
fun suggestCategory(s: AppState, desc: String): String {
    s.catRules[merchantKey(desc)]?.let { return it }
    val p = plainText(desc) + " "
    for ((cat, kws) in KEYWORD_CATS) if (kws.any { p.contains(it) }) return s.freeSpendCategories.firstOrNull { plainText(it) == plainText(cat) } ?: cat
    return s.freeSpendCategories.firstOrNull { plainText(it) == "outro" } ?: "Outro"
}

fun prepareImport(s: AppState, rows: List<BankRow>, invert: Boolean = false): List<BankItem> {
    val seen = HashMap<String, Int>(); val known = s.importedIds.toHashSet(); val oldKeys = s.importedKeys.toHashSet()
    return rows.map { r0 ->
        val r = if (invert) BankRow(r0.date, -r0.amount, r0.desc, r0.fitId) else r0
        val base = r.fitId?.let { "f:$it" } ?: "h:${r.date}|${Math.round(r.amount * 100)}|${merchantKey(r.desc)}"
        val n = (seen[base] ?: 0) + 1; seen[base] = n
        val id = if (r.fitId != null) base else "$base#$n"
        val dup = id in known || "${r.date}|${r.desc.take(60)}|${round2(r.amount)}" in oldKeys
        BankItem(r, id, if (r.amount < 0) suggestCategory(s, r.desc) else "", dup, !dup)
    }
}

/** Corrige a categoria de um lançamento e de todos os outros do mesmo estabelecimento na revisão. */
fun setCategoryForMerchant(items: List<BankItem>, item: BankItem, cat: String) {
    val k = merchantKey(item.row.desc)
    items.forEach { if (it === item || (it.isExpense && !it.duplicate && merchantKey(it.row.desc) == k)) it.category = cat }
}

class BankResult(val expenses: Int, val incomes: Int, val skipped: Int, val spent: Double, val received: Double)

fun applyImport(s: AppState, items: List<BankItem>, updateCash: Boolean): BankResult {
    var e = 0; var i = 0; var sk = 0; var spent = 0.0; var rec = 0.0
    items.forEach { it ->
        if (!it.include || it.duplicate || it.id in s.importedIds) { sk++; return@forEach }
        val r = it.row
        val entry: HistoryEntry
        if (r.amount < 0) {
            val cat = it.category.ifBlank { "Outro" }
            if (s.freeSpendCategories.none { c -> c == cat }) s.freeSpendCategories.add(cat)
            s.catRules[merchantKey(r.desc)] = cat
            entry = HistoryEntry(r.date, "gastolivre", "Extrato — ${r.desc}", r.amount, null, cat); e++; spent += -r.amount
        } else {
            entry = HistoryEntry(r.date, "renda", "Entrada (extrato) — ${r.desc}", r.amount, null, null); i++; rec += r.amount
        }
        if (updateCash) s.cash = round2(s.cash + r.amount)
        s.history.add(0, entry); recordStat(entry, s.monthlyStats)
        s.importedIds.add(it.id)
        s.importedKeys.add("${r.date}|${r.desc.take(60)}|${round2(r.amount)}")
    }
    while (s.history.size > 500) s.history.removeAt(s.history.size - 1)
    while (s.importedIds.size > 6000) s.importedIds.removeAt(0)
    return BankResult(e, i, sk, round2(spent), round2(rec))
}

/* ============================================================
   Confiabilidade: o app confere os próprios dados
   ============================================================ */

class HealthIssue(val severe: Boolean, val text: String)

fun dataHealth(s: AppState): List<HealthIssue> {
    val out = mutableListOf<HealthIssue>()
    if (s.cash < -0.005) out += HealthIssue(true, "O saldo da conta está negativo (${formatBRL(s.cash)}). Confira os últimos lançamentos.")
    s.reserves.filter { it.balance < -0.005 }.forEach { out += HealthIssue(true, "A caixinha ${it.name} está com saldo negativo.") }
    s.goals.filter { it.saved < -0.005 }.forEach { out += HealthIssue(true, "A meta ${it.name} está com valor negativo.") }
    s.assets.filter { it.quantity < 0 || it.price < 0 }.forEach { out += HealthIssue(true, "O ativo ${it.ticker} tem quantidade ou preço negativo.") }
    val dupIds = s.reserves.groupBy { it.id }.filter { it.value.size > 1 }
    if (dupIds.isNotEmpty()) out += HealthIssue(true, "Há caixinhas com o mesmo identificador. Exclua e recrie a duplicada.")
    s.settings.autoRoundTarget.takeIf { it.isNotBlank() && !targetExists(s, it) }?.let { out += HealthIssue(false, "O piloto automático (troco) aponta para uma caixinha que não existe mais.") }
    s.settings.autoSaveTarget.takeIf { it.isNotBlank() && !targetExists(s, it) }?.let { out += HealthIssue(false, "O piloto automático (Pix recebido) aponta para uma caixinha que não existe mais.") }
    if (s.history.size >= 480) out += HealthIssue(false, "O histórico está perto do limite de 500 itens. Exporte um backup para não perder os mais antigos.")
    return out
}

private fun targetExists(s: AppState, opt: String): Boolean {
    val p = opt.split(":"); val id = p.getOrNull(1)?.toIntOrNull() ?: return false
    return when (p[0]) { "reserve" -> s.reserves.any { it.id == id }; "goal" -> s.goals.any { it.id == id && !it.completed }; else -> false }
}
