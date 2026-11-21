package com.meubanco.patrimonio.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

/** Avisos e celebrações globais. */
object Ui {
    var toast by mutableStateOf<String?>(null)
    var celebration by mutableStateOf<Celebration?>(null)
    var quickSpend by mutableStateOf(false)
    val celebrationQueue = mutableListOf<Celebration>()
    fun show(msg: String, sub: String = "") { toast = if (sub.isBlank()) msg else "$msg — $sub" }
    fun enqueue(list: List<Celebration>) {
        celebrationQueue.addAll(list)
        if (celebration == null && celebrationQueue.isNotEmpty()) celebration = celebrationQueue.removeAt(0)
    }
    fun nextCelebration() { celebration = if (celebrationQueue.isNotEmpty()) celebrationQueue.removeAt(0) else null }
}

/** Lê Store.rev para o Compose redesenhar quando o estado muda. */
@Composable
fun state(): AppState { Store.rev; return Store.state }

@Composable
fun money(v: Double): String = if (state().settings.hideBalance) "R$ •••••" else formatBRL(v)

/** Mostra o resultado de uma ação; retorna true se deu certo. */
fun handle(r: Res): Boolean {
    if (r.ok) { if (r.msg.isNotBlank()) Ui.show(r.msg, r.sub) }
    else if (r.msg.isNotBlank()) Ui.show(r.msg)
    return r.ok
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.card)
        .border(1.dp, P.line, RoundedCornerShape(16.dp))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(14.dp)
    Column(m, verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun Txt(text: String, modifier: Modifier = Modifier, color: Color = P.text, style: androidx.compose.ui.text.TextStyle = T.body,
        bold: Boolean = false, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, style = if (bold) style.copy(fontWeight = FontWeight.Bold) else style, textAlign = align, maxLines = maxLines)
}

@Composable fun Muted(text: String, modifier: Modifier = Modifier) = Txt(text, modifier, P.muted, T.small)
@Composable fun H2(text: String) = Txt(text, style = T.h2, bold = true)

@Composable
fun KV(k: String, v: String, vColor: Color = P.text) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Txt(k, color = P.muted, modifier = Modifier.weight(1f))
        Txt(v, color = vColor, bold = true, align = TextAlign.End)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.brand, contentColor = Color.White)) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier, enabled = enabled, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (danger) P.red else P.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) P.red else P.text)) { Text(text) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) P.brand else P.card2)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Txt(text, color = if (selected) Color.White else P.text, style = T.small)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (k, label) -> Chip(label, k == selected) { onSelect(k) } }
    }
}

/** Campo de texto padrão. */
@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
          keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, suffix: String? = null) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        suffix = suffix?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = P.text, unfocusedTextColor = P.text,
            focusedBorderColor = P.brand, unfocusedBorderColor = P.line, focusedLabelColor = P.brand, unfocusedLabelColor = P.muted,
            cursorColor = P.brand))
}

/** Campo numérico. Com suffix "R$" vira campo de moeda com máscara (digite só os números: 12345 → 123,45). */
@Composable
fun NumField(v: Double?, onChange: (Double?) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null, decimals: Boolean = true) {
    if (suffix == "R$" && decimals) { MoneyField(v, onChange, label, modifier); return }
    var text by remember { mutableStateOf(if (v == null || v == 0.0) "" else fmtPlain(v)) }
    Field(text, {
        text = it
        onChange(if (it.isBlank()) null else parseBrNumber(it))
    }, label, modifier, if (decimals) KeyboardType.Decimal else KeyboardType.Number, suffix = suffix)
}

fun maskCents(c: Long): String {
    if (c <= 0) return ""
    val s = c.toString().padStart(3, '0')
    val int = s.dropLast(2).reversed().chunked(3).joinToString(".").reversed()
    return int + "," + s.takeLast(2)
}

@Composable
fun MoneyField(v: Double?, onChange: (Double?) -> Unit, label: String, modifier: Modifier = Modifier) {
    var cents by remember { mutableStateOf(if (v == null) 0L else Math.round(v * 100)) }
    var tf by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(maskCents(cents), androidx.compose.ui.text.TextRange(maskCents(cents).length))) }
    OutlinedTextField(tf, { nv ->
        val digits = nv.text.filter { it.isDigit() }.take(12).trimStart('0')
        cents = digits.toLongOrNull() ?: 0L
        val m = maskCents(cents)
        tf = androidx.compose.ui.text.input.TextFieldValue(m, androidx.compose.ui.text.TextRange(m.length))
        onChange(if (cents == 0L) null else cents / 100.0)
    }, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        prefix = { Text("R$ ") },
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = P.text, unfocusedTextColor = P.text,
            focusedBorderColor = P.brand, unfocusedBorderColor = P.line, focusedLabelColor = P.brand, unfocusedLabelColor = P.muted,
            cursorColor = P.brand))
}

