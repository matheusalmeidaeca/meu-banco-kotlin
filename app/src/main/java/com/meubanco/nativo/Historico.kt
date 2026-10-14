package com.meubanco.nativo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Série mensal (um valor por mês, do primeiro mês com dados até o atual). */
fun JSONObject.monthSeries(field: String): List<ChartPoint> {
    val stats = optJSONObject("monthlyStats") ?: return emptyList()
    val keys = stats.keys().asSequence().toList().sorted()
    if (keys.isEmpty()) return emptyList()
    val cur = ymOf(optString("simDateISO", todayISO()))
    val out = mutableListOf<ChartPoint>()
    var ym = keys.first()
    for (g in 0 until 600) {
        out.add(ChartPoint(monthShort(ym), stats.optJSONObject(ym)?.optDouble(field, 0.0) ?: 0.0))
        if (ym >= cur) break
        ym = shiftYm(ym, 1)
    }
    return out
}

@Composable
fun HistoricoPanel(store: Store) {
    val p = LocalPalette.current
    val st = store.current()
    var filter by rememberSaveable { mutableStateOf("todos") }
    var range by rememberSaveable { mutableStateOf(0) }
    var bars by rememberSaveable { mutableStateOf(true) }
    var acum by rememberSaveable { mutableStateOf(false) }

    if (st == null) {
        CardBox {
            BoxTitle("Extrato & histórico")
            Text("Importe um backup (ou comece uma conta nova) na aba Conta.", color = p.muted, fontSize = 13.sp)
        }
        return
    }

    val sim = st.optString("simDateISO", todayISO())
    val ym = ymOf(sim)
    val all = st.objList("history")
    val month = all.filter { it.str("ts").take(7) == ym && it.str("type") != "conquista" && it.str("type") != "aviso" }
    val entradas = month.filter { it.optDouble("amount", 0.0) > 0 }.sumOf { it.optDouble("amount", 0.0) }
    val saidas = month.filter { it.optDouble("amount", 0.0) < 0 }.sumOf { Math.abs(it.optDouble("amount", 0.0)) }

    CardBox {
        BoxTitle("Resumo do mês", ymLabel(ym))
        MoneyRow("Entradas (renda, dividendos, retiradas)", "+" + brl(entradas), p.emerald)
        MoneyRow("Saídas (despesas, aportes, gasto livre)", "−" + brl(saidas), p.coral)
        MoneyRow("Saldo do mês", brl(entradas - saidas), if (entradas - saidas >= 0) p.emerald else p.coral, bold = true)
    }
    Spacer(Modifier.height(14.dp))

    // ---- gráficos mês a mês ----
    CardBox {
        BoxTitle("Mês a mês", "Toque no gráfico para ver cada mês")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(6 to "6M", 12 to "1A", 0 to "Tudo").forEach { (k, l) -> GChip(l, range == k) { range = k } }
            GChip("Barras", bars) { bars = true }
            GChip("Linha", !bars) { bars = false }
        }
    }
    Spacer(Modifier.height(10.dp))
    fun slice(l: List<ChartPoint>) = if (range > 0) l.takeLast(range) else l

    MonthChartCard("Gastos", slice(st.monthSeries("spent")), p.coral, bars)
    Spacer(Modifier.height(10.dp))
    MonthChartCard("Renda", slice(st.monthSeries("income")), p.emerald, bars)
    Spacer(Modifier.height(10.dp))
    var ap = st.monthSeries("invested")
    if (acum) {
        var run = 0.0
        ap = ap.map { run = r2(run + it.value); ChartPoint(it.label, run) }
    }
    MonthChartCard(
        "Aplicado (aportes, reservas e metas)",
        slice(ap),
        p.gold,
        bars,
        extra = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GChip("No mês", !acum) { acum = false }
                GChip("Acumulado", acum) { acum = true }
            }
            Spacer(Modifier.height(8.dp))
        },
    )
    Spacer(Modifier.height(14.dp))

    // ---- filtros e lista ----
    val types = listOf(
        "todos" to "Todos", "renda" to "Renda", "despesa" to "Gastos", "gastolivre" to "Gasto livre", "pix" to "Pix",
        "cartao" to "Cartão", "fatura" to "Fatura", "transferencia" to "Transferências", "dividendo" to "Dividendos",
        "aporte" to "Aportes", "saque" to "Retiradas", "compra" to "Compras", "venda" to "Vendas", "imposto" to "Impostos",
        "cdb" to "Reservas", "meta" to "Metas", "conquista" to "Conquistas", "aviso" to "Avisos",
    )
    CardBox {
        BoxTitle("Extrato & histórico")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { (k, l) -> GChip(l, filter == k) { filter = k } }
        }
        Spacer(Modifier.height(12.dp))
        val items = if (filter == "todos") all else all.filter { it.str("type") == filter }
        if (items.isEmpty()) {
            Text("Nenhum evento neste filtro ainda.", color = p.muted, fontSize = 13.sp)
        } else {
            items.take(150).forEachIndexed { i, h ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                HistoryRow(h)
            }
            if (items.size > 150) Helper("Mostrando os 150 mais recentes de ${items.size}.")
        }
    }
}

@Composable
fun MonthChartCard(
    title: String,
    data: List<ChartPoint>,
    color: androidx.compose.ui.graphics.Color,
    bars: Boolean,
    extra: @Composable () -> Unit = {},
) {
    val p = LocalPalette.current
    CardBox {
        BoxTitle(title)
        extra()
        if (data.isEmpty() || data.all { it.value == 0.0 }) {
            Text("Ainda sem dados — o gráfico aparece conforme os meses forem passando.", color = p.muted, fontSize = 13.sp)
        } else {
            SeriesChart(data, color, bars)
            if (data.size > 1) {
                val last = data.last().value
                val prev = data[data.size - 2].value
                if (prev > 0 && last > 0) {
                    val pct = (last / prev - 1) * 100
                    Helper(
                        (if (pct >= 0) "▲ " else "▼ ") + Math.abs(pct).toInt() + "% vs mês anterior",
                    )
                }
            }
        }
    }
}
