package com.meubanco.nativo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import org.json.JSONObject

// ============================================================
// Componentes de apoio
// ============================================================

@Composable
fun TextInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val p = LocalPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = p.text,
            unfocusedTextColor = p.text,
            cursorColor = p.brand,
            focusedBorderColor = p.brand,
            unfocusedBorderColor = p.border,
            focusedLabelColor = p.brand,
            unfocusedLabelColor = p.muted,
        ),
    )
}

@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) p.brandDim else p.cardAlt)
            .border(1.dp, if (selected) p.brand else p.border, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (selected) p.text else p.muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun MiniButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(32.dp)
            .clip(shape)
            .background(p.cardAlt)
            .border(1.dp, p.border, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) p.text else p.mutedDim, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SheetDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(shape)
                .background(p.card)
                .border(1.dp, p.border, shape),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = p.muted, fontSize = 18.sp) }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                content = content,
            )
        }
    }
}

@Composable
fun ConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        text = { Text(message, color = p.text, fontSize = 15.sp) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text("Confirmar", color = p.brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = p.muted) } },
    )
}

// ============================================================
// Aba Gastos
// ============================================================

@Composable
fun GastosPanel(store: Store) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var pieMode by rememberSaveable { mutableStateOf("real") }
    var pieYm by rememberSaveable { mutableStateOf<String?>(null) }

    var showAddExpense by remember { mutableStateOf(false) }
    var editExpense by remember { mutableStateOf<JSONObject?>(null) }
    var showAddItem by remember { mutableStateOf(false) }
    var productForm by remember { mutableStateOf<JSONObject?>(null) }
    var showNewProduct by remember { mutableStateOf(false) }
    var weighProduct by remember { mutableStateOf<JSONObject?>(null) }
    var editShop by remember { mutableStateOf<JSONObject?>(null) }
    var showDiscount by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val st = store.current()
    if (st == null) {
        CardBox {
            BoxTitle("Gastos")
            Text("Importe o backup do app antigo (em Configurações) para ver seus gastos.", color = p.muted, fontSize = 13.sp)
        }
        return
    }

    val simISO = st.optString("simDateISO").ifEmpty { todayISO() }
    val cur = simISO.take(7)

    // ---------- Para onde vai o dinheiro ----------
    val ym = pieYm?.takeIf { it <= cur } ?: cur
    val first = st.firstStatsYm() ?: cur
    val slices = st.pieSlices(pieMode, ym)
    val pieTotal = slices.fold(0.0) { a, x -> a + x.value }

    CardBox {
        BoxTitle("Para onde vai o dinheiro", "Gastos por categoria")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("Realizado no mês", pieMode == "real") { pieMode = "real" }
            PillChip("Gastos fixos planejados", pieMode == "plan") { pieMode = "plan" }
        }
        if (pieMode == "real") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiniButton("‹", enabled = ym > first) { pieYm = shiftYm(ym, -1) }
                Text(
                    formatMonthLabel(ym),
                    color = p.text,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MiniButton("›", enabled = ym < cur) { pieYm = shiftYm(ym, 1) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (pieTotal <= 0) {
            Text(
                if (pieMode == "real") {
                    "Sem gastos registrados neste mês ainda — os gastos fixos caem 1 dia após o salário. Veja o \"planejado\" enquanto isso."
                } else {
                    "Cadastre gastos fixos para ver o gráfico."
                },
                color = p.muted,
                fontSize = 13.sp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(128.dp)) {
                        val stroke = size.minDimension * 0.16f
                        var start = -90f
                        slices.forEachIndexed { i, x ->
                            val sweep = (x.value / pieTotal * 360.0).toFloat()
                            drawArc(
                                color = PIE_COLORS[i % PIE_COLORS.size],
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke),
                            )
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", color = p.muted, fontSize = 10.sp)
                        Text(brl(pieTotal), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    slices.forEachIndexed { i, x ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(PIE_COLORS[i % PIE_COLORS.size]))
                            Spacer(Modifier.width(6.dp))
                            Text(x.label, color = p.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("${Math.round(x.value / pieTotal * 100)}%", color = p.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---------- Gastos fixos mensais ----------
    val expenses = st.arr("expenses")
    val linkedId = st.optInt("shoppingLinkedExpenseId", -1)
    CardBox {
        BoxTitle("Gastos fixos mensais", "Toque nos três pontinhos para editar ou excluir uma categoria")
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Total dos gastos fixos", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(brl(st.expensesTotal()), color = p.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (expenses.isEmpty()) {
            Text("Nenhum gasto fixo cadastrado.", color = p.muted, fontSize = 13.sp)
        } else {
            expenses.forEachIndexed { i, e ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.borderSoft))
                val isLinked = e.optInt("id") == linkedId
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.optString("name"), color = p.text, fontSize = 14.sp)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tag = RoundedCornerShape(6.dp)
                            Box(Modifier.clip(tag).background(p.cardAlt).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(e.optString("category").ifEmpty { "Outros" }, color = p.muted, fontSize = 10.sp)
                            }
                            if (isLinked) {
                                Spacer(Modifier.width(6.dp))
                                Text("vem da lista de compras", color = p.mutedDim, fontSize = 10.sp)
                            }
                        }
                    }
                    Text(brl(e.optDouble("amount", 0.0)), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (isLinked) {
                        Spacer(Modifier.width(40.dp))
                    } else {
                        RowMenu(
                            listOf(
                                MenuAction("✏️ Editar") { editExpense = e },
                                MenuAction("🗑 Excluir", danger = true) {
                                    confirm = "Excluir este gasto fixo?" to {
                                        store.update { it.removeExpense(e.optInt("id")) }
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("+ Adicionar gastos") { showAddExpense = true }
    }
    Spacer(Modifier.height(14.dp))

    // ---------- Lista de compras ----------
    val items = st.arr("shoppingList")
    CardBox {
        BoxTitle("Lista de compras", "Fica salva de um mês para o outro · o total vira o gasto fixo \"Alimentação\"")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("+ Adicionar item", Modifier.weight(1f)) { showAddItem = true }
            GhostButton("📦 Catálogo", Modifier.weight(1f)) { showAddItem = true }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("🏷 Desconto", Modifier.weight(1f)) { showDiscount = true }
            GhostButton("↺ Lista nova", Modifier.weight(1f)) {
                confirm = "Desmarcar todos os itens da lista?" to { store.update { it.resetShoppingChecks() } }
            }
        }
        Spacer(Modifier.height(12.dp))
        ReceiptView(
            st = st,
            items = items,
            onToggle = { id -> store.update { it.toggleShop(id) } },
            onQty = { id, d -> store.update { it.changeShopQty(id, d) } },
            onEdit = { editShop = it },
            onDelete = { id ->
                confirm = "Remover este item da lista?" to { store.update { it.removeShopItem(id) } }
            },
        )
    }

    // ---------- Diálogos ----------
    if (showAddExpense) {
        ExpenseDialog(
            title = "Adicionar gasto fixo",
            initName = "",
            initAmount = "",
            initCategory = "Outros",
            showCatalog = true,
            confirmLabel = "Adicionar gasto",
            onClose = { showAddExpense = false },
            onSave = { n, a, c ->
                store.update { it.addExpense(n, a, c) }
                showAddExpense = false
            },
        )
    }
    editExpense?.let { e ->
        ExpenseDialog(
            title = "Editar gasto fixo",
            initName = e.optString("name"),
            initAmount = e.optDouble("amount", 0.0).let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() },
            initCategory = e.optString("category").ifEmpty { "Outros" },
            showCatalog = false,
            confirmLabel = "Salvar",
            onClose = { editExpense = null },
            onSave = { n, a, c ->
                store.update { it.updateExpense(e.optInt("id"), n, a, c) }
                editExpense = null
            },
        )
    }
    if (showAddItem) {
        AddItemDialog(
            products = st.arr("products"),
            onClose = { showAddItem = false },
            onAddProduct = { prod ->
                if (prod.optBoolean("byWeight")) {
                    weighProduct = prod
                } else {
                    store.update { it.addProductToList(prod) }
                    Toast.makeText(ctx, "🛒 ${prod.optString("name")} na lista", Toast.LENGTH_SHORT).show()
                }
            },
            onEditProduct = { productForm = it },
            onDeleteProduct = { prod ->
                confirm = "Excluir este produto do catálogo?" to {
                    store.update { it.deleteProduct(prod.optInt("id")) }
                }
            },
            onNewProduct = { showNewProduct = true },
            onSeed = {
                store.update { it.seedCommonProducts() }
                Toast.makeText(ctx, "Produtos comuns adicionados — ajuste os preços do seu mercado", Toast.LENGTH_LONG).show()
            },
            onAddLoose = { n, c, pr, q ->
                store.update { it.addShopItem(n, c, pr, q) }
                showAddItem = false
            },
        )
    }
    if (showNewProduct || productForm != null) {
        val editing = productForm
        ProductFormDialog(
            initial = editing,
            onClose = {
                showNewProduct = false
                productForm = null
            },
            onSave = { n, c, pr, bc, br, v, bw ->
                store.update { it.saveProduct(editing?.optInt("id"), n, c, pr, bc, br, v, bw) }
                showNewProduct = false
                productForm = null
            },
        )
    }
    weighProduct?.let { prod ->
        WeighDialog(
            product = prod,
            onClose = { weighProduct = null },
            onAdd = { kg ->
                store.update { it.addWeighedToList(prod, kg) }
                weighProduct = null
                Toast.makeText(ctx, "🛒 ${prod.optString("name")} · ${fmtKg(kg)} na lista", Toast.LENGTH_SHORT).show()
            },
        )
    }
    editShop?.let { item ->
        EditShopDialog(
            item = item,
            onClose = { editShop = null },
            onSave = { n, c, q, pr ->
                store.update { it.editShopItem(item.optInt("id"), n, c, q, pr) }
                editShop = null
            },
        )
    }
    if (showDiscount) {
        val d = st.optJSONObject("shoppingDiscount")
        DiscountDialog(
            initType = d?.optString("type", "pct") ?: "pct",
            initValue = d?.optDouble("value", 0.0) ?: 0.0,
            onClose = { showDiscount = false },
            onApply = { type, v ->
                store.update { it.setShoppingDiscount(type, v) }
                showDiscount = false
            },
        )
    }
    confirm?.let { (msg, run) ->
        ConfirmDialog(msg, onConfirm = run, onDismiss = { confirm = null })
    }
}

// ============================================================
// Lista de compras em formato de nota
// ============================================================

@Composable
fun ReceiptView(
    st: JSONObject,
    items: List<JSONObject>,
    onToggle: (Int) -> Unit,
    onQty: (Int, Int) -> Unit,
    onEdit: (JSONObject) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val p = LocalPalette.current
    if (items.isEmpty()) {
        Text(
            "Lista vazia. Adicione itens do catálogo ou digite um item avulso.",
            color = p.muted,
            fontSize = 13.sp,
        )
        return
    }
    val mono = FontFamily.Monospace
    val shape = RoundedCornerShape(12.dp)
    val iso = st.optString("simDateISO").ifEmpty { todayISO() }
    val dateLabel = iso.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else iso }
    val totalQty = items.fold(0) { a, i -> a + i.optInt("quantity", 1) }
    val sub = st.shoppingSubtotal()
    val disc = st.shoppingDiscountFor(sub)
    val sd = st.optJSONObject("shoppingDiscount")

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
            Text(dateLabel, color = p.muted, fontSize = 11.sp, fontFamily = mono)
        }
        Text("DOCUMENTO NÃO FISCAL · USO PESSOAL", color = p.mutedDim, fontSize = 10.sp, fontFamily = mono)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Spacer(Modifier.height(6.dp))

        items.forEachIndexed { idx, i ->
            val id = i.optInt("id")
            val checked = i.optBoolean("checked")
            val weight = i.optDouble("weightKg", 0.0)
            val lineTotal = i.optDouble("price", 0.0) * i.optInt("quantity", 1)
            val meta = listOf(i.optString("category"), i.optString("variant"), i.optString("barcode"))
                .filter { it.isNotEmpty() }.joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle(id) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = p.brand,
                        uncheckedColor = p.mutedDim,
                        checkmarkColor = p.brandInk,
                    ),
                )
                Text((idx + 1).toString().padStart(3, '0'), color = p.mutedDim, fontSize = 11.sp, fontFamily = mono)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        i.optString("name"),
                        color = if (checked) p.mutedDim else p.text,
                        fontSize = 14.sp,
                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    if (meta.isNotEmpty()) Text(meta, color = p.mutedDim, fontSize = 10.5.sp)
                }
                RowMenu(
                    listOf(
                        MenuAction("✏️ Editar") { onEdit(i) },
                        MenuAction("🗑 Excluir", danger = true) { onDelete(id) },
                    ),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (weight > 0) {
                    Text(
                        "${fmtKg(weight)} x ${fmt2(i.optDouble("pricePerKg", 0.0))}/kg",
                        color = p.muted,
                        fontSize = 11.5.sp,
                        fontFamily = mono,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    MiniButton("−") { onQty(id, -1) }
                    Text(
                        i.optInt("quantity", 1).toString(),
                        color = p.text,
                        fontSize = 13.sp,
                        fontFamily = mono,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(30.dp),
                    )
                    MiniButton("+") { onQty(id, 1) }
                    Spacer(Modifier.width(8.dp))
                    Text("UN x ${fmt2(i.optDouble("price", 0.0))}", color = p.muted, fontSize = 11.5.sp, fontFamily = mono, modifier = Modifier.weight(1f))
                }
                Text(fmt2(lineTotal), color = p.text, fontSize = 13.sp, fontFamily = mono, fontWeight = FontWeight.SemiBold)
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Spacer(Modifier.height(6.dp))
        ReceiptTotal("QTD. TOTAL DE ITENS", totalQty.toString())
        ReceiptTotal("NO CARRINHO (MARCADOS)", "R$ " + fmt2(st.shoppingCheckedTotal()))
        if (disc > 0) {
            ReceiptTotal("SUBTOTAL", "R$ " + fmt2(sub))
            val pct = if (sd?.optString("type") == "valor") "" else " (${sd?.optDouble("value", 0.0)?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }}%)"
            ReceiptTotal("DESCONTO$pct", "− R$ " + fmt2(disc))
        }
        ReceiptTotal("VALOR TOTAL", "R$ " + fmt2(st.shoppingFullTotal()), big = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "*** BOM MERCADO! ***",
            color = p.mutedDim,
            fontSize = 11.sp,
            fontFamily = mono,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ReceiptTotal(label: String, value: String, big: Boolean = false) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            color = if (big) p.text else p.muted,
            fontSize = if (big) 14.sp else 11.5.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = if (big) p.gold else p.text,
            fontSize = if (big) 14.sp else 11.5.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ============================================================
// Diálogos
// ============================================================

@Composable
fun ExpenseDialog(
    title: String,
    initName: String,
    initAmount: String,
    initCategory: String,
    showCatalog: Boolean,
    confirmLabel: String,
    onClose: () -> Unit,
    onSave: (String, Double, String) -> Unit,
) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(initName) }
    var amount by remember { mutableStateOf(initAmount) }
    var category by remember { mutableStateOf(initCategory) }
    SheetDialog(title, onClose) {
        if (showCatalog) {
            Text(
                "Escolha uma opção do catálogo ou digite um gasto seu — dá para editar nome, valor e categoria antes de adicionar.",
                color = p.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EXPENSE_CATALOG.forEach { c ->
                    PillChip(c.name, name == c.name) {
                        name = c.name
                        amount = c.value.toString()
                        category = c.category
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TextInput("Gasto", name, { name = it })
        Spacer(Modifier.height(8.dp))
        TextInput("Valor (R$)", amount, { amount = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        Text("Categoria", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EXPENSE_CATEGORIES.forEach { c -> PillChip(c, category == c) { category = c } }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(confirmLabel) {
            val a = parseNum(amount)
            if (name.trim().isNotEmpty() && a > 0) onSave(name.trim(), a, category)
        }
    }
}

@Composable
fun AddItemDialog(
    products: List<JSONObject>,
    onClose: () -> Unit,
    onAddProduct: (JSONObject) -> Unit,
    onEditProduct: (JSONObject) -> Unit,
    onDeleteProduct: (JSONObject) -> Unit,
    onNewProduct: () -> Unit,
    onSeed: () -> Unit,
    onAddLoose: (String, String, Double, Int) -> Unit,
) {
    val p = LocalPalette.current
    var search by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    val term = search.trim().lowercase()
    val filtered = products.filter {
        term.isEmpty() || it.optString("name").lowercase().contains(term) || it.optString("barcode").contains(term)
    }
    SheetDialog("Adicionar à lista", onClose) {
        TextInput("Buscar no catálogo...", search, { search = it })
        Spacer(Modifier.height(8.dp))
        if (products.isEmpty()) {
            Text(
                "Seu catálogo está vazio. Cadastre produtos ou use \"Produtos comuns\".",
                color = p.muted,
                fontSize = 13.sp,
            )
        } else if (filtered.isEmpty()) {
            Text("Nenhum produto encontrado.", color = p.muted, fontSize = 13.sp)
        } else {
            filtered.forEach { prod ->
                val byWeight = prod.optBoolean("byWeight")
                val meta = buildString {
                    append(prod.optString("category").ifEmpty { "Geral" })
                    if (byWeight) append(" · ⚖️ por peso")
                    if (prod.optString("variant").isNotEmpty()) append(" · ").append(prod.optString("variant"))
                    if (prod.optString("barcode").isNotEmpty()) append(" · ").append(prod.optString("barcode"))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(prod.optString("name"), color = p.text, fontSize = 14.sp)
                        Text(meta, color = p.mutedDim, fontSize = 10.5.sp)
                    }
                    Text(
                        brl(prod.optDouble("price", 0.0)) + if (byWeight) "/kg" else "",
                        color = p.gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    MiniButton("+") { onAddProduct(prod) }
                    RowMenu(
                        listOf(
                            MenuAction("✏️ Editar") { onEditProduct(prod) },
                            MenuAction("🗑 Excluir", danger = true) { onDeleteProduct(prod) },
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("+ Cadastrar produto", Modifier.weight(1f), onNewProduct)
            GhostButton("Produtos comuns", Modifier.weight(1f), onSeed)
        }
        Spacer(Modifier.height(16.dp))
        Text("Item avulso (sem cadastrar)", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TextInput("Item", name, { name = it })
        Spacer(Modifier.height(8.dp))
        TextInput("Categoria", category, { category = it })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput("Qtd.", qty, { qty = it }, Modifier.weight(1f), KeyboardType.Number)
            TextInput("Preço unit. (R$)", price, { price = it }, Modifier.weight(1.6f), KeyboardType.Decimal)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Adicionar item") {
            val q = Math.max(1, Math.round(parseNum(qty)).toInt())
            if (name.trim().isNotEmpty()) onAddLoose(name.trim(), category.trim(), parseNum(price), q)
        }
    }
}

@Composable
fun ProductFormDialog(
    initial: JSONObject?,
    onClose: () -> Unit,
    onSave: (String, String, Double, String, String, String, Boolean) -> Unit,
) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(initial?.optString("name").orEmpty()) }
    var category by remember { mutableStateOf(initial?.optString("category").orEmpty()) }
    var price by remember {
        mutableStateOf(
            initial?.optDouble("price", 0.0)?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }.orEmpty(),
        )
    }
    var byWeight by remember { mutableStateOf(initial?.optBoolean("byWeight") ?: false) }
    var barcode by remember { mutableStateOf(initial?.optString("barcode").orEmpty()) }
    var brand by remember { mutableStateOf(initial?.optString("brand").orEmpty()) }
    var variant by remember { mutableStateOf(initial?.optString("variant").orEmpty()) }
    var more by remember { mutableStateOf(!brand.isEmpty() || !variant.isEmpty()) }
    SheetDialog(if (initial != null) "Editar produto" else "Cadastrar produto", onClose) {
        TextInput("Nome do produto", name, { name = it })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput("Categoria", category, { category = it }, Modifier.weight(1.2f))
            TextInput(if (byWeight) "Preço por kg" else "Preço", price, { price = it }, Modifier.weight(1f), KeyboardType.Decimal)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clickable { byWeight = !byWeight },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = byWeight,
                onCheckedChange = { byWeight = it },
                colors = CheckboxDefaults.colors(checkedColor = p.brand, uncheckedColor = p.mutedDim, checkmarkColor = p.brandInk),
            )
            Text("⚖️ Vendido por peso (o preço é por kg)", color = p.text, fontSize = 13.sp)
        }
        TextInput("Código de barras", barcode, { barcode = it }, keyboard = KeyboardType.Number)
        Spacer(Modifier.height(8.dp))
        GhostButton(if (more) "− Menos detalhes" else "+ Mais detalhes (marca, variação)") { more = !more }
        if (more) {
            Spacer(Modifier.height(8.dp))
            TextInput("Marca", brand, { brand = it })
            Spacer(Modifier.height(8.dp))
            TextInput("Variação (tamanho, cor, sabor)", variant, { variant = it })
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Salvar produto") {
            if (name.trim().isNotEmpty()) {
                onSave(name.trim(), category.trim(), parseNum(price), barcode.trim(), brand.trim(), variant.trim(), byWeight)
            }
        }
    }
}

@Composable
fun WeighDialog(product: JSONObject, onClose: () -> Unit, onAdd: (Double) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    val raw = parseNum(input)
    val kg = if (unit == "g") raw / 1000.0 else raw
    val pricePerKg = product.optDouble("price", 0.0)
    SheetDialog(product.optString("name"), onClose) {
        Text("${brl(pricePerKg)} por kg · digite o peso da balança", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput("Peso (ex.: 0,350)", input, { input = it }, Modifier.weight(1f), KeyboardType.Decimal)
            if (unit == "kg") PrimaryButton("kg", Modifier.width(64.dp)) { unit = "kg" } else GhostButton("kg", Modifier.width(64.dp)) { unit = "kg" }
            if (unit == "g") PrimaryButton("g", Modifier.width(64.dp)) { unit = "g" } else GhostButton("g", Modifier.width(64.dp)) { unit = "g" }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Valor", color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(brl(if (kg > 0) round2(pricePerKg * kg) else 0.0), color = p.gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Adicionar à lista") {
            if (kg <= 0) Toast.makeText(ctx, "Digite o peso do produto", Toast.LENGTH_SHORT).show() else onAdd(kg)
        }
    }
}

@Composable
fun EditShopDialog(item: JSONObject, onClose: () -> Unit, onSave: (String, String, Double, Double) -> Unit) {
    val p = LocalPalette.current
    val isWeight = item.optDouble("weightKg", 0.0) > 0
    var name by remember { mutableStateOf(item.optString("name")) }
    var category by remember { mutableStateOf(item.optString("category")) }
    var qtyOrKg by remember {
        mutableStateOf(
            if (isWeight) item.optDouble("weightKg", 0.0).toString().replace(".", ",") else item.optInt("quantity", 1).toString(),
        )
    }
    var price by remember {
        mutableStateOf(item.optDouble("price", 0.0).let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() })
    }
    SheetDialog("Editar item", onClose) {
        TextInput("Item", name, { name = it })
        Spacer(Modifier.height(8.dp))
        TextInput("Categoria", category, { category = it })
        Spacer(Modifier.height(8.dp))
        if (isWeight) {
            TextInput("Peso (kg)", qtyOrKg, { qtyOrKg = it }, keyboard = KeyboardType.Decimal)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Qtd.", qtyOrKg, { qtyOrKg = it }, Modifier.weight(1f), KeyboardType.Number)
                TextInput("Preço unit. (R$)", price, { price = it }, Modifier.weight(1.6f), KeyboardType.Decimal)
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Salvar") {
            if (name.trim().isNotEmpty()) onSave(name.trim(), category.trim(), parseNum(qtyOrKg), parseNum(price))
        }
    }
}

@Composable
fun DiscountDialog(initType: String, initValue: Double, onClose: () -> Unit, onApply: (String, Double) -> Unit) {
    val p = LocalPalette.current
    var type by remember { mutableStateOf(if (initType == "valor") "valor" else "pct") }
    var value by remember {
        mutableStateOf(if (initValue <= 0) "" else if (initValue == Math.floor(initValue)) initValue.toLong().toString() else initValue.toString())
    }
    SheetDialog("Desconto na lista", onClose) {
        Text("Aplica um desconto sobre o total da lista de compras.", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("Porcentagem (%)", type == "pct") { type = "pct" }
            PillChip("Valor (R$)", type == "valor") { type = "valor" }
        }
        Spacer(Modifier.height(10.dp))
        TextInput(if (type == "pct") "Desconto (%)" else "Desconto (R$)", value, { value = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Remover", Modifier.weight(1f)) { onApply("pct", 0.0) }
            PrimaryButton("Aplicar", Modifier.weight(1f)) { onApply(type, parseNum(value)) }
        }
    }
}
