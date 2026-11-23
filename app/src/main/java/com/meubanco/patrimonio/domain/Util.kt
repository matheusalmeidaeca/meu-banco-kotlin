package com.meubanco.patrimonio.domain

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

const val EPS = 2.220446049250313E-16

val WEEKDAY_NAMES = listOf("domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado")
val MONTH_NAMES = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

fun round2(v: Double): Double = Math.round((v + EPS) * 100.0) / 100.0
fun clamp(v: Double, lo: Double, hi: Double) = Math.max(lo, Math.min(hi, v))
fun clampI(v: Int, lo: Int, hi: Int) = Math.max(lo, Math.min(hi, v))

private val brlFmt: DecimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("pt", "BR"))).apply {
    roundingMode = RoundingMode.HALF_UP
}

/** Igual ao toLocaleString("pt-BR", BRL) do JS: "R$ 1.234,56" (espaço sem quebra) e "-R$ 5,00". */
fun formatBRL(v: Double?): String {
    val x = v ?: 0.0
    val body = brlFmt.format(Math.abs(x))
    return (if (x < 0) "-" else "") + "R$ " + body
}

fun dateToISO(d: LocalDate): String = d.toString()
fun isoToDate(iso: String): LocalDate = LocalDate.parse(iso.substring(0, 10))
fun addDaysISO(iso: String, days: Int): String = isoToDate(iso).plusDays(days.toLong()).toString()
fun ymKey(iso: String): String = iso.substring(0, 7)

/** 0 = domingo … 6 = sábado, como o getDay() do JS. */
fun LocalDate.jsDay(): Int = dayOfWeek.value % 7
fun LocalDate.monthIdx(): Int = monthValue - 1

fun formatSimDate(iso: String): String {
    val d = isoToDate(iso)
    return "${d.dayOfMonth} de ${MONTH_NAMES[d.monthIdx()]} de ${d.year}"
}

fun formatMonthLabel(ym: String): String {
    val y = ym.substring(0, 4)
    val m = ym.substring(5, 7).toInt() - 1
    val n = MONTH_NAMES[m]
    return n.substring(0, 3).replaceFirstChar { it.uppercase() } + "/" + y.substring(2)
}

fun shiftYm(ym: String, d: Int): String {
    var y = ym.substring(0, 4).toInt()
    var m = ym.substring(5, 7).toInt() + d
    while (m > 12) { m -= 12; y++ }
    while (m < 1) { m += 12; y-- }
    return "%04d-%02d".format(y, m)
}

fun daysBetweenISO(fromISO: String, toISO: String): Int =
    java.time.temporal.ChronoUnit.DAYS.between(isoToDate(fromISO), isoToDate(toISO)).toInt()

fun nearestSundayOnOrAfter(d: LocalDate): LocalDate = d.plusDays(((7 - d.jsDay()) % 7).toLong())

/** Mesmo "hash" do app em HTML — assim o PIN de um backup importado continua valendo. */
fun simpleHash(str: String): String {
    var h = 0
    for (ch in str) h = ((h shl 5) - h + ch.code)
    return h.toString()
}

fun ageFromISO(iso: String?): Int? {
    if (iso.isNullOrBlank()) return null
    val b = isoToDate(iso)
    val t = AppClock.today()
    var a = t.year - b.year
    if (t.monthValue < b.monthValue || (t.monthValue == b.monthValue && t.dayOfMonth < b.dayOfMonth)) a--
    return if (a in 0..129) a else null
}

fun parseBrNumber(raw: String): Double? {
    var s = raw.trim()
    if (s.contains(",")) s = s.replace(".", "").replace(",", ".")
    return s.toDoubleOrNull()?.takeIf { it.isFinite() }
}

// ---------- catálogos ----------
class CatalogExpense(val name: String, val category: String, val value: Double)
class CommonProduct(val name: String, val category: String, val price: Double)

val EXPENSE_CATALOG = listOf(
    CatalogExpense("Aluguel", "Moradia", 800.0), CatalogExpense("Condomínio", "Moradia", 300.0),
    CatalogExpense("Financiamento do imóvel", "Moradia", 900.0), CatalogExpense("IPTU", "Moradia", 100.0),
    CatalogExpense("Energia elétrica", "Contas", 120.0), CatalogExpense("Água e esgoto", "Contas", 70.0),
    CatalogExpense("Gás de cozinha", "Contas", 110.0), CatalogExpense("Internet", "Contas", 100.0),
    CatalogExpense("Celular", "Contas", 60.0), CatalogExpense("TV por assinatura", "Contas", 90.0),
    CatalogExpense("Streaming", "Lazer", 40.0), CatalogExpense("Academia", "Saúde", 90.0),
    CatalogExpense("Plano de saúde", "Saúde", 350.0), CatalogExpense("Farmácia / remédios", "Saúde", 100.0),
    CatalogExpense("Transporte (passe)", "Transporte", 200.0), CatalogExpense("Combustível", "Transporte", 300.0),
    CatalogExpense("Seguro do carro", "Transporte", 150.0), CatalogExpense("Financiamento do veículo", "Transporte", 700.0),
    CatalogExpense("Escola / faculdade", "Educação", 400.0), CatalogExpense("Curso", "Educação", 150.0),
    CatalogExpense("Pensão alimentícia", "Outros", 500.0), CatalogExpense("Empréstimo", "Outros", 250.0),
)

