package com.meubanco.nativo

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

// ============================================================
// Catálogos (copiados do app HTML)
// ============================================================

class CatalogExpense(val name: String, val category: String, val value: Double)

val EXPENSE_CATEGORIES = listOf("Moradia", "Contas", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Outros")

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

// ============================================================
// Lógica (equivale às funções de gastos/compras do app HTML)
// ============================================================

fun r2(v: Double): Double = Math.round((v + 1e-9) * 100.0) / 100.0

fun parseNum(s: String): Double = s.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

fun money2(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

fun fmtKg(kg: Double): String = String.format(Locale("pt", "BR"), "%.3f kg", kg)

/** Número para preencher um campo de texto (sem ".0" sobrando). */
fun numText(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace(".", ",")

fun ddmmyyyy(iso: String): String = try {
    val d = LocalDate.parse(iso)
    "${pad2(d.dayOfMonth)}/${pad2(d.monthValue)}/${d.year}"
} catch (e: Exception) {
    iso
}

fun ymLabel(ym: String): String {
    val m = ym.substring(5, 7).toIntOrNull() ?: return ym
    return MONTH_NAMES.getOrElse(m - 1) { "" } + " de " + ym.take(4)
}

fun shiftYm(ym: String, d: Int): String {
    var y = ym.take(4).toIntOrNull() ?: return ym
    var m = (ym.substring(5, 7).toIntOrNull() ?: return ym) + d
    while (m > 12) { m -= 12; y++ }
    while (m < 1) { m += 12; y-- }
    return "$y-${pad2(m)}"
}

/** String segura: campo ausente ou null vira vazio (nunca "null"). */
fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

fun JSONObject.objList(key: String): MutableList<JSONObject> =
    (optJSONArray(key)?.objects() ?: emptyList()).toMutableList()

fun JSONObject.putList(key: String, items: List<JSONObject>) {
    put(key, JSONArray(items))
}

/** Pega o próximo id de um contador do estado (nextExpenseId etc.), sem repetir ids existentes. */
fun JSONObject.takeId(counter: String, listKey: String): Int {
    val maxId = objList(listKey).maxOfOrNull { it.optInt("id") } ?: 0
    val id = Math.max(optInt(counter, 1), maxId + 1)
    put(counter, id + 1)
    return id
}

// ---- linhas imutáveis para a tela (evitam telas "congeladas" ao mudar o JSON no lugar) ----

data class ExpRow(val id: Int, val name: String, val amount: Double, val category: String)

data class ShopRow(
    val id: Int,
    val name: String,
    val category: String,
    val variant: String,
    val barcode: String,
    val price: Double,
    val qty: Int,
    val checked: Boolean,
    val weightKg: Double,
    val pricePerKg: Double,
)

data class ProdRow(
    val id: Int,
    val name: String,
    val category: String,
    val price: Double,
    val barcode: String,
    val brand: String,
    val variant: String,
    val byWeight: Boolean,
)

class PieSlice(val label: String, val value: Double)

fun JSONObject.expRows(): List<ExpRow> = objList("expenses").map {
    ExpRow(it.optInt("id"), it.str("name"), it.optDouble("amount", 0.0), it.str("category").ifEmpty { "Outros" })
}

fun JSONObject.shopRows(): List<ShopRow> = objList("shoppingList").map {
    ShopRow(
        id = it.optInt("id"),
        name = it.str("name"),
        category = it.str("category").ifEmpty { "Geral" },
        variant = it.str("variant"),
        barcode = it.str("barcode"),
        price = it.optDouble("price", 0.0),
        qty = Math.max(1, it.optInt("quantity", 1)),
        checked = it.optBoolean("checked", false),
        weightKg = it.optDouble("weightKg", 0.0),
        pricePerKg = it.optDouble("pricePerKg", 0.0),
    )
}

fun JSONObject.prodRows(): List<ProdRow> = objList("products").map {
    ProdRow(
        id = it.optInt("id"),
        name = it.str("name"),
        category = it.str("category").ifEmpty { "Geral" },
        price = it.optDouble("price", 0.0),
        barcode = it.str("barcode"),
        brand = it.str("brand"),
        variant = it.str("variant"),
        byWeight = it.optBoolean("byWeight", false),
    )
}

// ---- gastos fixos ----

fun JSONObject.addExpense(name: String, amount: Double, category: String) {
    val list = objList("expenses")
    val id = takeId("nextExpenseId", "expenses")
    list.add(JSONObject().put("id", id).put("name", name).put("amount", amount).put("category", category))
    putList("expenses", list)
}

fun JSONObject.editExpense(id: Int, name: String, amount: Double, category: String) {
    val e = objList("expenses").firstOrNull { it.optInt("id") == id } ?: return
    e.put("name", name)
    e.put("amount", amount)
    e.put("category", category)
}

fun JSONObject.removeExpense(id: Int) {
    putList("expenses", objList("expenses").filter { it.optInt("id") != id })
}

// ---- lista de compras ----

fun JSONObject.shopSub(onlyChecked: Boolean): Double =
    objList("shoppingList")
        .filter { !onlyChecked || it.optBoolean("checked", false) }
        .sumOf { it.optDouble("price", 0.0) * Math.max(1, it.optInt("quantity", 1)) }

fun JSONObject.shopDiscountFor(sub: Double): Double {
    val full = shopSub(false)
    val d = optJSONObject("shoppingDiscount")
    val v = Math.max(0.0, d?.optDouble("value", 0.0) ?: 0.0)
    if (v <= 0.0 || sub <= 0.0 || full <= 0.0) return 0.0
    val isValor = d?.optString("type") == "valor"
    return r2(if (isValor) Math.min(sub, v * sub / full) else sub * Math.min(v, 100.0) / 100.0)
}

fun JSONObject.shopFullTotal(): Double {
    val s = shopSub(false)
    return r2(s - shopDiscountFor(s))
}

fun JSONObject.shopCartTotal(): Double {
    val s = shopSub(true)
    return r2(s - shopDiscountFor(s))
}

/** O total da lista vira o gasto fixo "Alimentação" (como no app HTML). */
fun JSONObject.syncFoodExpense() {
    val list = objList("expenses")
    val linkedId = optInt("shoppingLinkedExpenseId", -1)
    val existing = list.firstOrNull { it.optInt("id") == linkedId }
    val exp = existing ?: JSONObject().also { n ->
        val id = takeId("nextExpenseId", "expenses")
        n.put("id", id)
        n.put("name", "Alimentação")
        n.put("amount", 0.0)
        n.put("category", "Alimentação")
        list.add(n)
        put("shoppingLinkedExpenseId", id)
        putList("expenses", list)
    }
    exp.put("amount", shopFullTotal())
}

fun JSONObject.addShopItem(name: String, category: String, price: Double, qty: Int) {
    val list = objList("shoppingList")
    val id = takeId("nextShoppingId", "shoppingList")
    list.add(
        JSONObject().put("id", id).put("name", name)
            .put("category", category.ifBlank { "Geral" })
            .put("price", price).put("quantity", Math.max(1, qty)).put("checked", false),
    )
    putList("shoppingList", list)
    syncFoodExpense()
}

fun JSONObject.addProductToList(productId: Int) {
    val p = objList("products").firstOrNull { it.optInt("id") == productId } ?: return
    val list = objList("shoppingList")
    val existing = list.firstOrNull { it.optInt("productId", -1) == productId && it.optDouble("weightKg", 0.0) <= 0.0 }
    if (existing != null) {
        existing.put("quantity", existing.optInt("quantity", 1) + 1)
    } else {
        val id = takeId("nextShoppingId", "shoppingList")
        list.add(
            JSONObject().put("id", id).put("name", p.str("name"))
                .put("category", p.str("category").ifEmpty { "Geral" })
                .put("price", p.optDouble("price", 0.0)).put("quantity", 1).put("checked", false)
                .put("productId", productId).put("barcode", p.str("barcode")).put("variant", p.str("variant")),
        )
    }
    putList("shoppingList", list)
    syncFoodExpense()
}

fun JSONObject.addWeighed(productId: Int, kgRaw: Double) {
    val p = objList("products").firstOrNull { it.optInt("id") == productId } ?: return
    val kg = Math.round(kgRaw * 1000.0) / 1000.0
    if (kg <= 0.0) return
    val perKg = p.optDouble("price", 0.0)
    val list = objList("shoppingList")
    val existing = list.firstOrNull { it.optInt("productId", -1) == productId && it.optDouble("weightKg", 0.0) > 0.0 }
    if (existing != null) {
        val w = Math.round((existing.optDouble("weightKg", 0.0) + kg) * 1000.0) / 1000.0
        existing.put("weightKg", w)
        existing.put("pricePerKg", perKg)
        existing.put("price", r2(perKg * w))
    } else {
        val id = takeId("nextShoppingId", "shoppingList")
        list.add(
            JSONObject().put("id", id).put("name", p.str("name"))
                .put("category", p.str("category").ifEmpty { "Geral" })
                .put("price", r2(perKg * kg)).put("quantity", 1).put("checked", false)
                .put("productId", productId).put("barcode", p.str("barcode")).put("variant", p.str("variant"))
                .put("weightKg", kg).put("pricePerKg", perKg),
        )
    }
    putList("shoppingList", list)
    syncFoodExpense()
}

fun JSONObject.saveEditShop(id: Int, name: String, cat: String, kgText: String, qtyText: String, priceText: String) {
    val item = objList("shoppingList").firstOrNull { it.optInt("id") == id } ?: return
    if (name.isBlank()) return
    item.put("name", name.trim())
    item.put("category", cat.trim().ifEmpty { "Geral" })
    if (item.optDouble("weightKg", 0.0) > 0.0) {
        val typed = parseNum(kgText)
        val base = if (typed > 0.0) typed else item.optDouble("weightKg", 0.0)
        val kg = Math.round(Math.max(0.001, base) * 1000.0) / 1000.0
        item.put("weightKg", kg)
        item.put("price", r2(item.optDouble("pricePerKg", 0.0) * kg))
    } else {
        item.put("quantity", Math.max(1, qtyText.toIntOrNull() ?: 1))
        item.put("price", Math.max(0.0, parseNum(priceText)))
    }
    syncFoodExpense()
}

fun JSONObject.toggleShopItem(id: Int) {
    val item = objList("shoppingList").firstOrNull { it.optInt("id") == id } ?: return
    item.put("checked", !item.optBoolean("checked", false))
}

fun JSONObject.changeShopQty(id: Int, delta: Int) {
    val item = objList("shoppingList").firstOrNull { it.optInt("id") == id } ?: return
    item.put("quantity", Math.max(1, item.optInt("quantity", 1) + delta))
    syncFoodExpense()
}

fun JSONObject.removeShopItem(id: Int) {
    putList("shoppingList", objList("shoppingList").filter { it.optInt("id") != id })
    syncFoodExpense()
}

fun JSONObject.resetShoppingChecks() {
    objList("shoppingList").forEach { it.put("checked", false) }
}

fun JSONObject.applyShopDiscount(type: String, value: Double) {
    put("shoppingDiscount", JSONObject().put("type", type).put("value", Math.max(0.0, value)))
    syncFoodExpense()
}

// ---- catálogo de produtos ----

fun JSONObject.saveProduct(
    id: Int?,
    name: String,
    category: String,
    price: Double,
    barcode: String,
    brand: String,
    variant: String,
    byWeight: Boolean,
): Int {
    val list = objList("products")
    val found = if (id != null) list.firstOrNull { it.optInt("id") == id } else null
    val p = found ?: JSONObject().also { n ->
        n.put("id", takeId("nextProductId", "products"))
        list.add(n)
    }
    p.put("name", name)
    p.put("category", category)
    p.put("price", price)
    p.put("barcode", barcode)
    p.put("brand", brand)
    p.put("variant", variant)
    p.put("byWeight", byWeight)
    putList("products", list)
    return p.optInt("id")
}

fun JSONObject.removeProduct(id: Int) {
    putList("products", objList("products").filter { it.optInt("id") != id })
}

fun JSONObject.seedProducts() {
    val list = objList("products")
    COMMON_PRODUCTS.forEach { c ->
        if (list.none { it.str("name").equals(c.name, ignoreCase = true) }) {
            val id = takeId("nextProductId", "products")
            list.add(
                JSONObject().put("id", id).put("name", c.name).put("category", c.category)
                    .put("price", c.price).put("barcode", ""),
            )
            putList("products", list)
        }
    }
}

// ---- gasto livre ----

fun JSONObject.freeCats(): List<String> {
    val a = optJSONArray("freeSpendCategories") ?: return listOf("Outro")
    val out = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
    return if (out.isEmpty()) listOf("Outro") else out
}

/** Registra no histórico e nas estatísticas do mês (gráficos do Histórico e de Gastos). */
fun JSONObject.addHistory(
    type: String,
    label: String,
    amount: Double,
    category: String? = null,
    cats: Map<String, Double>? = null,
) {
    val e = JSONObject()
        .put("ts", optString("simDateISO", todayISO()))
        .put("type", type)
        .put("label", label)
        .put("amount", amount)
    if (category != null) e.put("category", category)
    if (cats != null) {
        val c = JSONObject()
        cats.forEach { (k, v) -> c.put(k, v) }
        e.put("cats", c)
    }
    val hist = objList("history")
    hist.add(0, e)
    putList("history", hist.take(500))
    recordStat(e)
}

/** Retorna quanto foi realmente gasto (limitado ao saldo da conta). */
fun JSONObject.spendFree(amount: Double, category: String): Double {
    if (amount <= 0.0) return 0.0
    val cash = optDouble("cash", 0.0)
    val actual = r2(Math.min(amount, Math.max(0.0, cash)))
    if (actual <= 0.0) return 0.0
    put("cash", cash - actual)
    val cat = if (category in freeCats()) category else "Outro"
    addHistory("gastolivre", "Gasto livre — $cat", -actual, cat)
    if (actual < amount - 0.01) {
        addHistory("aviso", "Caixa livre insuficiente: gasto reduzido de ${brl(amount)} para ${brl(actual)}", 0.0)
    }
    return actual
}

private fun JSONObject.freeSpendEntries(): List<JSONObject> {
    val ym = optString("simDateISO", todayISO()).take(7)
    return objList("history").filter { it.optString("type") == "gastolivre" && it.optString("ts").take(7) == ym }
}

fun JSONObject.freeSpentThisMonth(): Double = freeSpendEntries().sumOf { Math.abs(it.optDouble("amount", 0.0)) }

fun JSONObject.freeSpentByCategory(): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    freeCats().forEach { out[it] = 0.0 }
    freeSpendEntries().forEach { h ->
        val c = h.str("category")
        val key = if (c.isNotEmpty() && out.containsKey(c)) c else "Outro"
        out[key] = (out[key] ?: 0.0) + Math.abs(h.optDouble("amount", 0.0))
    }
    return out
}

// ---- gráfico "Para onde vai o dinheiro" ----

fun JSONObject.firstStatMonth(): String? {
    val stats = optJSONObject("monthlyStats") ?: return null
    return stats.keys().asSequence().toList().sorted().firstOrNull()
}

fun JSONObject.pieSlices(mode: String, ym: String): List<PieSlice> {
    val cats = LinkedHashMap<String, Double>()
    if (mode == "plan") {
        objList("expenses").forEach { e ->
            val c = e.str("category").ifEmpty { e.str("name").ifEmpty { "Gastos" } }
            cats[c] = r2((cats[c] ?: 0.0) + e.optDouble("amount", 0.0))
        }
    } else {
        val o = optJSONObject("monthlyStats")?.optJSONObject(ym)?.optJSONObject("cats")
        if (o != null) o.keys().forEach { k -> cats[k] = o.optDouble(k, 0.0) }
    }
    var list = cats.map { PieSlice(it.key, it.value) }.filter { it.value > 0.0 }.sortedByDescending { it.value }
    if (list.size > 7) {
        val rest = list.drop(7).sumOf { it.value }
        list = list.take(7) + PieSlice("Outros", r2(rest))
    }
    return list
}

// ============================================================
// Componentes de interface
// ============================================================

@Composable
fun GSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 24.dp)
                .heightIn(max = 640.dp)
                .clip(shape)
                .background(p.card)
                .border(1.dp, p.border, shape)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = p.muted, fontSize = 18.sp) }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
        }
    }
}

