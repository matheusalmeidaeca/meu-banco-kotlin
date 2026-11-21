package com.meubanco.patrimonio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.MarketApi
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.launch

@Composable
fun GastosScreen() {
    val s = state()
    var sub by remember { mutableStateOf("fixos") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChipRow(listOf("fixos" to "Gastos fixos", "livre" to "Gasto livre", "orcamento" to "Orçamento", "assinaturas" to "Assinaturas", "compras" to "Lista de compras", "dividir" to "Dividir contas", "produtos" to "Produtos"), sub) { sub = it }
        when (sub) {
            "fixos" -> FixosSection()
            "livre" -> LivreSection()
            "dividir" -> DividirBlock()
            "orcamento" -> OrcamentoBlock()
            "assinaturas" -> AssinaturasBlock()
            "compras" -> ComprasSection()
            else -> ProdutosSection()
        }
    }
}

@Composable
private fun FixosSection() {
    val s = state()
    var add by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf(false) }
    Card {
        H2("Gastos fixos do mês")
        KV("Total", money(s.expenses.sumOf { it.amount }), P.red)
        Muted("Pagos automaticamente no dia seguinte ao salário.")
    }
    s.expenses.forEach { e ->
        var edit by remember { mutableStateOf(false) }
        Card(onClick = { edit = true }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Txt(e.name, bold = true); Muted(e.category + if (e.id == s.shoppingLinkedExpenseId) " · ligado à lista de compras" else "") }
                Txt(money(e.amount), bold = true)
            }
        }
        if (edit) {
            var name by remember { mutableStateOf(e.name) }; var amt by remember { mutableStateOf<Double?>(e.amount) }; var cat by remember { mutableStateOf(e.category) }
            FormDialog("Editar gasto", { edit = false }, "Salvar", {
                Store.mutate { e.name = name.ifBlank { e.name }; e.amount = amt ?: 0.0; e.category = cat }; true
            }) {
                Field(name, { name = it }, "Nome")
                if (e.id != s.shoppingLinkedExpenseId) NumField(amt, { amt = it }, "Valor mensal", suffix = "R$")
                else Muted("O valor vem da sua lista de compras.")
                ChipRow(EXPENSE_CATEGORIES.map { it to it }, cat) { cat = it }
                if (e.id != s.shoppingLinkedExpenseId) GhostButton("Excluir gasto", danger = true) { Store.mutate { removeExpense(it, e.id) }; edit = false }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("＋ Novo gasto", Modifier.weight(1f)) { add = true }
        GhostButton("Catálogo", Modifier.weight(1f)) { catalog = true }
    }
    if (add) {
        var name by remember { mutableStateOf("") }; var amt by remember { mutableStateOf<Double?>(null) }; var cat by remember { mutableStateOf("Contas") }
        FormDialog("Novo gasto fixo", { add = false }, "Adicionar", {
            if (name.isBlank()) false else { Store.mutate { addExpense(it, name.trim(), amt ?: 0.0, cat) }; true }
        }) {
            Field(name, { name = it }, "Nome"); NumField(amt, { amt = it }, "Valor mensal", suffix = "R$")
            ChipRow(EXPENSE_CATEGORIES.map { it to it }, cat) { cat = it }
        }
    }
    if (catalog) Sheet("Catálogo de gastos", { catalog = false }) {
        EXPENSE_CATALOG.forEach { c ->
            val has = s.expenses.any { it.name == c.name }
            Row(Modifier.fillMaxWidth().clickable(enabled = !has) { Store.mutate { addExpense(it, c.name, c.value, c.category) }; catalog = false },
                horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(c.name + if (has) " ✓" else "", color = if (has) P.muted else P.text); Muted(c.category)
            }
        }
    }
}

