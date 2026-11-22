package com.meubanco.patrimonio.data

import android.content.Context
import kotlinx.serialization.json.*
import java.io.File

/** Uma movimentação lida de uma notificação de banco, esperando o usuário confirmar. */
class Cap(val id: Long, val kind: String, val amount: Double, val desc: String, val bank: String)

/** Transforma o texto de avisos de banco em sugestões de lançamento. Tudo no aparelho, sem permissão sensível. */
object BankNotif {
    private val BANKS = linkedMapOf(
        "nu.production" to "Nubank", "caixa" to "Caixa", "mercadopago" to "Mercado Pago", "picpay" to "PicPay",
        "xp" to "XP", "itau" to "Itaú", "bradesco" to "Bradesco", "intermedium" to "Inter", "bb.android" to "Banco do Brasil",
        "santander" to "Santander", "c6bank" to "C6", "uol.ps" to "PagBank", "sicredi" to "Sicredi", "sicoob" to "Sicoob",
        "neon" to "Neon", "btg" to "BTG", "original" to "Original", "next" to "Next",
    )
    fun bankOf(pkg: String): String? = BANKS.entries.firstOrNull { pkg.contains(it.key, ignoreCase = true) }?.value

    private val MONEY = Regex("""R\$\s*([\d]{1,3}(?:\.\d{3})*,\d{2}|\d+,\d{2})""")
    private val IGNORE = Regex("""fatura|limite|senha|código|codigo|token|oferta|promo|vence|vencimento|simula|empréstimo|emprestimo|cashback|investimento rendeu|rendeu""", RegexOption.IGNORE_CASE)
    private val IN = Regex("""recebeu|recebido|recebida|você recebeu|depósito|deposito|crédito em conta|credito em conta|transferência recebida|pix recebido""", RegexOption.IGNORE_CASE)
    private val OUT = Regex("""compra|pagamento|pagou|você pagou|pix enviado|enviou|transferência enviada|transferiu|débito|debito|saque|paga com""", RegexOption.IGNORE_CASE)
    private val WHO = Regex("""(?:\bem|\bpara|\bde|\bno|\bna)\s+([A-Za-zÀ-ú0-9][A-Za-zÀ-ú0-9 &*.\-]{2,28})""")

    fun parseMoney(s: String): Double? = MONEY.find(s)?.groupValues?.get(1)?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull()

    /** Devolve a sugestão, ou null se a notificação não for uma movimentação clara. */
    fun parse(pkg: String, title: String, text: String, now: Long): Cap? { val b = bankOf(pkg) ?: return null; return parseCore(b, title, text, now) }

    /** Texto colado ou compartilhado pelo usuário (aviso do banco, comprovante de Pix). */
    fun parseText(text: String, now: Long): Cap? = parseCore("Compartilhado", "", text, now)

    private fun parseCore(bank: String, title: String, text: String, now: Long): Cap? {
        val all = "$title. $text".replace('\n', ' ')
        if (IGNORE.containsMatchIn(all)) return null
        val amount = parseMoney(all) ?: return null
        if (amount <= 0) return null
        val isIn = IN.containsMatchIn(all); val isOut = OUT.containsMatchIn(all)
        val kind = when { isIn && !isOut -> "entrada"; isOut && !isIn -> "saida"; else -> return null }
        val after = all.substring(MONEY.find(all)!!.range.last + 1)
        val who = (WHO.find(after) ?: WHO.find(all.substringBefore("R$")))?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', '-') ?: ""
        val desc = who.ifBlank { title.take(30).ifBlank { bank } }
        return Cap(now * 1000 + (all.hashCode() and 0x3ff), kind, amount, desc, bank)
    }

    private fun file(ctx: Context) = File(ctx.filesDir, "captures.json")
    private val lock = Any()

    fun load(ctx: Context): List<Cap> = synchronized(lock) {
        try {
            Json.parseToJsonElement(file(ctx).readText()).jsonArray.map { e ->
                val o = e.jsonObject
                Cap(o["id"]!!.jsonPrimitive.long, o["kind"]!!.jsonPrimitive.content, o["amount"]!!.jsonPrimitive.double, o["desc"]!!.jsonPrimitive.content, o["bank"]!!.jsonPrimitive.content)
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(ctx: Context, l: List<Cap>) {
        val arr = JsonArray(l.map { c -> JsonObject(mapOf("id" to JsonPrimitive(c.id), "kind" to JsonPrimitive(c.kind), "amount" to JsonPrimitive(c.amount), "desc" to JsonPrimitive(c.desc), "bank" to JsonPrimitive(c.bank))) })
        file(ctx).writeText(arr.toString())
    }

    /** Guarda a sugestão, ignorando repetida (mesmo valor, tipo e banco em até 2 min) e limitando a 100. */
    fun add(ctx: Context, c: Cap): Boolean = synchronized(lock) {
        val l = load(ctx)
        if (l.any { it.kind == c.kind && it.amount == c.amount && it.bank == c.bank && Math.abs(it.id / 1000 - c.id / 1000) < 120_000 }) return false
        save(ctx, (listOf(c) + l).take(100)); true
    }
    fun remove(ctx: Context, id: Long) = synchronized(lock) { save(ctx, load(ctx).filter { it.id != id }) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("admoney_flags", Context.MODE_PRIVATE)
    /** Modo do dono: libera a tela "Contas conectadas" (Meu Pluggy com chaves e IDs). Desligado para as outras pessoas. */
    fun ownerMode(ctx: Context) = prefs(ctx).getBoolean("owner_mode", false)
    fun setOwnerMode(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("owner_mode", on).apply()
}
