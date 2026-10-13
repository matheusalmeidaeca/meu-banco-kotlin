package com.meubanco.nativo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

// ============================================================
// Lógica (equivale às funções de ponto do app HTML)
// ============================================================

val SLOTS = listOf("in1", "out1", "in2", "out2")
val SLOT_LABELS = mapOf(
    "in1" to "Entrada",
    "out1" to "Saída (almoço)",
    "in2" to "Volta do almoço",
    "out2" to "Saída",
)
val MONTH_NAMES = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

fun pad2(n: Int): String = n.toString().padStart(2, '0')
fun todayISO(): String = LocalDate.now().toString()
fun nowHM(): String {
    val t = LocalTime.now()
    return "${pad2(t.hour)}:${pad2(t.minute)}"
}

fun formatDate(iso: String): String = try {
    val d = LocalDate.parse(iso)
    "${d.dayOfMonth} de ${MONTH_NAMES[d.monthValue - 1]} de ${d.year}"
} catch (e: Exception) {
    iso
}

fun isSundayISO(iso: String): Boolean = try {
    LocalDate.parse(iso).dayOfWeek == DayOfWeek.SUNDAY
} catch (e: Exception) {
    false
}

fun fmtH(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

fun parseHM(hm: String): Int? {
    val parts = hm.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun span(start: String, end: String): Double {
    val a = parseHM(start) ?: return 0.0
    var b = parseHM(end) ?: return 0.0
    if (b <= a) b += 1440 // saída no dia seguinte (turno cruzando a meia-noite)
    return (b - a) / 60.0
}

fun computeHours(in1: String, out1: String, in2: String, out2: String): Double {
    val total = span(in1, out1) + span(in2, out2)
    return Math.round(total * 100) / 100.0
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.clockList(): MutableList<JSONObject> =
    (optJSONArray("timeClock")?.objects() ?: emptyList()).toMutableList()

fun JSONObject.entryFor(date: String): JSONObject? =
    clockList().firstOrNull { it.optString("date") == date }

fun JSONObject.sundays(): List<String> {
    val a = optJSONArray("sundaysWorked") ?: return emptyList()
    return (0 until a.length()).map { a.optString(it) }
}

fun JSONObject.nextSlot(date: String): String? {
    val e = entryFor(date) ?: return "in1"
    for (s in SLOTS) if (e.optString(s).isEmpty()) return s
    return null
}

private fun JSONObject.putClock(list: List<JSONObject>) {
    val sorted = list.sortedByDescending { it.optString("date") }.take(500)
    put("timeClock", JSONArray(sorted))
}

private fun recompute(e: JSONObject) {
    e.put("hours", computeHours(e.optString("in1"), e.optString("out1"), e.optString("in2"), e.optString("out2")))
}

fun JSONObject.savePontoSlot(date: String, field: String, time: String) {
    val list = clockList()
    val e = list.firstOrNull { it.optString("date") == date } ?: JSONObject().also {
        it.put("date", date)
        for (s in SLOTS) it.put(s, "")
        it.put("hours", 0.0)
        list.add(it)
    }
    e.put(field, time)
    recompute(e)
    putClock(list)
}

fun JSONObject.removePontoSlot(date: String, field: String) {
    val list = clockList()
    val e = list.firstOrNull { it.optString("date") == date } ?: return
    e.put(field, "")
    recompute(e)
    if (SLOTS.all { e.optString(it).isEmpty() }) list.remove(e)
    putClock(list)
}

fun JSONObject.removePontoDay(date: String) {
    putClock(clockList().filter { it.optString("date") != date })
}

fun JSONObject.markSunday(iso: String) {
    put("sundaysWorked", JSONArray((sundays() + iso).distinct().sorted()))
}

fun JSONObject.removeSunday(iso: String) {
    put("sundaysWorked", JSONArray(sundays().filter { it != iso }))
}

/** Turno do mês: "manha" ou "noite" (escala fixa ou alternada mês a mês). */
fun shiftForMonth(inc: JSONObject, year: Int, month1: Int): String {
    if (inc.optString("shiftMode") == "fixo") return inc.optString("fixedShift", "noite")
    val baseShift = inc.optString("baseShift", "noite")
    val parts = inc.optString("baseMonthKey").split("-")
    val by = parts.getOrNull(0)?.toIntOrNull()
    val bm = parts.getOrNull(1)?.toIntOrNull()
    val diff = if (by != null && bm != null) (year - by) * 12 + (month1 - bm) else 0
    val sameParity = ((diff % 2) + 2) % 2 == 0
    val other = if (baseShift == "manha") "noite" else "manha"
    return if (sameParity) baseShift else other
}

fun scheduledHours(inc: JSONObject, shift: String): Double =
    inc.optJSONObject("shifts")?.optJSONObject(shift)?.optDouble("hours", 7.0) ?: 7.0

fun extraHoursFor(inc: JSONObject, e: JSONObject): Double = try {
    val d = LocalDate.parse(e.optString("date"))
    val sched = scheduledHours(inc, shiftForMonth(inc, d.year, d.monthValue))
    Math.max(0.0, e.optDouble("hours", 0.0) - sched)
} catch (ex: Exception) {
    0.0
}

fun pickTime(ctx: Context, initial: String, onPicked: (String) -> Unit) {
    val h = initial.split(":").getOrNull(0)?.toIntOrNull() ?: 8
    val m = initial.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(ctx, { _, hh, mm -> onPicked("${pad2(hh)}:${pad2(mm)}") }, h, m, true).show()
}

fun pickDate(ctx: Context, onPicked: (String) -> Unit) {
    val d = LocalDate.now()
    DatePickerDialog(
        ctx,
        { _, y, m, day -> onPicked("%04d-%02d-%02d".format(y, m + 1, day)) },
        d.year,
        d.monthValue - 1,
        d.dayOfMonth,
    ).show()
}

// ============================================================
// Tela
// ============================================================

class HistItem(val date: String, val entry: JSONObject?)

class MenuAction(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
fun PontoPanel(store: Store) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var showOther by rememberSaveable { mutableStateOf(false) }
    var pendingSunday by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val st = store.current()
    if (st == null) {
        CardBox {
            BoxTitle("Registro de ponto")
            Text(
                "Importe o backup do app antigo (em Configurações) para usar o ponto.",
                color = p.muted,
                fontSize = 13.sp,
            )
        }
        return
    }

    val inc = st.optJSONObject("income") ?: JSONObject()
    val today = todayISO()
    val date = target ?: today
    val sundayList = st.sundays()
    val sundayMode = isSundayISO(today) && target == null

    if (sundayMode) {
        CardBox {
            BoxTitle("Hoje é domingo", "Só aparece no próprio dia · o ponto eletrônico fica de fora")
            SundayAsk(
                worked = today in sundayList,
                pending = today !in sundayList && pendingSunday == today,
                onYes = { if (today !in sundayList) pendingSunday = today },
                onNo = {
                    pendingSunday = null
                    store.update { it.removeSunday(today) }
                },
                onConfirm = {
                    pendingSunday = null
                    store.update { it.markSunday(today) }
                },
            )
        }
    } else {
        CardBox {
            BoxTitle("Registro de ponto", (if (date == today) "Hoje · " else "") + formatDate(date))

            if (target != null && isSundayISO(date)) {
                SundayAsk(
                    worked = date in sundayList,
                    pending = date !in sundayList && pendingSunday == date,
                    onYes = { if (date !in sundayList) pendingSunday = date },
                    onNo = {
                        pendingSunday = null
                        store.update { it.removeSunday(date) }
                    },
                    onConfirm = {
                        pendingSunday = null
                        store.update { it.markSunday(date) }
                    },
                )
            } else {
                val entry = st.entryFor(date)
                val next = st.nextSlot(date)

                // Horários já registrados
                SLOTS.filter { !entry?.optString(it).isNullOrEmpty() }.forEach { s ->
                    val time = entry?.optString(s).orEmpty()
                    SlotRow(
                        label = SLOT_LABELS[s].orEmpty(),
                        time = time,
                        menu = listOf(
                            MenuAction("✏️ Corrigir horário") {
                                pickTime(ctx, time) { t -> store.update { it.savePontoSlot(date, s, t) } }
                            },
                            MenuAction("🗑 Excluir horário", danger = true) {
                                confirm = "Excluir este horário?" to {
                                    store.update { it.removePontoSlot(date, s) }
                                }
                            },
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Etapas (1 a 4)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SLOTS.forEachIndexed { i, s ->
                        val done = !entry?.optString(s).isNullOrEmpty()
                        val current = !done && s == next
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (done) p.emerald else Color.Transparent)
                                    .border(2.dp, if (done) p.emerald else if (current) p.brand else p.border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (done) "✓" else "${i + 1}",
                                    color = if (done) Color.White else if (current) p.brand else p.mutedDim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                SLOT_LABELS[s].orEmpty(),
                                color = if (current) p.text else p.mutedDim,
                                fontSize = 9.5.sp,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (next == null) {
                    val h = entry?.optDouble("hours", 0.0) ?: 0.0
                    Text("✓ Dia completo — ${fmtH(h)}h trabalhadas.", color = p.muted, fontSize = 13.sp)
                } else {
                    var manual by remember(date, next) { mutableStateOf(nowHM()) }
                    Text("Agora: ${SLOT_LABELS[next]}", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    val fieldShape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .background(p.bgSoft)
                            .border(1.dp, p.borderSoft, fieldShape)
                            .clickable { pickTime(ctx, manual) { t -> manual = t } }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⏰  $manual", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("⏱ Bater agora", Modifier.weight(1f)) {
                            val t = nowHM()
                            store.update { it.savePontoSlot(date, next, t) }
                        }
                        GhostButton("Registrar este horário", Modifier.weight(1f)) {
                            val t = manual
                            confirm = "${SLOT_LABELS[next]} às $t. Está correto?" to {
                                store.update { it.savePontoSlot(date, next, t) }
                            }
                        }
                    }
                }
            }

            // Lançar um dia anterior
            Spacer(Modifier.height(12.dp))
            GhostButton("Lançar um dia anterior") {
                showOther = !showOther
                if (!showOther) target = null
            }
            if (showOther) {
                Spacer(Modifier.height(10.dp))
                Text("Dia", color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                val fieldShape = RoundedCornerShape(10.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(fieldShape)
                        .background(p.bgSoft)
                        .border(1.dp, p.borderSoft, fieldShape)
                        .clickable { pickDate(ctx) { d -> target = d } }
                        .padding(vertical = 14.dp, horizontal = 14.dp),
                ) {
                    Text(
                        "📅  " + (target?.let { formatDate(it) } ?: "Escolher o dia"),
                        color = p.text,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // Histórico de ponto
    val hist = (
        st.clockList().map { HistItem(it.optString("date"), it) } +
            sundayList.map { HistItem(it, null) }
        ).sortedByDescending { it.date }.take(15)

    CardBox {
        BoxTitle("Histórico de ponto", "O que passar do turno vira hora extra automaticamente")
        if (hist.isEmpty()) {
            Text("Nenhum ponto registrado ainda.", color = p.muted, fontSize = 13.sp)
        } else {
            hist.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.borderSoft))
                val e = item.entry
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (e == null) {
                        Text(
                            "${formatDate(item.date)} — ☀️ Domingo trabalhado",
                            color = p.text,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        RowMenu(
                            listOf(
                                MenuAction("🗑 Remover domingo", danger = true) {
                                    confirm = "Remover o registro deste domingo trabalhado?" to {
                                        store.update { it.removeSunday(item.date) }
                                    }
                                },
                            ),
                        )
                    } else {
                        val hrs = e.optDouble("hours", 0.0)
                        val extra = extraHoursFor(inc, e)
                        Text(
                            "${formatDate(item.date)} — ${fmtH(hrs)}h" + if (extra > 0) " (+${fmtH(extra)}h extra)" else "",
                            color = p.text,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        RowMenu(
                            listOf(
                                MenuAction("🗑 Excluir o dia", danger = true) {
                                    confirm = "Excluir o registro de ponto deste dia?" to {
                                        store.update { it.removePontoDay(item.date) }
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "A comparação é com as horas padrão do turno daquele mês — o excedente entra como hora extra real no contracheque.",
            color = p.mutedDim,
            fontSize = 11.sp,
        )
    }

    confirm?.let { (msg, run) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = p.card,
            text = { Text(msg, color = p.text, fontSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = {
                    run()
                    confirm = null
                }) { Text("Confirmar", color = p.brand) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancelar", color = p.muted) }
            },
        )
    }
}

@Composable
fun BoxTitle(title: String, sub: String? = null) {
    val p = LocalPalette.current
    Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    if (sub != null) {
        Spacer(Modifier.height(2.dp))
        Text(sub, color = p.muted, fontSize = 11.5.sp)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SundayAsk(
    worked: Boolean,
    pending: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onConfirm: () -> Unit,
) {
    val p = LocalPalette.current
    val yesActive = worked || pending
    Text(
        "Você trabalhou neste domingo? Ele é pago à parte, por recibo, e não entra no contracheque.",
        color = p.muted,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (yesActive) PrimaryButton("Sim", Modifier.weight(1f), onYes) else GhostButton("Sim", Modifier.weight(1f), onYes)
        if (yesActive) GhostButton("Não", Modifier.weight(1f), onNo) else PrimaryButton("Não", Modifier.weight(1f), onNo)
    }
    if (pending) {
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Confirmar", onClick = onConfirm)
    } else if (worked) {
        Spacer(Modifier.height(8.dp))
        Text("✓ Domingo trabalhado registrado no histórico de ponto.", color = p.muted, fontSize = 13.sp)
    }
}

@Composable
fun SlotRow(label: String, time: String, menu: List<MenuAction>) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.bgSoft)
            .border(1.dp, p.borderSoft, shape)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(time, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(4.dp))
        RowMenu(menu)
    }
}

@Composable
fun RowMenu(items: List<MenuAction>) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Text("⋮", color = p.muted, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(p.card),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, color = if (item.danger) p.coral else p.text) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