@Composable
private fun LivreSection() {
    val s = state()
    var spend by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf(false) }
    val used = freeSpendThisMonth(s)
    Card {
        H2("Gasto livre do mês")
        Txt(money(used), style = T.title, bold = true)
        if (s.freeSpendMonthlyLimit > 0) { Progress(used / s.freeSpendMonthlyLimit, if (used > s.freeSpendMonthlyLimit) P.red else P.green); Muted("Limite ${formatBRL(s.freeSpendMonthlyLimit)}") }
        PrimaryButton("Registrar gasto") { spend = true }
        var lim by remember { mutableStateOf<Double?>(s.freeSpendMonthlyLimit) }
        NumField(lim, { lim = it; Store.mutate { st -> st.freeSpendMonthlyLimit = it ?: 0.0 } }, "Limite mensal (0 = sem limite)", suffix = "R$")
    }
    val byCat = freeSpendThisMonthByCategory(s).filter { it.value > 0 }.toList()
    if (byCat.isNotEmpty()) Card { H2("Por categoria"); PieWithLegend(byCat.sortedByDescending { it.second }) }
    Card {
        H2("Categorias")
        ChipRow(s.freeSpendCategories.map { it to it }, "") { n -> handle(Store.mutate { removeFreeSpendCategory(it, n) }) }
        Muted("Toque numa categoria para removê-la.")
        GhostButton("＋ Nova categoria") { newCat = true }
    }
    if (spend) {
        var v by remember { mutableStateOf<Double?>(null) }; var cat by remember { mutableStateOf(s.freeSpendCategories.firstOrNull() ?: "Outro") }
        FormDialog("Gasto livre", { spend = false }, "Registrar", {
            handle(Store.mutate { spendFree(it, v ?: 0.0, cat) })
        }) {
            NumField(v, { v = it }, "Valor", suffix = "R$")
            ChipRow(s.freeSpendCategories.map { it to it }, cat) { cat = it }
            Muted("Saldo: ${formatBRL(s.cash)}")
        }
    }
    if (newCat) {
        var n by remember { mutableStateOf("") }
        FormDialog("Nova categoria", { newCat = false }, "Adicionar", { if (n.isBlank()) false else { Store.mutate { addFreeSpendCategory(it, n.trim()) }; true } }) { Field(n, { n = it }, "Nome") }
    }
}