@Composable
fun GField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, color = p.muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.bgSoft)
                .border(1.dp, p.borderSoft, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = p.mutedDim, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                cursorBrush = SolidColor(p.brand),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun GChip(text: String, picked: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .clip(shape)
            .background(p.cardAlt)
            .border(1.dp, if (picked) p.brand else p.border, shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) { Text(text, color = if (picked) p.brand else p.text, fontSize = 11.5.sp) }
}

@Composable
fun GTag(text: String) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .clip(shape)
            .background(p.bgSoft)
            .border(1.dp, p.borderSoft, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(text, color = p.muted, fontSize = 10.sp) }
}

@Composable
fun MiniBtn(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(p.cardAlt)
            .border(1.dp, p.border, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) p.text else p.mutedDim, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun KvRow(k: String, v: String, vColor: Color? = null, bold: Boolean = false) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            v,
            color = vColor ?: p.text,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun GConfirm(msg: String, onYes: () -> Unit, onNo: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = p.card,
        text = { Text(msg, color = p.text, fontSize = 15.sp) },
        confirmButton = { TextButton(onClick = onYes) { Text("Confirmar", color = p.brand) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancelar", color = p.muted) } },
    )
}

@Composable
fun Donut(slices: List<PieSlice>, total: Double, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.16f
            val inset = stroke / 2f
            val dia = size.minDimension - stroke
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = (s.value / total * 360.0).toFloat()
                drawArc(
                    color = PIE_COLORS[i % PIE_COLORS.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(dia, dia),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total", color = p.muted, fontSize = 10.sp)
            Text(brl(total), color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

// ============================================================
// Tela principal da aba Gastos
// ============================================================

@Composable
fun GastosPanel(store: Store) {
    val p = LocalPalette.current
    val ctx = LocalContext.current

    var showExpense by rememberSaveable { mutableStateOf(false) }
    var editExpenseId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showShopAdd by rememberSaveable { mutableStateOf(false) }
    var showProductForm by rememberSaveable { mutableStateOf(false) }
    var productFormId by rememberSaveable { mutableStateOf<Int?>(null) }
    var weighId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editShopId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDiscount by rememberSaveable { mutableStateOf(false) }
    var showFree by rememberSaveable { mutableStateOf(false) }
    var showLookup by rememberSaveable { mutableStateOf(false) }
    var showScan by rememberSaveable { mutableStateOf(false) }
    var formBarcode by rememberSaveable { mutableStateOf("") }
    var formAddToList by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val st = store.current()
    if (st == null) {
        CardBox {
            BoxTitle("Gastos")
            Text(
                "Importe o backup do app antigo (em Configurações) para ver seus gastos.",
                color = p.muted,
                fontSize = 13.sp,
            )
        }
        return
    }

    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }

    val addProduct: (ProdRow) -> Unit = { pr ->
        if (pr.byWeight) {
            weighId = pr.id
        } else {
            store.update { it.addProductToList(pr.id) }
            toast("🛒 ${pr.name} na lista")
        }
    }

    SpendPieCard(store)
    Spacer(Modifier.height(14.dp))

    FixedExpensesCard(
        store,
        onAdd = { editExpenseId = null; showExpense = true },
        onEdit = { id -> editExpenseId = id; showExpense = true },
        onDelete = { id ->
            confirm = "Excluir este gasto fixo?" to { store.update { it.removeExpense(id) } }
        },
    )
    Spacer(Modifier.height(14.dp))

    ShoppingCard(
        store,
        onAddItem = { showShopAdd = true },
        onScan = { showScan = true },
        onLookup = { showLookup = true },
        onDiscount = { showDiscount = true },
        onFree = { showFree = true },
        onEdit = { id -> editShopId = id },
        onDelete = { id ->
            confirm = "Remover este item da lista de compras?" to { store.update { it.removeShopItem(id) } }
        },
    )

    // ---- janelas ----
    if (showExpense) {
        ExpenseSheet(store, editExpenseId) { showExpense = false; editExpenseId = null }
    }
    if (showShopAdd) {
        ShopAddSheet(
            store,
            onClose = { showShopAdd = false },
            onNewProduct = { productFormId = null; showProductForm = true },
            onEditProduct = { id -> productFormId = id; showProductForm = true },
            onDeleteProduct = { id ->
                confirm = "Excluir este produto do catálogo?" to { store.update { it.removeProduct(id) } }
            },
            onAddProduct = addProduct,
        )
    }
    if (showProductForm) {
        ProductFormSheet(store, productFormId, formBarcode, formAddToList) {
            showProductForm = false
            productFormId = null
            formBarcode = ""
            formAddToList = false
        }
    }
    if (showScan) {
        ScanDialog(
            continuous = true,
            onCode = { code ->
                val pr = store.current()?.prodRows()?.firstOrNull { it.barcode.isNotEmpty() && it.barcode == code }
                when {
                    pr == null -> {
                        formBarcode = code
                        formAddToList = true
                        productFormId = null
                        showProductForm = true
                        ScanAction("Produto novo — cadastre pra adicionar", close = true)
                    }
                    pr.byWeight -> {
                        weighId = pr.id
                        ScanAction(close = true)
                    }
                    else -> {
                        store.update { it.addProductToList(pr.id) }
                        val q = store.current()?.objList("shoppingList")?.firstOrNull { it.optInt("productId", -1) == pr.id }?.optInt("quantity", 1) ?: 1
                        ScanAction("✓ ${pr.name} · $q un na lista")
                    }
                }
            },
            onClose = { showScan = false },
        )
    }
    weighId?.let { id -> WeighSheet(store, id) { weighId = null } }
    editShopId?.let { id -> EditShopSheet(store, id) { editShopId = null } }
    if (showDiscount) DiscountSheet(store) { showDiscount = false }
    if (showFree) FreeSpendSheet(store) { showFree = false }
    if (showLookup) {
        LookupSheet(store, onAdd = { pr -> showLookup = false; addProduct(pr) }) { showLookup = false }
    }

    confirm?.let { (msg, run) ->
        GConfirm(
            msg,
            onYes = { run(); confirm = null },
            onNo = { confirm = null },
        )
    }
}

// ---------------- gráfico de pizza ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendPieCard(store: Store) {
    val p = LocalPalette.current
    var mode by rememberSaveable { mutableStateOf("real") }
    var pieYm by rememberSaveable { mutableStateOf<String?>(null) }
    val st = store.current() ?: return

    val cur = st.optString("simDateISO", todayISO()).take(7)
    val ym = pieYm?.takeIf { it <= cur } ?: cur
    val first = st.firstStatMonth() ?: cur
    val slices = st.pieSlices(mode, ym)
    val total = slices.sumOf { it.value }

    CardBox {
        BoxTitle("Para onde vai o dinheiro", "Gastos por categoria")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GChip("Realizado no mês", mode == "real") { mode = "real" }
            GChip("Gastos fixos planejados", mode == "plan") { mode = "plan" }
        }
        if (mode == "real") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiniBtn("‹", enabled = ym > first) { pieYm = shiftYm(ym, -1) }
                Text(
                    ymLabel(ym),
                    color = p.text,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MiniBtn("›", enabled = ym < cur) { pieYm = shiftYm(ym, 1) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (total <= 0.0) {
            Text(
                if (mode == "real") {
                    "Sem gastos registrados neste mês ainda — os gastos fixos caem 1 dia após o salário. Veja o \"planejado\" enquanto isso."
                } else {
                    "Cadastre gastos fixos pra ver a divisão."
                },
                color = p.muted,
                fontSize = 13.sp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(slices, total, Modifier.size(130.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    slices.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(PIE_COLORS[i % PIE_COLORS.size]))
                            Spacer(Modifier.width(7.dp))
                            Text(s.label, color = p.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("${Math.round(s.value / total * 100)}%", color = p.muted, fontSize = 12.sp)
                        }
                        Text(
                            brl(s.value),
                            color = p.mutedDim,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 17.dp, bottom = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------- gastos fixos ----------------

@Composable
fun FixedExpensesCard(store: Store, onAdd: () -> Unit, onEdit: (Int) -> Unit, onDelete: (Int) -> Unit) {
    val p = LocalPalette.current
    val st = store.current() ?: return
    val rows = st.expRows()
    val linked = st.optInt("shoppingLinkedExpenseId", -1)
    val total = rows.sumOf { it.amount }

    CardBox {
        BoxTitle("Gastos fixos mensais", "Toque nos três pontinhos pra editar ou excluir uma categoria")
        KvRow("Total dos gastos fixos", brl(total), p.gold, bold = true)
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            Text("Nenhum gasto fixo cadastrado.", color = p.muted, fontSize = 13.sp)
        } else {
            rows.forEachIndexed { i, e ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.borderSoft))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = p.text, fontSize = 14.sp)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GTag(e.category)
                            if (e.id == linked) {
                                Spacer(Modifier.width(6.dp))
                                Text("vem da lista de compras", color = p.mutedDim, fontSize = 10.sp)
                            }
                        }
                    }
                    Text(brl(e.amount), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (e.id == linked) {
                        Spacer(Modifier.width(40.dp))
                    } else {
                        RowMenu(
                            listOf(
                                MenuAction("✏️ Editar") { onEdit(e.id) },
                                MenuAction("🗑 Excluir", danger = true) { onDelete(e.id) },
                            ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("+ Adicionar gastos", onClick = onAdd)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseSheet(store: Store, editId: Int?, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val existing = editId?.let { id -> store.current()?.expRows()?.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.let { numText(it.amount) } ?: "") }
    var cat by remember { mutableStateOf(existing?.category ?: "Outros") }

    GSheet(if (existing != null) "Editar gasto fixo" else "Adicionar gasto fixo", onClose) {
        if (existing == null) {
            Text(
                "Escolha uma opção do catálogo ou digite um gasto seu — dá pra editar nome, valor e categoria antes de adicionar.",
                color = p.muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EXPENSE_CATALOG.forEach { c ->
                    GChip(c.name, picked = name == c.name) {
                        name = c.name
                        amount = numText(c.value)
                        cat = c.category
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        GField("Gasto", name, { name = it }, placeholder = "Ex.: Aluguel")
        Spacer(Modifier.height(8.dp))
        GField("Valor (R$)", amount, { amount = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        Text("Categoria", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (EXPENSE_CATEGORIES + cat).distinct().forEach { c -> GChip(c, picked = cat == c) { cat = c } }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (existing != null) "Salvar" else "Adicionar gasto") {
            val v = parseNum(amount)
            if (name.isBlank() || v <= 0.0) {
                Toast.makeText(ctx, "Preencha o nome e um valor maior que zero", Toast.LENGTH_SHORT).show()
            } else {
                if (existing != null) {
                    store.update { it.editExpense(existing.id, name.trim(), v, cat) }
                } else {
                    store.update { it.addExpense(name.trim(), v, cat) }
                }
                onClose()
            }
        }
    }
}

// ---------------- lista de compras ----------------

@Composable
fun ShoppingCard(
    store: Store,
    onAddItem: () -> Unit,
    onScan: () -> Unit,
    onLookup: () -> Unit,
    onDiscount: () -> Unit,
    onFree: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val p = LocalPalette.current
    val st = store.current() ?: return
    val rows = st.shopRows()

    CardBox {
        BoxTitle("Lista de compras", "Fica salva de um mês pro outro · o total vira o gasto fixo \"Alimentação\"")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("+ Adicionar item", Modifier.weight(1f), onAddItem)
            GhostButton("📷 Escanear", Modifier.weight(1f), onScan)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("📦 Catálogo", Modifier.weight(1f), onAddItem)
            GhostButton("↺ Lista nova", Modifier.weight(1f)) { store.update { it.resetShoppingChecks() } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("🔎 Consultar preço", Modifier.weight(1f), onLookup)
            GhostButton("🏷 Desconto", Modifier.weight(1f), onDiscount)
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("🎉 Gasto livre", onClick = onFree)
        Spacer(Modifier.height(12.dp))

        if (rows.isEmpty()) {
            Text(
                "Lista vazia. Adicione itens do catálogo, ou digite um item avulso.",
                color = p.muted,
                fontSize = 13.sp,
            )
        } else {
            val subAll = st.shopSub(false)
            val disc = st.shopDiscountFor(subAll)
            val sd = st.optJSONObject("shoppingDiscount")
            val discLabel = if (sd?.optString("type") == "valor") {
                "DESCONTO"
            } else {
                "DESCONTO (${numText(sd?.optDouble("value", 0.0) ?: 0.0)}%)"
            }
            val mono = FontFamily.Monospace
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(p.bgSoft)
                    .border(1.dp, p.borderSoft, shape)
                    .padding(12.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("LISTA DE COMPRAS", color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = mono, modifier = Modifier.weight(1f))
                    Text(ddmmyyyy(st.optString("simDateISO", todayISO())), color = p.muted, fontSize = 12.sp, fontFamily = mono)
                }
                Text("DOCUMENTO NÃO FISCAL · USO PESSOAL", color = p.mutedDim, fontSize = 10.sp, fontFamily = mono)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                rows.forEachIndexed { idx, r ->
                    ReceiptItem(
                        idx = idx,
                        r = r,
                        onToggle = { store.update { it.toggleShopItem(r.id) } },
                        onQty = { d -> store.update { it.changeShopQty(r.id, d) } },
                        onEdit = { onEdit(r.id) },
                        onDelete = { onDelete(r.id) },
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                Spacer(Modifier.height(6.dp))
                ReceiptTotal("QTD. TOTAL DE ITENS", rows.sumOf { it.qty }.toString())
                ReceiptTotal("NO CARRINHO (MARCADOS)", "R$ " + money2(st.shopCartTotal()))
                if (disc > 0.0) {
                    ReceiptTotal("SUBTOTAL", "R$ " + money2(subAll))
                    ReceiptTotal(discLabel, "− R$ " + money2(disc))
                }
                ReceiptTotal("VALOR TOTAL", "R$ " + money2(st.shopFullTotal()), big = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    "*** BOM MERCADO! ***",
                    color = p.mutedDim,
                    fontSize = 11.sp,
                    fontFamily = mono,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Dica: marque o item quando colocar no carrinho; o ⋮ edita ou remove.", color = p.mutedDim, fontSize = 11.sp)
        }
    }
}

@Composable
fun ReceiptTotal(k: String, v: String, big: Boolean = false) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            k,
            color = if (big) p.text else p.muted,
            fontSize = if (big) 14.sp else 12.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            v,
            color = if (big) p.gold else p.text,
            fontSize = if (big) 15.sp else 12.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun ReceiptItem(
    idx: Int,
    r: ShopRow,
    onToggle: () -> Unit,
    onQty: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    val mono = FontFamily.Monospace
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = r.checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = p.brand,
                    uncheckedColor = p.mutedDim,
                    checkmarkColor = p.brandInk,
                ),
            )
            Text(String.format("%03d", idx + 1), color = p.mutedDim, fontSize = 11.sp, fontFamily = mono)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.name,
                    color = if (r.checked) p.mutedDim else p.text,
                    fontSize = 14.sp,
                    textDecoration = if (r.checked) TextDecoration.LineThrough else TextDecoration.None,
                )
                val meta = r.category + (if (r.variant.isNotEmpty()) " · ${r.variant}" else "") +
                    (if (r.barcode.isNotEmpty()) " · ${r.barcode}" else "")
                Text(meta, color = p.mutedDim, fontSize = 10.5.sp)
            }
            RowMenu(
                listOf(
                    MenuAction("✏️ Editar") { onEdit() },
                    MenuAction("🗑 Excluir", danger = true) { onDelete() },
                ),
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.weightKg > 0.0) {
                Text("${fmtKg(r.weightKg)} x ${money2(r.pricePerKg)}/kg", color = p.muted, fontSize = 12.sp, fontFamily = mono)
            } else {
                MiniBtn("−") { onQty(-1) }
                Text(
                    r.qty.toString(),
                    color = p.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(30.dp),
                )
                MiniBtn("+") { onQty(1) }
                Spacer(Modifier.width(8.dp))
                Text("UN x ${money2(r.price)}", color = p.muted, fontSize = 12.sp, fontFamily = mono)
            }
            Spacer(Modifier.weight(1f))
            Text(money2(r.price * r.qty), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = mono)
        }
    }
}

// ---------------- catálogo de produtos e item avulso ----------------

@Composable
fun ShopAddSheet(
    store: Store,
    onClose: () -> Unit,
    onNewProduct: () -> Unit,
    onEditProduct: (Int) -> Unit,
    onDeleteProduct: (Int) -> Unit,
    onAddProduct: (ProdRow) -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var term by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }

    val all = st?.prodRows() ?: emptyList()
    val t = term.trim().lowercase()
    val shown = all.filter { t.isEmpty() || it.name.lowercase().contains(t) || it.barcode.contains(t) }

    GSheet("Adicionar à lista", onClose) {
        GField("", term, { term = it }, placeholder = "Buscar no catálogo...")
        Spacer(Modifier.height(8.dp))
        if (all.isEmpty()) {
            Text(
                "Seu catálogo está vazio. Cadastre produtos (com código de barras, se quiser) ou use \"Produtos comuns\".",
                color = p.muted,
                fontSize = 13.sp,
            )
        } else if (shown.isEmpty()) {
            Text("Nenhum produto encontrado.", color = p.muted, fontSize = 13.sp)
        } else {
            shown.forEachIndexed { i, pr ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.borderSoft))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pr.name, color = p.text, fontSize = 14.sp)
                        val meta = pr.category + (if (pr.byWeight) " · ⚖️ por peso" else "") +
                            (if (pr.variant.isNotEmpty()) " · ${pr.variant}" else "") +
                            (if (pr.barcode.isNotEmpty()) " · ${pr.barcode}" else "")
                        Text(meta, color = p.mutedDim, fontSize = 10.5.sp)
                    }
                    Text(
                        brl(pr.price) + if (pr.byWeight) "/kg" else "",
                        color = p.gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    MiniBtn("+") { onAddProduct(pr) }
                    RowMenu(
                        listOf(
                            MenuAction("✏️ Editar") { onEditProduct(pr.id) },
                            MenuAction("🗑 Excluir", danger = true) { onDeleteProduct(pr.id) },
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("+ Cadastrar produto", Modifier.weight(1f), onNewProduct)
            GhostButton("Produtos comuns", Modifier.weight(1f)) {
                store.update { it.seedProducts() }
                Toast.makeText(ctx, "Produtos comuns adicionados — ajuste os preços do seu mercado", Toast.LENGTH_LONG).show()
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Item avulso (sem cadastrar)", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        GField("Item", name, { name = it }, placeholder = "Ex.: sabão em pó")
        Spacer(Modifier.height(8.dp))
        GField("Categoria", cat, { cat = it }, placeholder = "Ex.: limpeza")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GField("Qtd.", qty, { qty = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            GField("Preço unit. (R$)", price, { price = it }, Modifier.weight(1.5f), keyboard = KeyboardType.Decimal)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Adicionar item") {
            if (name.isBlank()) {
                Toast.makeText(ctx, "Digite o nome do item", Toast.LENGTH_SHORT).show()
            } else {
                val q = qty.toIntOrNull() ?: 1
                val pr = parseNum(price)
                store.update { it.addShopItem(name.trim(), cat.trim(), pr, q) }
                onClose()
            }
        }
    }
}

@Composable
fun ProductFormSheet(store: Store, editId: Int?, initialBarcode: String = "", addToList: Boolean = false, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val existing = editId?.let { id -> store.current()?.prodRows()?.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var cat by remember { mutableStateOf(existing?.category ?: "") }
    var price by remember { mutableStateOf(existing?.let { numText(it.price) } ?: "") }
    var byWeight by remember { mutableStateOf(existing?.byWeight ?: false) }
    var barcode by remember { mutableStateOf(existing?.barcode ?: initialBarcode) }
    var scanning by remember { mutableStateOf(false) }
    var brand by remember { mutableStateOf(existing?.brand ?: "") }
    var variant by remember { mutableStateOf(existing?.variant ?: "") }
    var more by remember { mutableStateOf(existing != null && (existing.brand.isNotEmpty() || existing.variant.isNotEmpty())) }

    LaunchedEffect(barcode) {
        if (barcode.trim().length >= 8 && name.isBlank()) {
            fetchProductInfo(barcode.trim()) { n, b ->
                if (name.isBlank()) {
                    name = n
                    if (brand.isBlank() && b.isNotEmpty()) brand = b
                    Toast.makeText(ctx, "Nome preenchido pelo código de barras", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    GSheet(if (existing != null) "Editar produto" else "Cadastrar produto", onClose) {
        GField("Nome do produto", name, { name = it }, placeholder = "Ex.: Arroz 5kg")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GField("Categoria", cat, { cat = it }, Modifier.weight(1.3f), placeholder = "Ex.: mercearia")
            GField(
                if (byWeight) "Preço por kg (R$)" else "Preço (R$)",
                price,
                { price = it },
                Modifier.weight(1f),
                keyboard = KeyboardType.Decimal,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clickable { byWeight = !byWeight }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = byWeight,
                onCheckedChange = { byWeight = it },
                colors = CheckboxDefaults.colors(checkedColor = p.brand, uncheckedColor = p.mutedDim, checkmarkColor = p.brandInk),
            )
            Text("⚖️ Vendido por peso (o preço é por kg)", color = p.text, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            GField("Código de barras", barcode, { barcode = it }, Modifier.weight(1f), placeholder = "Digite ou escaneie", keyboard = KeyboardType.Number)
            GhostButton("📷", Modifier.width(60.dp)) { scanning = true }
        }
        if (scanning) {
            ScanDialog(false, { code -> barcode = code; ScanAction(close = true) }, { scanning = false })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (more) "− Menos detalhes" else "+ Mais detalhes (marca, variação)",
            color = p.brand,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { more = !more }.padding(vertical = 6.dp),
        )
        if (more) {
            GField("Marca", brand, { brand = it }, placeholder = "Ex.: Tio João")
            Spacer(Modifier.height(8.dp))
            GField("Variação (tamanho, cor, sabor)", variant, { variant = it }, placeholder = "Ex.: 5kg, integral")
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Salvar produto") {
            if (name.isBlank()) {
                Toast.makeText(ctx, "Digite o nome do produto", Toast.LENGTH_SHORT).show()
            } else {
                store.update {
                    val newId = it.saveProduct(
                        editId,
                        name.trim(),
                        cat.trim().ifEmpty { "Geral" },
                        Math.max(0.0, parseNum(price)),
                        barcode.trim(),
                        brand.trim(),
                        variant.trim(),
                        byWeight,
                    )
                    if (addToList && editId == null && !byWeight) it.addProductToList(newId)
                }
                onClose()
            }
        }
    }
}

@Composable
fun WeighSheet(store: Store, productId: Int, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val prod = store.current()?.prodRows()?.firstOrNull { it.id == productId } ?: return
    var text by remember { mutableStateOf("") }
    var unitG by remember { mutableStateOf(false) }

    val typed = parseNum(text)
    val kg = if (unitG) typed / 1000.0 else typed
    val valor = r2(prod.price * kg)

    GSheet(prod.name, onClose) {
        Text("${brl(prod.price)} por kg · digite o peso da balança", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            GField("Peso", text, { text = it }, Modifier.weight(1.4f), placeholder = "Ex.: 0,350", keyboard = KeyboardType.Decimal)
            if (!unitG) PrimaryButton("kg", Modifier.weight(0.5f)) { unitG = false } else GhostButton("kg", Modifier.weight(0.5f)) { unitG = false }
            if (unitG) PrimaryButton("g", Modifier.weight(0.5f)) { unitG = true } else GhostButton("g", Modifier.weight(0.5f)) { unitG = true }
        }
        Spacer(Modifier.height(10.dp))
        KvRow("Valor", brl(valor), p.gold, bold = true)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Adicionar à lista") {
            if (kg <= 0.0) {
                Toast.makeText(ctx, "Digite o peso do produto", Toast.LENGTH_SHORT).show()
            } else {
                store.update { it.addWeighed(prod.id, kg) }
                Toast.makeText(ctx, "🛒 ${prod.name} · ${fmtKg(Math.round(kg * 1000.0) / 1000.0)} na lista", Toast.LENGTH_SHORT).show()
                onClose()
            }
        }
    }
}

@Composable
fun EditShopSheet(store: Store, id: Int, onClose: () -> Unit) {
    val row = store.current()?.shopRows()?.firstOrNull { it.id == id } ?: return
    val isWeight = row.weightKg > 0.0
    var name by remember { mutableStateOf(row.name) }
    var cat by remember { mutableStateOf(row.category) }
    var kg by remember { mutableStateOf(numText(row.weightKg)) }
    var qty by remember { mutableStateOf(row.qty.toString()) }
    var price by remember { mutableStateOf(numText(row.price)) }

    GSheet("Editar item", onClose) {
        GField("Item", name, { name = it })
        Spacer(Modifier.height(8.dp))
        GField("Categoria", cat, { cat = it })
        Spacer(Modifier.height(8.dp))
        if (isWeight) {
            GField("Peso (kg)", kg, { kg = it }, keyboard = KeyboardType.Decimal)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GField("Qtd.", qty, { qty = it }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                GField("Preço unit. (R$)", price, { price = it }, Modifier.weight(1.5f), keyboard = KeyboardType.Decimal)
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Salvar") {
            store.update { it.saveEditShop(id, name, cat, kg, qty, price) }
            onClose()
        }
    }
}

@Composable
fun DiscountSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val d = store.current()?.optJSONObject("shoppingDiscount")
    var type by remember { mutableStateOf(if (d?.optString("type") == "valor") "valor" else "pct") }
    var value by remember {
        val v = d?.optDouble("value", 0.0) ?: 0.0
        mutableStateOf(if (v > 0.0) numText(v) else "")
    }

    GSheet("Desconto na lista", onClose) {
        Text("O desconto é dividido proporcionalmente entre os itens e já entra no valor total.", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (type == "pct") PrimaryButton("Porcentagem (%)", Modifier.weight(1f)) { type = "pct" } else GhostButton("Porcentagem (%)", Modifier.weight(1f)) { type = "pct" }
            if (type == "valor") PrimaryButton("Valor fixo (R$)", Modifier.weight(1f)) { type = "valor" } else GhostButton("Valor fixo (R$)", Modifier.weight(1f)) { type = "valor" }
        }
        Spacer(Modifier.height(10.dp))
        GField(if (type == "pct") "Desconto (%)" else "Desconto (R$)", value, { value = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Aplicar desconto") {
            store.update { it.applyShopDiscount(type, parseNum(value)) }
            onClose()
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("Remover desconto") {
            store.update { it.applyShopDiscount("pct", 0.0) }
            onClose()
        }
    }
}

@Composable
fun LookupSheet(store: Store, onAdd: (ProdRow) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    val prods = store.current()?.prodRows() ?: emptyList()
    val found = if (code.length >= 3) prods.firstOrNull { it.barcode.isNotEmpty() && it.barcode == code.trim() } else null

    GSheet("Consultar preço", onClose) {
        Text("Digite o código de barras para ver o preço no seu catálogo.", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        GField("Código de barras", code, { code = it }, placeholder = "Ex.: 7891000100103", keyboard = KeyboardType.Number)
        Spacer(Modifier.height(8.dp))
        GhostButton("📷 Escanear") { scanning = true }
        if (scanning) {
            ScanDialog(false, { c -> code = c; ScanAction(close = true) }, { scanning = false })
        }
        Spacer(Modifier.height(12.dp))
        if (found != null) {
            KvRow(found.name, brl(found.price) + if (found.byWeight) "/kg" else "", p.gold, bold = true)
            Text(found.category + (if (found.variant.isNotEmpty()) " · ${found.variant}" else ""), color = p.mutedDim, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("+ Adicionar à lista") { onAdd(found) }
        } else if (code.trim().length >= 8) {
            Text("O código ${code.trim()} não está no seu catálogo. Cadastre o produto em Adicionar item → Cadastrar produto.", color = p.muted, fontSize = 13.sp)
        }
    }
}

// ---------------- gasto livre ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FreeSpendSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    val cats = st?.freeCats() ?: listOf("Outro")
    var cat by remember { mutableStateOf(cats.firstOrNull() ?: "Outro") }
    var amount by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf(numText(st?.optDouble("freeSpendMonthlyLimit", 0.0) ?: 0.0)) }

    GSheet("Gasto livre", onClose) {
        Text("O que sai do bolso fora do orçamento fixo — festas, imprevistos etc.", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text("Categoria", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { c -> GChip(c, picked = cat == c) { cat = c } }
        }
        Spacer(Modifier.height(10.dp))
        GField("Valor (R$)", amount, { amount = it }, placeholder = "Valor do gasto", keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Registrar gasto") {
            val v = parseNum(amount)
            if (v <= 0.0) {
                Toast.makeText(ctx, "Digite o valor do gasto", Toast.LENGTH_SHORT).show()
            } else {
                var actual = 0.0
                store.update { actual = it.spendFree(v, cat) }
                if (actual > 0.0) {
                    Toast.makeText(ctx, "Gasto registrado · ${brl(actual)} · $cat", Toast.LENGTH_LONG).show()
                    onClose()
                } else {
                    Toast.makeText(ctx, "Sem saldo na conta para este gasto", Toast.LENGTH_LONG).show()
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Spacer(Modifier.height(12.dp))
        GField("Limite mensal de gasto livre (0 = sem limite)", limitText, { limitText = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        GhostButton("Salvar limite") {
            val l = Math.max(0.0, parseNum(limitText))
            store.update { it.put("freeSpendMonthlyLimit", l) }
            Toast.makeText(ctx, "Limite salvo", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(12.dp))

        val cur = store.current()
        if (cur != null) {
            val spent = cur.freeSpentThisMonth()
            val limit = cur.optDouble("freeSpendMonthlyLimit", 0.0)
            if (limit > 0.0) {
                val frac = Math.min(1.0, Math.max(0.0, spent / limit))
                val over = spent > limit
                val notify = cur.optJSONObject("settings")?.optDouble("notifyFreeSpendPct", 90.0) ?: 90.0
                val near = !over && spent / limit * 100.0 >= notify
                val barColor = if (over) p.coral else p.gold
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.borderSoft)) {
                    Box(Modifier.fillMaxWidth(frac.toFloat()).fillMaxHeight().background(barColor))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("${brl(spent)} de ${brl(limit)} este mês", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${Math.round(frac * 100)}%", color = p.muted, fontSize = 12.sp)
                }
                if (over) {
                    Spacer(Modifier.height(6.dp))
                    Text("Você passou do limite de gasto livre deste mês em ${brl(spent - limit)}.", color = p.coral, fontSize = 12.sp)
                } else if (near) {
                    Spacer(Modifier.height(6.dp))
                    Text("Você já usou ${Math.round(frac * 100)}% do limite de gasto livre deste mês.", color = p.gold, fontSize = 12.sp)
                }
            } else {
                Text("Gasto livre este mês: ${brl(spent)} (defina um limite acima pra acompanhar)", color = p.muted, fontSize = 12.sp)
            }
            val byCat = cur.freeSpentByCategory().filter { it.value > 0.0 }
            if (byCat.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                byCat.forEach { (k, v) -> KvRow(k, brl(v)) }
            }
        }
    }
}
