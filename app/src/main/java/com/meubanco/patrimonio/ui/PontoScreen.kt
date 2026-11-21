package com.meubanco.patrimonio.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*
import java.time.LocalDate

@Composable
fun PontoScreen() {
    val s = state()
    val ctx = LocalContext.current
    val today = s.simDateISO
    val d = isoToDate(today)
    val entry = s.timeClock.find { it.date == today }
    val shift = currentShiftForMonth(s, d.year, d.monthIdx())
    val sh = s.income.shifts[shift]

    fun pickTime(slot: String) {
        val now = java.time.LocalTime.now()
        TimePickerDialog(ctx, { _, h, m -> Store.mutate { savePontoSlot(it, today, slot, "%02d:%02d".format(h, m)) } }, now.hour, now.minute, true).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            H2("⏱ Ponto de hoje")
            Muted(formatSimDate(today))
            if (sh != null) Muted("Turno do mês: ${sh.name} · ${sh.detail} · ${fmtPlain(sh.hours)} h")
            PONTO_SLOTS.forEach { slot ->
                val v = entry?.slot(slot).orEmpty()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Txt(PONTO_SLOT_LABELS[slot] ?: slot); Txt(if (v.isBlank()) "—" else v, bold = true) }
                    if (v.isBlank()) GhostButton("Marcar") { pickTime(slot) }
                    else GhostButton("Remover", danger = true) { Store.mutate { removePontoSlot(it, today, slot) } }
                }
            }
            nextPontoSlot(s, today)?.let { n ->
                PrimaryButton("Marcar agora: ${PONTO_SLOT_LABELS[n]}") {
                    val t = java.time.LocalTime.now()
                    Store.mutate { st -> savePontoSlot(st, today, n, "%02d:%02d".format(t.hour, t.minute)) }
                }
            }
            if (entry != null && entry.hours > 0) KV("Horas de hoje", fmtPlain(entry.hours) + " h")
        }
        Card {
            val ym = ymKey(today); val pm = pontoMonthSummary(s, ym)
            H2("Resumo de ${formatMonthLabel(ym)}")
            KV("Dias trabalhados", pm.days.toString()); KV("Horas totais", fmtPlain(pm.hours) + " h"); KV("Horas extras", fmtPlain(pm.extraHours) + " h")
            GhostButton("Compartilhar espelho de ponto", Modifier.fillMaxWidth()) { shareText(ctx, pontoShareText(s, ym), "Espelho de ponto") }
        }
        Card {
            H2("Extras do mês")
            val extra = pontoExtraHoursForMonth(s, d.year, d.monthIdx())
            KV("Horas extras (ponto)", fmtPlain(Math.round(extra * 100) / 100.0) + " h")
            val inc = computeMonthlyIncome(s, d.year, d.monthIdx())
            KV("Salário com extras (bruto)", money(inc.salaryWithExtras))
            KV("Líquido estimado do mês", money(inc.net), P.green)
        }
        Card {
            H2("Domingos trabalhados")
            val sundays = getSundaysInMonth(d.year, d.monthIdx())
            sundays.forEach { sd ->
                val iso = sd.toString(); val worked = isSundayWorked(s, sd)
                SwitchRow(formatSimDate(iso), worked) { on -> Store.mutate { st -> if (on) markSundayWorked(st, iso) else removeSundayWorked(st, iso) } }
            }
        }
        H2("Registros recentes")
        s.timeClock.takeLast(10).reversed().forEach { e ->
            Card { KV(formatSimDate(e.date), fmtPlain(e.hours) + " h"); Muted("${e.in1.ifBlank { "--" }} · ${e.out1.ifBlank { "--" }} · ${e.in2.ifBlank { "--" }} · ${e.out2.ifBlank { "--" }}") }
        }
    }
}