@Composable
private fun ComprasSection() {
    val s = state()
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf(false) }
    var discount by remember { mutableStateOf(false) }
    var weigh by remember { mutableStateOf<Product?>(null) }
    var unknown by remember { mutableStateOf<String?>(null) }

    val sub = shoppingListFullTotal(s)
    Card {
        H2("🛒 Lista de compras")
        KV("Subtotal", money(sub))
        if (shoppingDiscountFor(s, sub) > 0) KV("Desconto", "− " + money(shoppingDiscountFor(s, sub)), P.green)
        KV("Total", money(shoppingTotal(s)), P.gold)
        Muted("O total alimenta o gasto fixo \"Alimentação\".")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("＋ Item", Modifier.weight(1f)) { add = true }
            GhostButton("Produtos", Modifier.weight(1f)) { pick = true }
        }
        val ctxShare = androidx.compose.ui.platform.LocalContext.current
        GhostButton("Compartilhar lista", Modifier.fillMaxWidth()) { shareText(ctxShare, shoppingShareText(s), "Lista de compras") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("📷 Escanear", Modifier.weight(1f)) { scan = true }
            GhostButton("Desconto", Modifier.weight(1f)) { discount = true }
            GhostButton("Limpar ✓", Modifier.weight(1f)) { Store.mutate { resetShoppingChecks(it) } }
        }
    }
    if (s.shoppingList.isNotEmpty()) {
        val mono = androidx.compose.ui.text.font.FontFamily.Monospace
        Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(if (P.dark) P.card2 else androidx.compose.ui.graphics.Color(0xFFFFFBEF)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val ink = if (P.dark) P.text else androidx.compose.ui.graphics.Color(0xFF2B2B2B)
            Text("NOTA DE COMPRAS", color = ink, fontFamily = mono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
            Text("- - - - - - - - - - - - - - - - - -", color = P.muted, fontFamily = mono, fontSize = 12.sp)
            s.shoppingList.forEach { it ->
                Row(Modifier.fillMaxWidth().clickable { Store.mutate { st -> toggleShopItem(st, it.id) } }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (it.checked) "[x]" else "[ ]", color = ink, fontFamily = mono, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (it.weightKg != null) "${fmtPlain(it.weightKg!!)}kg " else "${it.quantity}x ") + it.name + (if (it.variant.isNotBlank()) " (${it.variant})" else ""),
                            color = if (it.checked) P.muted else ink, fontFamily = mono, fontSize = 13.sp,
                            textDecoration = if (it.checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        if (it.category.isNotBlank() && it.category != "Geral") Text(it.category, color = P.muted, fontFamily = mono, fontSize = 10.sp)
                    }
                    Text(if (state().settings.hideBalance) "•••" else formatBRL(if (it.weightKg != null) it.price else it.price * it.quantity),
                        color = ink, fontFamily = mono, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (it.weightKg == null) {
                        TextButton({ Store.mutate { st -> changeShopQty(st, it.id, -1) } }) { Text("−", color = P.brand) }
                        TextButton({ Store.mutate { st -> changeShopQty(st, it.id, 1) } }) { Text("+", color = P.brand) }
                    }
                    TextButton({ Store.mutate { st -> removeShopItem(st, it.id) } }) { Text("remover", color = P.red, fontSize = 11.sp) }
                }
            }
            Text("- - - - - - - - - - - - - - - - - -", color = P.muted, fontFamily = mono, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL", color = ink, fontFamily = mono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(if (state().settings.hideBalance) "•••" else formatBRL(shoppingTotal(s)), color = ink, fontFamily = mono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }

    if (add) {
        var name by remember { mutableStateOf("") }; var price by remember { mutableStateOf<Double?>(null) }; var qty by remember { mutableStateOf(1.0) }
        var cat by remember { mutableStateOf("Geral") }
        FormDialog("Novo item", { add = false }, "Adicionar", {
            if (name.isBlank()) false else { Store.mutate { addShopItem(it, name.trim(), cat, price ?: 0.0, qty.toInt().coerceAtLeast(1)) }; true }
        }) {
            Field(name, { name = it }, "Item"); NumField(price, { price = it }, "Preço unitário", suffix = "R$")
            NumField(qty, { qty = it ?: 1.0 }, "Quantidade", decimals = false)
            Field(cat, { cat = it }, "Categoria")
        }
    }
    if (pick) Sheet("Escolha um produto", { pick = false }) {
        if (s.products.isEmpty()) {
            Muted("Nenhum produto cadastrado.")
            GhostButton("Carregar produtos comuns") { Store.mutate { seedCommonProducts(it) } }
        }
        s.products.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable {
                if (p.byWeight) { weigh = p; pick = false } else { Store.mutate { st -> addProductToList(st, p.id) }; pick = false }
            }, horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(p.name + if (p.byWeight) " (kg)" else ""); Muted(formatBRL(p.price))
            }
        }
    }
    weigh?.let { p ->
        var kg by remember { mutableStateOf<Double?>(null) }
        FormDialog("Pesar ${p.name}", { weigh = null }, "Adicionar", {
            if ((kg ?: 0.0) <= 0) false else { Store.mutate { addWeighedProduct(it, p.id, kg!!) }; true }
        }) { NumField(kg, { kg = it }, "Peso", suffix = "kg"); Muted("${formatBRL(p.price)} por kg") }
    }
    if (discount) {
        var type by remember { mutableStateOf(s.shoppingDiscount.type) }; var v by remember { mutableStateOf<Double?>(s.shoppingDiscount.value) }
        FormDialog("Desconto na compra", { discount = false }, "Aplicar", {
            Store.mutate { st -> st.shoppingDiscount.type = type; st.shoppingDiscount.value = v ?: 0.0; syncShoppingToFoodExpense(st) }; true
        }) {
            ChipRow(listOf("pct" to "Percentual %", "val" to "Valor R$"), type) { type = it }
            NumField(v, { v = it }, if (type == "pct") "Desconto" else "Valor", suffix = if (type == "pct") "%" else "R$")
        }
    }
    if (scan) BarcodeScannerDialog({ code ->
        scan = false
        val known = s.products.find { it.barcode == code }
        if (known != null) {
            if (known.byWeight) weigh = known else { Store.mutate { addProductToList(it, known.id) }; Ui.show("Adicionado: ${known.name}") }
        } else unknown = code
    }, { scan = false })
    unknown?.let { code ->
        var name by remember { mutableStateOf("") }; var brand by remember { mutableStateOf("") }; var price by remember { mutableStateOf<Double?>(null) }
        var loading by remember { mutableStateOf(true) }
        LaunchedEffect(code) {
            MarketApi.lookupBarcode(code)?.let { (n, b) -> name = n; brand = b }
            loading = false
        }
        FormDialog("Produto novo ($code)", { unknown = null }, "Salvar e adicionar", {
            if (name.isBlank() || (price ?: 0.0) <= 0) { Ui.show("Informe nome e preço."); false } else {
                Store.mutate { st ->
                    val p = saveProduct(st, null, Product(0, name.trim(), "Geral", price ?: 0.0, code, brand.trim()))
                    addProductToList(st, p.id)
                }; true
            }
        }) {
            if (loading) Muted("Buscando o nome na internet…")
            Field(name, { name = it }, "Nome"); Field(brand, { brand = it }, "Marca (opcional)")
            NumField(price, { price = it }, "Preço", suffix = "R$")
        }
    }
}

@Composable
private fun ProdutosSection() {
    val s = state()
    var edit by remember { mutableStateOf<Product?>(null) }
    var isNew by remember { mutableStateOf(false) }
    Card {
        H2("Produtos cadastrados")
        Muted("Seu catálogo de preços — use na lista de compras.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("＋ Produto", Modifier.weight(1f)) { edit = Product(); isNew = true }
            GhostButton("Produtos comuns", Modifier.weight(1f)) { Store.mutate { seedCommonProducts(it) } }
        }
    }
    s.products.forEach { p ->
        Card(onClick = { edit = p; isNew = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Txt(p.name, bold = true); Muted(p.category + if (p.brand.isNotBlank()) " · ${p.brand}" else "") }
                Txt(formatBRL(p.price) + if (p.byWeight) "/kg" else "", bold = true)
            }
        }
    }
    edit?.let { p ->
        var name by remember { mutableStateOf(p.name) }; var price by remember { mutableStateOf<Double?>(p.price) }
        var cat by remember { mutableStateOf(p.category) }; var kg by remember { mutableStateOf(p.byWeight) }
        var brand by remember { mutableStateOf(p.brand) }; var code by remember { mutableStateOf(p.barcode) }
        FormDialog(if (isNew) "Novo produto" else "Editar produto", { edit = null }, "Salvar", {
            if (name.isBlank()) false else {
                Store.mutate { saveProduct(it, if (isNew) null else p.id, Product(p.id, name.trim(), cat.ifBlank { "Geral" }, price ?: 0.0, code.trim(), brand.trim(), p.variant, kg)) }; true
            }
        }) {
            Field(name, { name = it }, "Nome"); NumField(price, { price = it }, if (kg) "Preço por kg" else "Preço", suffix = "R$")
            Field(cat, { cat = it }, "Categoria"); Field(brand, { brand = it }, "Marca"); Field(code, { code = it }, "Código de barras")
            SwitchRow("Vendido por peso (kg)", kg) { kg = it }
            if (!isNew) GhostButton("Excluir produto", danger = true) { Store.mutate { deleteProduct(it, p.id) }; edit = null }
        }
    }
}
