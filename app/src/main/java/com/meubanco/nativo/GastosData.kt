package com.meubanco.nativo

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ============================================================
// Dados e lógica da aba Gastos (mesmos campos do app HTML)
// ============================================================

val EXPENSE_CATEGORIES = listOf("Moradia", "Contas", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Outros")

class CatalogExpense(val name: String, val category: String, val value: Int)

val EXPENSE_CATALOG = listOf(
    CatalogExpense("Aluguel", "Moradia", 800), CatalogExpense("Condomínio", "Moradia", 300),
    CatalogExpense("Financiamento do imóvel", "Moradia", 900), CatalogExpense("IPTU", "Moradia", 100),
    CatalogExpense("Energia elétrica", "Contas", 120), CatalogExpense("Água e esgoto", "Contas", 70),
    CatalogExpense("Gás de cozinha", "Contas", 110), CatalogExpense("Internet", "Contas", 100),
    CatalogExpense("Celular", "Contas", 60), CatalogExpense("TV por assinatura", "Contas", 90),
    CatalogExpense("Streaming", "Lazer", 40), CatalogExpense("Academia", "Saúde", 90),
    CatalogExpense("Plano de saúde", "Saúde", 350), CatalogExpense("Farmácia / remédios", "Saúde", 100),
    CatalogExpense("Transporte (passe)", "Transporte", 200), CatalogExpense("Combustível", "Transporte", 300),
    CatalogExpense("Seguro do carro", "Transporte", 150), CatalogExpense("Financiamento do veículo", "Transporte", 700),
    CatalogExpense("Escola / faculdade", "Educação", 400), CatalogExpense("Curso", "Educação", 150),
    CatalogExpense("Pensão alimentícia", "Outros", 500), CatalogExpense("Empréstimo", "Outros", 250),
)

class CommonProduct(val name: String, val category: String, val price: Double)

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

val PIE_COLORS = listOf(
    Color(0xFF4F8CFF), Color(0xFFE0A43A), Color(0xFF2FBF8F), Color(0xFFE5645F),
    Color(0xFF9B7BEA), Color(0xFF38B6C9), Color(0xFFD96FA8), Color(0xFF8996AB),
)

fun round2(v: Double): Double = Math.round(v * 100) / 100.0
fun parseNum(s: String): Double = s.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
fun fmt2(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)
fun fmtKg(kg: Double): String = String.format(Locale("pt", "BR"), "%.3f", Math.round(kg * 1000) / 1000.0) + " kg"

fun JSONObject.arr(key: String): MutableList<JSONObject> =
    (optJSONArray(key)?.objects() ?: emptyList()).toMutableList()

fun JSONObject.putArr(key: String, list: List<JSONObject>) {
    put(key, JSONArray(list))
}

fun JSONObject.nextId(counter: String, listKey: String): Int {
    val cur = if (has(counter)) optInt(counter, 1) else (arr(listKey).maxOfOrNull { it.optInt("id", 0) } ?: 0) + 1
    put(counter, cur + 1)
    return cur
}

// ---------------- Gastos fixos ----------------

fun JSONObject.addExpense(name: String, amount: Double, category: String) {
    val list = arr("expenses")
    val id = nextId("nextExpenseId", "expenses")
    list.add(JSONObject().put("id", id).put("name", name).put("amount", amount).put("category", category))
    putArr("expenses", list)
}

fun JSONObject.updateExpense(id: Int, name: String, amount: Double, category: String) {
    val e = arr("expenses").firstOrNull { it.optInt("id") == id } ?: return
    e.put("name", name).put("amount", amount).put("category", category)
}

fun JSONObject.removeExpense(id: Int) {
    putArr("expenses", arr("expenses").filter { it.optInt("id") != id })
}

fun JSONObject.expensesTotal(): Double = arr("expenses").fold(0.0) { a, e -> a + e.optDouble("amount", 0.0) }

// ---------------- Lista de compras ----------------

private fun JSONObject.lineTotal(i: JSONObject): Double = i.optDouble("price", 0.0) * i.optInt("quantity", 1)

fun JSONObject.shoppingDiscountFor(sub: Double): Double {
    val full = arr("shoppingList").fold(0.0) { a, i -> a + lineTotal(i) }
    val d = optJSONObject("shoppingDiscount")
    val v = Math.max(0.0, d?.optDouble("value", 0.0) ?: 0.0)
    if (v == 0.0 || sub <= 0 || full <= 0) return 0.0
    return round2(if (d?.optString("type") == "valor") Math.min(sub, v * sub / full) else sub * Math.min(v, 100.0) / 100)
}

fun JSONObject.shoppingSubtotal(): Double = arr("shoppingList").fold(0.0) { a, i -> a + lineTotal(i) }

fun JSONObject.shoppingFullTotal(): Double {
    val sub = shoppingSubtotal()
    return round2(sub - shoppingDiscountFor(sub))
}

fun JSONObject.shoppingCheckedTotal(): Double {
    val sub = arr("shoppingList").filter { it.optBoolean("checked") }.fold(0.0) { a, i -> a + lineTotal(i) }
    return round2(sub - shoppingDiscountFor(sub))
}

/** O total da lista vira o gasto fixo "Alimentação" (como no app HTML). */
fun JSONObject.syncShoppingToFoodExpense() {
    val list = arr("expenses")
    val linked = optInt("shoppingLinkedExpenseId", -1)
    val existing = list.firstOrNull { it.optInt("id") == linked }
    val exp = existing ?: JSONObject().also { n ->
        val id = nextId("nextExpenseId", "expenses")
        n.put("id", id).put("name", "Alimentação").put("amount", 0.0).put("category", "Alimentação")
        list.add(n)
        put("shoppingLinkedExpenseId", id)
        putArr("expenses", list)
    }
    exp.put("amount", shoppingFullTotal())
}

private fun JSONObject.newShopItem(name: String, category: String, price: Double, qty: Int): JSONObject =
    JSONObject()
        .put("id", nextId("nextShoppingId", "shoppingList"))
        .put("name", name)
        .put("category", category.ifEmpty { "Geral" })
        .put("price", price)
        .put("quantity", qty)
        .put("checked", false)

fun JSONObject.addShopItem(name: String, category: String, price: Double, qty: Int) {
    val list = arr("shoppingList")
    list.add(newShopItem(name, category, price, qty))
    putArr("shoppingList", list)
    syncShoppingToFoodExpense()
}

fun JSONObject.toggleShop(id: Int) {
    val i = arr("shoppingList").firstOrNull { it.optInt("id") == id } ?: return
    i.put("checked", !i.optBoolean("checked"))
}

fun JSONObject.changeShopQty(id: Int, delta: Int) {
    val i = arr("shoppingList").firstOrNull { it.optInt("id") == id } ?: return
    i.put("quantity", Math.max(1, i.optInt("quantity", 1) + delta))
    syncShoppingToFoodExpense()
}

fun JSONObject.removeShopItem(id: Int) {
    putArr("shoppingList", arr("shoppingList").filter { it.optInt("id") != id })
    syncShoppingToFoodExpense()
}

fun JSONObject.resetShoppingChecks() {
    arr("shoppingList").forEach { it.put("checked", false) }
}

fun JSONObject.editShopItem(id: Int, name: String, category: String, qtyOrKg: Double, price: Double) {
    val i = arr("shoppingList").firstOrNull { it.optInt("id") == id } ?: return
    i.put("name", name).put("category", category.ifEmpty { "Geral" })
    if (i.optDouble("weightKg", 0.0) > 0) {
        val kg = Math.round(Math.max(0.001, if (qtyOrKg > 0) qtyOrKg else i.optDouble("weightKg")) * 1000) / 1000.0
        i.put("weightKg", kg).put("price", round2(i.optDouble("pricePerKg", 0.0) * kg))
    } else {
        i.put("quantity", Math.max(1, Math.round(qtyOrKg).toInt())).put("price", Math.max(0.0, price))
    }
    syncShoppingToFoodExpense()
}

fun JSONObject.setShoppingDiscount(type: String, value: Double) {
    put("shoppingDiscount", JSONObject().put("type", type).put("value", Math.max(0.0, value)))
    syncShoppingToFoodExpense()
}

// ---------------- Catálogo de produtos ----------------

fun JSONObject.addProductToList(p: JSONObject) {
    val list = arr("shoppingList")
    val pid = p.optInt("id")
    val existing = list.firstOrNull { it.optInt("productId", -1) == pid }
    if (existing != null) {
        existing.put("quantity", existing.optInt("quantity", 1) + 1)
    } else {
        list.add(
            newShopItem(p.optString("name"), p.optString("category"), p.optDouble("price", 0.0), 1)
                .put("productId", pid)
                .put("barcode", p.optString("barcode"))
                .put("variant", p.optString("variant")),
        )
        putArr("shoppingList", list)
    }
    syncShoppingToFoodExpense()
}

/** Produto por peso: informa só o peso e o valor é calculado (preço por kg × peso). */
fun JSONObject.addWeighedToList(p: JSONObject, kg: Double) {
    val kgR = Math.round(kg * 1000) / 1000.0
    val pricePerKg = p.optDouble("price", 0.0)
    val pid = p.optInt("id")
    val list = arr("shoppingList")
    val existing = list.firstOrNull { it.optInt("productId", -1) == pid && it.optDouble("weightKg", 0.0) > 0 }
    if (existing != null) {
        val w = Math.round((existing.optDouble("weightKg", 0.0) + kgR) * 1000) / 1000.0
        existing.put("weightKg", w).put("pricePerKg", pricePerKg).put("price", round2(pricePerKg * w))
    } else {
        list.add(
            newShopItem(p.optString("name"), p.optString("category"), round2(pricePerKg * kgR), 1)
                .put("productId", pid)
                .put("barcode", p.optString("barcode"))
                .put("variant", p.optString("variant"))
                .put("weightKg", kgR)
                .put("pricePerKg", pricePerKg),
        )
        putArr("shoppingList", list)
    }
    syncShoppingToFoodExpense()
}

fun JSONObject.saveProduct(
    editId: Int?,
    name: String,
    category: String,
    price: Double,
    barcode: String,
    brand: String,
    variant: String,
    byWeight: Boolean,
): JSONObject {
    val list = arr("products")
    val target = if (editId != null) list.firstOrNull { it.optInt("id") == editId } else null
    val prod = target ?: JSONObject().also {
        it.put("id", nextId("nextProductId", "products"))
        list.add(it)
    }
    prod.put("name", name)
        .put("category", category.ifEmpty { "Geral" })
        .put("price", Math.max(0.0, price))
        .put("barcode", barcode)
        .put("brand", brand)
        .put("variant", variant)
        .put("byWeight", byWeight)
    putArr("products", list)
    return prod
}

fun JSONObject.deleteProduct(id: Int) {
    putArr("products", arr("products").filter { it.optInt("id") != id })
}

fun JSONObject.seedCommonProducts() {
    val list = arr("products")
    COMMON_PRODUCTS.forEach { c ->
        if (list.none { it.optString("name").equals(c.name, ignoreCase = true) }) {
            list.add(
                JSONObject()
                    .put("id", nextId("nextProductId", "products"))
                    .put("name", c.name)
                    .put("category", c.category)
                    .put("price", c.price)
                    .put("barcode", ""),
            )
            putArr("products", list)
        }
    }
}

// ---------------- Gráfico "Para onde vai o dinheiro" ----------------

class PieSlice(val label: String, val value: Double)

fun shiftYm(ym: String, d: Int): String {
    val p = ym.split("-")
    var y = p.getOrNull(0)?.toIntOrNull() ?: 2026
    var m = (p.getOrNull(1)?.toIntOrNull() ?: 1) + d
    while (m > 12) { m -= 12; y++ }
    while (m < 1) { m += 12; y-- }
    return "%04d-%02d".format(y, m)
}

fun formatMonthLabel(ym: String): String {
    val p = ym.split("-")
    val y = p.getOrNull(0)?.toIntOrNull() ?: return ym
    val m = p.getOrNull(1)?.toIntOrNull() ?: return ym
    val name = MONTH_NAMES[m - 1].take(3)
    return name.replaceFirstChar { it.uppercase() } + "/" + y.toString().takeLast(2)
}

fun JSONObject.firstStatsYm(): String? =
    optJSONObject("monthlyStats")?.keys()?.asSequence()?.toList()?.sorted()?.firstOrNull()

fun JSONObject.pieSlices(mode: String, ym: String): List<PieSlice> {
    val cats = LinkedHashMap<String, Double>()
    if (mode == "plan") {
        arr("expenses").forEach { e ->
            val c = e.optString("category").ifEmpty { e.optString("name").ifEmpty { "Gastos" } }
            cats[c] = round2((cats[c] ?: 0.0) + e.optDouble("amount", 0.0))
        }
    } else {
        val o = optJSONObject("monthlyStats")?.optJSONObject(ym)?.optJSONObject("cats")
        if (o != null) o.keys().forEach { k -> cats[k] = o.optDouble(k, 0.0) }
    }
    var list = cats.map { PieSlice(it.key, it.value) }.filter { it.value > 0 }.sortedByDescending { it.value }
    if (list.size > 7) {
        val rest = list.drop(7).fold(0.0) { a, x -> a + x.value }
        list = list.take(7) + PieSlice("Outros", round2(rest))
    }
    return list
}
