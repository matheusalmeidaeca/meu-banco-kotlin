package com.meubanco.nativo

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

fun showToast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

/** Roda uma ação do motor que devolve mensagem de erro (ou null). Mostra o aviso. Retorna true se deu certo. */
fun Store.act(ctx: Context, okMsg: String? = null, block: (JSONObject) -> String?): Boolean {
    var err: String? = null
    update { err = block(it) }
    val e = err
    if (e != null) {
        showToast(ctx, e)
        return false
    }
    if (okMsg != null) showToast(ctx, okMsg)
    return true
}

fun JSONObject.hideBalance(): Boolean = sub("settings").optBoolean("hideBalance", false)
fun JSONObject.maskBrl(v: Double): String = if (hideBalance()) "R$ ••••" else brl(v)

@Composable
fun Toggle(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = 14.sp)
            if (sub != null) Text(sub, color = p.muted, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.brand,
                checkedThumbColor = p.brandInk,
                uncheckedTrackColor = p.cardAlt,
                uncheckedThumbColor = p.muted,
                uncheckedBorderColor = p.border,
            ),
        )
    }
}

@Composable
fun Seg(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            if (key == selected) {
                PrimaryButton(label, Modifier.weight(1f)) { onSelect(key) }
            } else {
                GhostButton(label, Modifier.weight(1f)) { onSelect(key) }
            }
        }
    }
}

@Composable
fun ProgressBar(frac: Double, color: Color? = null) {
    val p = LocalPalette.current
    val f = frac.coerceIn(0.0, 1.0).toFloat()
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(p.bgSoft)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(color ?: p.brand))
    }
}

@Composable
fun Accordion(
    title: String,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.border, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (summary.isNotEmpty()) Text(summary, color = p.muted, fontSize = 12.sp)
            }
            Text(if (open) "▲" else "▼", color = p.muted, fontSize = 12.sp)
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
        }
    }
}

@Composable
fun SmallLabel(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Helper(text: String, color: Color? = null) {
    val p = LocalPalette.current
    Text(text, color = color ?: p.mutedDim, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun WarnBox(text: String, color: Color? = null) {
    val p = LocalPalette.current
    val c = color ?: p.gold
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(p.bgSoft)
            .border(1.dp, c, shape)
            .padding(10.dp),
    ) { Text(text, color = c, fontSize = 12.5.sp) }
}

@Composable
fun MoneyRow(k: String, v: String, vColor: Color? = null, bold: Boolean = false) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(k, color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(
            v,
            color = vColor ?: p.text,
            fontSize = 13.5.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

class ChartPoint(val label: String, val value: Double)

/** Gráfico de barras ou linha de uma série mensal. Toque para ver o valor de cada mês. */
@Composable
fun SeriesChart(points: List<ChartPoint>, color: Color, bars: Boolean) {
    val p = LocalPalette.current
    val n = points.size
    if (n == 0) return
    var sel by remember(n) { mutableStateOf(n - 1) }
    val idx = sel.coerceIn(0, n - 1)
    val max = points.maxOf { it.value }.let { if (it <= 0.0) 1.0 else it }
    Text(
        "${points[idx].label} · ${brl(points[idx].value)}",
        color = p.muted,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .pointerInput(n) {
                detectTapGestures { off ->
                    sel = ((off.x / size.width) * n).toInt().coerceIn(0, n - 1)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        fun px(i: Int): Float = (i + 0.5f) / n * w
        fun py(v: Double): Float = h - 4f - (v / max).toFloat() * (h - 8f)
        if (bars || n == 1) {
            val bw = Math.min(40.dp.toPx(), w / n * 0.62f)
            points.forEachIndexed { i, pt ->
                val top = py(pt.value)
                drawRect(
                    color = if (i == idx) color else color.copy(alpha = 0.55f),
                    topLeft = Offset(px(i) - bw / 2f, top),
                    size = Size(bw, Math.max(1f, h - top)),
                )
            }
        } else {
            val path = Path()
            points.forEachIndexed { i, pt ->
                if (i == 0) path.moveTo(px(i), py(pt.value)) else path.lineTo(px(i), py(pt.value))
            }
            drawPath(path, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(px(idx), py(points[idx].value)))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(points.first().label, color = p.mutedDim, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
        Text(points.last().label, color = p.mutedDim, fontSize = 10.5.sp)
    }
}

/** Duas linhas (patrimônio total e investido) para o gráfico de evolução. */
@Composable
fun TwoLineChart(total: List<Double>, invested: List<Double>, totalColor: Color, investedColor: Color) {
    val n = total.size
    if (n < 2) return
    val all = total + invested + listOf(0.0)
    val mn = all.min()
    var mx = all.max()
    if (mx == mn) mx = mn + 1.0
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val w = size.width
        val h = size.height
        fun px(i: Int): Float = i.toFloat() / (n - 1) * w
        fun py(v: Double): Float = 6f + (h - 12f) - ((v - mn) / (mx - mn)).toFloat() * (h - 12f)
        fun line(vs: List<Double>, c: Color, width: Float) {
            val path = Path()
            vs.forEachIndexed { i, v -> if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
            drawPath(path, c, style = Stroke(width = width, cap = StrokeCap.Round))
        }
        line(invested, investedColor, 1.6.dp.toPx())
        line(total, totalColor, 2.4.dp.toPx())
    }
}