val COMMON_PRODUCTS = listOf(
    CommonProduct("Arroz 5kg", "mercearia", 28.0), CommonProduct("Feijão 1kg", "mercearia", 8.0),
    CommonProduct("Açúcar 1kg", "mercearia", 5.0), CommonProduct("Óleo de soja 900ml", "mercearia", 8.0),
    CommonProduct("Macarrão 500g", "mercearia", 5.0), CommonProduct("Café 500g", "mercearia", 20.0),
    CommonProduct("Leite 1L", "laticínios", 5.0), CommonProduct("Ovos (dúzia)", "laticínios", 12.0),
    CommonProduct("Pão de forma", "padaria", 8.0), CommonProduct("Frango 1kg", "carnes", 14.0),
    CommonProduct("Carne moída 1kg", "carnes", 35.0), CommonProduct("Banana 1kg", "hortifruti", 6.0),
    CommonProduct("Tomate 1kg", "hortifruti", 8.0), CommonProduct("Sabão em pó 1kg", "limpeza", 14.0),
    CommonProduct("Detergente", "limpeza", 3.0), CommonProduct("Papel higiênico (12)", "higiene", 18.0),
)

val FREE_SPEND_CATALOG = listOf(
    "Restaurante", "Lanche / padaria", "Delivery", "Bar / balada", "Cinema / shows", "Viagem", "Presentes",
    "Vestuário", "Beleza / cabelo", "Pets", "Jogos / apps", "Compras online", "Hobbies", "Doações",
    "Transporte por app", "Imprevistos",
)
val EXPENSE_CATEGORIES = listOf("Moradia", "Contas", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Outros")
val CATEGORY_LABELS = mapOf("acao" to "Ações", "tijolo" to "FIIs Tijolo", "papel" to "FIIs Papel")
val GOAL_TYPE_LABELS = mapOf("movel" to "Móvel", "imovel" to "Imóvel", "automovel" to "Automóvel", "outro" to "Outro")
val GOAL_TYPE_ICON = mapOf("movel" to "🛋", "imovel" to "🏠", "automovel" to "🚗", "outro" to "🎯")

val HIST_TYPE_ICON = mapOf(
    "dividendo" to "💰", "aporte" to "⬆", "saque" to "⬇", "compra" to "🧾", "cdb" to "🏦", "meta" to "🎯",
    "conquista" to "🏆", "aviso" to "⚠", "renda" to "💵", "despesa" to "📤", "gastolivre" to "🎉", "pix" to "🔁",
    "cartao" to "💳", "fatura" to "🧾", "transferencia" to "↔", "venda" to "💱", "imposto" to "🏛",
)
val HIST_TYPE_LABELS = linkedMapOf(
    "todos" to "Todos", "renda" to "Renda", "despesa" to "Gastos", "gastolivre" to "Gasto livre", "pix" to "Pix",
    "cartao" to "Cartão", "fatura" to "Fatura", "transferencia" to "Transferências", "dividendo" to "Dividendos",
    "aporte" to "Aportes", "saque" to "Retiradas", "compra" to "Compras", "venda" to "Vendas", "imposto" to "Impostos",
    "cdb" to "Reservas", "meta" to "Metas", "conquista" to "Conquistas", "aviso" to "Avisos",
)
val BANK_ACTIVITY_TYPES = setOf("pix", "cartao", "fatura", "transferencia")

val MAIN_TABS = listOf(
    "conta" to "Conta & cartão",
    "historico" to "Extrato & histórico",
    "carteira" to "Carteira de investimentos",
    "gastos" to "Gastos",
    "plano" to "Planejamento",
    "ponto" to "Ponto",
    "ajuda" to "Aprender & ajuda",
    "config" to "Configurações",
    "reais" to "Contas conectadas",
    "capturas" to "Capturas dos bancos",
    "smart" to "Lançamento inteligente",
    "ir" to "Resumo para o IR",
    "mais" to "Mais",
)