fun fmtPlain(v: Double): String = if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString() else v.toString().replace('.', ',')

@Composable
fun Progress(frac: Double, color: Color = P.brand, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.card2)) {
        Box(Modifier.fillMaxWidth(clamp(frac, 0.0, 1.0).toFloat()).fillMaxHeight().background(color))
    }
}

@Composable
fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = P.card, titleContentColor = P.text, textContentColor = P.text,
        title = { Txt(title, style = T.title, bold = true) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = { TextButton(onDismiss) { Text("Fechar") } })
}

@Composable
fun Confirm(text: String, onYes: () -> Unit, onNo: () -> Unit, yes: String = "Confirmar") {
    AlertDialog(onDismissRequest = onNo, containerColor = P.card, textContentColor = P.text,
        text = { Txt(text) },
        confirmButton = { TextButton(onYes) { Text(yes) } },
        dismissButton = { TextButton(onNo) { Text("Cancelar") } })
}

/** Pizza/rosca. */
@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier.size(150.dp)) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val stroke = size.minDimension * 0.22f
        var start = -90f
        slices.forEach { sl ->
            val sweep = (sl.value / total * 360).toFloat()
            drawArc(Color(sl.color or 0xFF000000), start, sweep, false,
                topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

/** Linha simples (patrimônio no tempo). */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier, color: Color = P.brand) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.size < 2) return@Canvas
        val mn = values.min(); val mx = values.max()
        val rng = if (mx - mn < 1e-9) 1.0 else mx - mn
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = (size.height - ((v - mn) / rng * size.height * 0.9 + size.height * 0.05)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
    }
}

/** Barras horizontais por categoria. */
@Composable
fun BarList(items: List<Pair<String, Double>>, color: Color = P.brand) {
    val mx = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (k, v) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Txt(k, style = T.small); Txt(money(v), style = T.small, bold = true) }
                Progress(v / mx, color)
            }
        }
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.height(8.dp))

/** Barras verticais com rótulo embaixo. */
@Composable
fun BarChart(items: List<Pair<String, Double>>, color: Color = P.green) {
    val mx = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        items.forEach { (k, v) ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v >= 1000) "${Math.round(v / 100) / 10.0}k" else "${Math.round(v)}", color = P.muted, fontSize = 9.sp, maxLines = 1)
                Box(Modifier.fillMaxWidth().fillMaxHeight((v / mx * 0.72).toFloat().coerceIn(0.02f, 0.72f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
                Text(k, color = P.muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

val CHART_COLORS = listOf(0xFF4F7CFF, 0xFFD4A24C, 0xFF34C98F, 0xFFEF6461, 0xFFA78BFA, 0xFF22D3EE, 0xFFF472B6, 0xFFFB923C, 0xFF84CC16)

/** Pizza com legenda para pares nome → valor. */
@Composable
fun PieWithLegend(items: List<Pair<String, Double>>) {
    val slices = items.mapIndexed { i, (k, v) -> Slice(k, v, CHART_COLORS[i % CHART_COLORS.size]) }
    val tot = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Donut(slices, Modifier.size(130.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { sl ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(sl.color)))
                    Txt("${sl.label} · ${Math.round(sl.value / tot * 100)}%", style = T.small)
                }
            }
        }
    }
}

/** Campo de data com calendário (guarda AAAA-MM-DD). */
@Composable
fun DateField(label: String, iso: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, defaultYear: Int = 1990) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(if (iso.isBlank()) "" else formatSimDate(iso), {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) },
            placeholder = { Text("Toque para escolher") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = P.text, unfocusedTextColor = P.text,
                focusedBorderColor = P.brand, unfocusedBorderColor = P.line, focusedLabelColor = P.brand, unfocusedLabelColor = P.muted))
        Box(Modifier.matchParentSize().clickable {
            val d = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(iso)) isoToDate(iso) else java.time.LocalDate.of(defaultYear, 1, 1)
            android.app.DatePickerDialog(ctx, { _, y, m, day -> onChange("%04d-%02d-%02d".format(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        })
    }
}

fun shareText(ctx: android.content.Context, text: String, title: String = "Compartilhar") {
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    ctx.startActivity(android.content.Intent.createChooser(i, title))
}
