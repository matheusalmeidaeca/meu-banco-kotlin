package com.meubanco.nativo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

@Composable
fun LiveText(label: String, initial: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    GField(label, t, { t = it; onValue(it) }, modifier)
}

@Composable
fun IncField(store: Store, st: JSONObject, label: String, key: String, parent: String = "income", modifier: Modifier = Modifier) {
    LiveNum(label, st.sub(parent).optDouble(key, 0.0), modifier) { v ->
        store.update { s ->
            s.sub(parent).put(key, v)
            if (parent == "income" && key == "baseSalary") s.recomputeFgtsBackfill()
        }
    }
}

@Composable
fun DateField(label: String, iso: String, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Text(label, color = p.muted, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(if (iso.isEmpty()) "Escolher data" else ddmmyyyy(iso), Modifier.weight(1f)) { pickDate(ctx) { onPick(it) } }
        if (iso.isNotEmpty()) GhostButton("Limpar", Modifier.weight(0.5f)) { onPick("") }
    }
}

fun ageFromISO(iso: String): Int? {
    if (iso.isEmpty()) return null
    return try {
        val b = LocalDate.parse(iso)
        val t = LocalDate.now()
        var a = t.year - b.year
        if (t.monthValue < b.monthValue || (t.monthValue == b.monthValue && t.dayOfMonth < b.dayOfMonth)) a--
        if (a in 0..129) a else null
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ConfigPanel(store: Store, onImport: () -> Unit, onExport: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var open by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val toggle: (String) -> Unit = { k -> open = if (open == k) "" else k }

    if (st == null) {
        CardBox {
            Text("💾 Dados & backup", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Nenhum dado carregado.", color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("⬆ Importar backup", onClick = onImport)
            Spacer(Modifier.height(8.dp))
            GhostButton("Começar conta nova") { store.createNew() }
        }
        return
    }

    val settings = st.sub("settings")
    val inc = st.sub("income")
    val profile = st.sub("profile")
    val name = settings.str("displayName")
    val idade = ageFromISO(profile.str("birthDateISO"))
    val tipo = inc.optString("incomeType", "empregado")

    // ---- cabeçalho e completude ----
    val checks = listOf(
        "nome" to name.isNotEmpty(),
        "data de nascimento" to profile.str("birthDateISO").isNotEmpty(),
        "sexo" to profile.str("sex").isNotEmpty(),
        "salário ou renda" to (tipo == "autonomo" || (if (tipo == "renda") inc.optDouble("beneficioAmount", 0.0) > 0 else inc.optDouble("baseSalary", 0.0) > 0)),
        "data de admissão" to (tipo != "empregado" || inc.str("hireDateISO").isNotEmpty()),
        "gastos fixos" to st.objList("expenses").any { it.optInt("id") != st.optInt("shoppingLinkedExpenseId", -1) },
        "PIN de segurança" to settings.optBoolean("pinEnabled", false),
        "um backup" to st.str("lastBackupAt").isNotEmpty(),
        "teste de perfil de investidor" to (st.profileLabel() != null && !st.profileExpired()),
    )
    val done = checks.count { it.second }
    val pct = Math.round(done.toDouble() / checks.size * 100).toInt()
    CardBox {
        Text(if (name.isNotEmpty()) name else "Seu perfil", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Agência ${st.sub("account").str("agency")} · Conta ${st.sub("account").str("number")}", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text("Perfil $pct% completo", color = p.text, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        ProgressBar(pct / 100.0, p.emerald)
        val missing = checks.filter { !it.second }.map { it.first }
        Helper(if (missing.isEmpty()) "Perfil completo! 🎉" else "Falta: " + missing.joinToString(", ") + ".")
    }
    Spacer(Modifier.height(12.dp))

    // ---- Perfil ----
    Accordion("👤 Perfil", (if (name.isNotEmpty()) name else "Sem nome") + (if (idade != null) " · $idade anos" else ""), open == "perfil", { toggle("perfil") }) {
        LiveText("Nome", name) { v -> store.update { it.sub("settings").put("displayName", v.trim()) } }
        Spacer(Modifier.height(8.dp))
        DateField("Data de nascimento", profile.str("birthDateISO")) { v ->
            store.update { it.sub("profile").put("birthDateISO", if (v.isEmpty()) JSONObject.NULL else v) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Sexo", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M" to "Masculino", "F" to "Feminino", "outro" to "Outro", "" to "Prefiro não dizer").forEach { (k, l) ->
                GChip(l, profile.str("sex") == k) { store.update { it.sub("profile").put("sex", if (k.isEmpty()) JSONObject.NULL else k) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        val pl = st.profileLabel()
        MoneyRow("Perfil de investidor", if (pl == null) "não feito" else pl + if (st.profileExpired()) " (vencido)" else "")
    }
    Spacer(Modifier.height(10.dp))

    // ---- Renda ----
    val rendaResumo = if (tipo == "renda") "${inc.str("beneficioLabel")}: ${brl(inc.optDouble("beneficioAmount", 0.0))}" else "${brl(inc.optDouble("baseSalary", 0.0))}/mês base"
    Accordion("💼 Renda e contracheque", rendaResumo + if (st.sub("secondaryIncome").optBoolean("enabled", false)) " + renda secundária" else "", open == "renda", { toggle("renda") }) {
        RendaSection(store, st)
    }
    Spacer(Modifier.height(10.dp))

    // ---- Gastos ----
    Accordion("🎉 Gasto livre", "${st.freeCats().size} categoria(s)", open == "gastos", { toggle("gastos") }) {
        Text("Categorias do catálogo (toque para marcar)", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        val cur = st.freeCats()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FREE_SPEND_CATALOG.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { c ->
                        val on = c in cur
                        GChip((if (on) "✓ " else "+ ") + c, on) { store.update { it.toggleFreeCategory(c) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        var newCat by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            GField("Nova categoria", newCat, { newCat = it }, Modifier.weight(1f))
            GhostButton("Adicionar", Modifier.weight(0.6f)) {
                if (newCat.isNotBlank()) {
                    store.update { it.addFreeCategory(newCat) }
                    newCat = ""
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        cur.forEach { c ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(c, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                RowMenu(
                    listOf(
                        MenuAction("🗑 Remover categoria", true) {
                            var ok = false
                            store.update { y -> ok = y.removeFreeCategory(c) }
                            if (!ok) showToast(ctx, "Precisa sobrar pelo menos uma categoria.")
                        },
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LiveNum("Limite mensal de gasto livre (R$, 0 = sem limite)", st.optDouble("freeSpendMonthlyLimit", 0.0)) { v ->
            store.update { it.put("freeSpendMonthlyLimit", Math.max(0.0, v)) }
        }
    }
    Spacer(Modifier.height(10.dp))

    // ---- Segurança ----
    Accordion("🔒 Segurança", if (settings.optBoolean("pinEnabled", false)) "PIN ativo" else "Sem PIN", open == "seg", { toggle("seg") }) {
        Toggle("Ocultar valores em destaque", settings.optBoolean("hideBalance", false), "Saldo e patrimônio aparecem como ••••") { on ->
            store.update { it.sub("settings").put("hideBalance", on) }
        }
        Spacer(Modifier.height(8.dp))
        if (!settings.optBoolean("pinEnabled", false)) {
            var p1 by remember { mutableStateOf("") }
            var p2 by remember { mutableStateOf("") }
            Text("Crie um PIN de 4 a 6 números. Ele será pedido sempre que o app abrir.", color = p.muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(8.dp))
            GField("Novo PIN", p1, { p1 = it.filter { c -> c.isDigit() }.take(6) }, keyboard = KeyboardType.NumberPassword)
            Spacer(Modifier.height(8.dp))
            GField("Confirmar PIN", p2, { p2 = it.filter { c -> c.isDigit() }.take(6) }, keyboard = KeyboardType.NumberPassword)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Ativar PIN") {
                if (p1.length < 4) {
                    showToast(ctx, "O PIN precisa ter de 4 a 6 números.")
                } else if (p1 != p2) {
                    showToast(ctx, "Os PINs não coincidem.")
                } else {
                    store.update { s ->
                        s.sub("settings").put("pinEnabled", true)
                        s.sub("settings").put("pinHash", simpleHash(p1))
                    }
                    p1 = ""
                    p2 = ""
                    showToast(ctx, "PIN ativado")
                }
            }
        } else {
            GhostButton("Desativar PIN") {
                confirm = "Desativar o PIN de acesso?" to {
                    store.update { s ->
                        s.sub("settings").put("pinEnabled", false)
                        s.sub("settings").put("pinHash", JSONObject.NULL)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        IncField(store, st, "Pix: pedir confirmação a partir de (R$, 0 = nunca)", "pixConfirmThreshold", "settings")
        Spacer(Modifier.height(8.dp))
        IncField(store, st, "Pix: limite diário (R$, 0 = sem limite)", "pixDailyLimit", "settings")
    }
    Spacer(Modifier.height(10.dp))

    // ---- Notificações ----
    Accordion("🔔 Avisos do assistente", "Quando avisar", open == "notif", { toggle("notif") }) {
        Toggle("Assistente ligado", settings.optBoolean("assistantEnabled", true), "Mostra avisos e dicas na aba Conta") { on ->
            store.update { it.sub("settings").put("assistantEnabled", on) }
        }
        Spacer(Modifier.height(6.dp))
        IncField(store, st, "Avisar a fatura faltando N dias", "notifyCardDays", "settings")
        Spacer(Modifier.height(8.dp))
        IncField(store, st, "Avisar backup após N dias sem exportar", "notifyBackupDays", "settings")
        Spacer(Modifier.height(8.dp))
        IncField(store, st, "Avisar quando a caixinha chegar a N% da meta", "notifyGoalPct", "settings")
        Spacer(Modifier.height(8.dp))
        IncField(store, st, "Avisar quando o gasto livre chegar a N% do limite", "notifyFreeSpendPct", "settings")
    }
    Spacer(Modifier.height(10.dp))

    // ---- Aparência ----
    Accordion("🎨 Aparência", if (settings.optString("theme", "dark") == "light") "Tema claro" else "Tema escuro", open == "aparencia", { toggle("aparencia") }) {
        Seg(listOf("dark" to "Escuro", "light" to "Claro"), settings.optString("theme", "dark")) { v ->
            store.update { it.sub("settings").put("theme", v) }
        }
    }
    Spacer(Modifier.height(10.dp))

    // ---- Dados ----
    Accordion("💾 Dados & backup", "Importar, exportar e apagar", open == "dados", { toggle("dados") }) {
        Text(
            "Data da simulação: ${ddmmyyyy(st.optString("simDateISO"))}." +
                (if (st.str("lastBackupAt").isNotEmpty()) " Último backup: ${ddmmyyyy(st.str("lastBackupAt").take(10))}." else " Nenhum backup exportado ainda."),
            color = p.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton("⬆ Importar backup", onClick = onImport)
        Spacer(Modifier.height(8.dp))
        GhostButton("⬇ Exportar backup agora", onClick = onExport)
        Spacer(Modifier.height(8.dp))
        Toggle("Lembrar de fazer backup", !settings.optBoolean("autoBackupEnabled", true), "Liga os avisos de backup na aba Conta") { on ->
            store.update { it.sub("settings").put("autoBackupEnabled", !on) }
        }
        Spacer(Modifier.height(14.dp))
        Text("Apagar só uma parte", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        val parts = listOf(
            Triple("historico", "Extrato e histórico", "todo o extrato/histórico"),
            Triple("carteira", "Carteira de investimentos", "toda a carteira de investimentos (ativos e configurações de aporte)"),
            Triple("caixinhas", "Caixinhas", "todas as caixinhas (reservas e metas)"),
            Triple("gastos", "Gastos fixos e lista de compras", "todos os gastos fixos e a lista de compras"),
        )
        parts.forEach { (k, l, desc) ->
            GhostButton("🗑 $l") { confirm = "Isso apaga $desc. Continuar?" to { store.update { it.resetPartial(k) } } }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("⚠ Apagar conta e todos os dados") {
            confirm = "Isso apaga sua conta e TODOS os dados (saldo, investimentos, gastos, caixinhas, histórico) de forma permanente e não pode ser desfeito. Tem certeza?" to { store.resetAll() }
        }
    }

    confirm?.let { (msg, run) ->
        GConfirm(msg, onYes = { run(); confirm = null }, onNo = { confirm = null })
    }
}

// ============================================================
// Renda
// ============================================================

@Composable
fun RendaSection(store: Store, st: JSONObject) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val inc = st.sub("income")
    val sec = st.sub("secondaryIncome")
    val tipo = inc.optString("incomeType", "empregado")

    Text("Tipo de renda", color = p.muted, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Seg(listOf("empregado" to "Empregado", "autonomo" to "Autônomo", "renda" to "Benefício"), tipo) { v ->
        store.update { s ->
            s.sub("income").put("incomeType", v)
            s.recomputeFgtsBackfill()
        }
    }
    Spacer(Modifier.height(12.dp))

    when (tipo) {
        "empregado" -> {
            IncField(store, st, "Salário base (R$)", "baseSalary")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IncField(store, st, "Hora extra (%)", "extraHourPct", modifier = Modifier.weight(1f))
                IncField(store, st, "Domingo trabalhado (R$)", "sundayBonus", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IncField(store, st, "Feriado: horas", "holidayHours", modifier = Modifier.weight(1f))
                IncField(store, st, "Feriado: adicional (%)", "holidayPct", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IncField(store, st, "Feriado: horas noturnas", "holidayNightHours", modifier = Modifier.weight(1f))
                IncField(store, st, "Adicional noturno (%)", "holidayNightPct", modifier = Modifier.weight(1f))
            }
            Helper("Valor de um feriado trabalhado: ${brl(st.holidayDayValue())}")
            Spacer(Modifier.height(8.dp))
            DateField("Data de admissão (usada no FGTS retroativo)", inc.str("hireDateISO")) { v ->
                store.update { s ->
                    s.sub("income").put("hireDateISO", if (v.isEmpty()) JSONObject.NULL else v)
                    s.recomputeFgtsBackfill()
                }
            }
            Spacer(Modifier.height(8.dp))
            Toggle("13º salário", inc.optBoolean("thirteenthEnabled", true), "1ª parcela em 30/nov e 2ª em 20/dez") { on ->
                store.update { it.sub("income").put("thirteenthEnabled", on) }
            }
            Toggle("Férias + 1/3", inc.optBoolean("feriasEnabled", true), "Pagas uma vez por ano, no mês escolhido") { on ->
                store.update { it.sub("income").put("feriasEnabled", on) }
            }
            if (inc.optBoolean("feriasEnabled", true)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MONTH_NAMES.forEachIndexed { i, m ->
                        GChip(m.take(3).replaceFirstChar { it.uppercase() }, inc.optInt("feriasMonth", -1) == i) {
                            store.update { it.sub("income").put("feriasMonth", i) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Turno", color = p.muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Seg(listOf("alternado" to "Alterna por mês", "fixo" to "Fixo"), inc.optString("shiftMode", "alternado")) { v ->
                store.update { it.sub("income").put("shiftMode", v) }
            }
            if (inc.optString("shiftMode", "alternado") == "fixo") {
                Spacer(Modifier.height(6.dp))
                Seg(listOf("manha" to "Manhã", "noite" to "Tarde / Noite"), inc.optString("fixedShift", "noite")) { v ->
                    store.update { it.sub("income").put("fixedShift", v) }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text("Turno do primeiro mês", color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Seg(listOf("manha" to "Manhã", "noite" to "Tarde / Noite"), inc.optString("baseShift", "noite")) { v ->
                    store.update { it.sub("income").put("baseShift", v) }
                }
            }
            Spacer(Modifier.height(8.dp))
            listOf("manha", "noite").forEach { k ->
                val sh = inc.sub("shifts").sub(k)
                Text(if (k == "manha") "Turno da manhã" else "Turno da tarde / noite", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                LiveText("Nome", sh.str("name")) { v -> store.update { it.sub("income").sub("shifts").sub(k).put("name", v) } }
                Spacer(Modifier.height(4.dp))
                LiveText("Horário", sh.str("detail")) { v -> store.update { it.sub("income").sub("shifts").sub(k).put("detail", v) } }
                Spacer(Modifier.height(4.dp))
                LiveNum("Horas do turno", sh.optDouble("hours", 7.0)) { v -> store.update { it.sub("income").sub("shifts").sub(k).put("hours", v) } }
                Spacer(Modifier.height(10.dp))
            }
        }
        "renda" -> {
            LiveText("Nome do benefício", inc.str("beneficioLabel")) { v -> store.update { it.sub("income").put("beneficioLabel", v) } }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IncField(store, st, "Valor (R$)", "beneficioAmount", modifier = Modifier.weight(1f))
                IncField(store, st, "Dia do pagamento", "beneficioPayDay", modifier = Modifier.weight(1f))
            }
        }
        else -> {
            Text("Autônomo: a renda não cai sozinha. Registre o que você receber, quando receber.", color = p.muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(8.dp))
            var label by remember { mutableStateOf("") }
            var value by remember { mutableStateOf("") }
            GField("Descrição", label, { label = it }, placeholder = "Ex.: Serviço para cliente")
            Spacer(Modifier.height(8.dp))
            GField("Valor recebido (R$)", value, { value = it }, keyboard = KeyboardType.Decimal)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Registrar renda") {
                val v = parseNum(value)
                if (v <= 0) {
                    showToast(ctx, "Informe o valor recebido.")
                } else {
                    store.update { s ->
                        s.cash = r2(s.cash + v)
                        val i = s.sub("income")
                        i.put("totalReceived", i.optDouble("totalReceived", 0.0) + v)
                        s.addHistory("renda", (label.ifBlank { "Renda de autônomo" }) + " recebida", v)
                    }
                    label = ""
                    value = ""
                    showToast(ctx, "Renda registrada")
                }
            }
        }
    }

    // ---- renda secundária ----
    Spacer(Modifier.height(14.dp))
    Toggle("Renda secundária", sec.optBoolean("enabled", false), "Uma segunda renda mensal, sem descontos") { on ->
        store.update { it.sub("secondaryIncome").put("enabled", on) }
    }
    if (sec.optBoolean("enabled", false)) {
        LiveText("Nome", sec.str("label")) { v -> store.update { it.sub("secondaryIncome").put("label", v) } }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IncField(store, st, "Valor (R$)", "amount", "secondaryIncome", Modifier.weight(1f))
            IncField(store, st, "Dia do pagamento", "payDay", "secondaryIncome", Modifier.weight(1f))
        }
    }

    // ---- resumo do ciclo + contracheque ----
    Spacer(Modifier.height(14.dp))
    val sim = isoDate(st.optString("simDateISO", todayISO()))
    val year = sim.year
    val month1 = sim.monthValue
    val prevYear = if (month1 == 1) year - 1 else year
    val prevMonth = if (month1 == 1) 12 else month1 - 1
    val benef = tipo == "renda"
    val received: MonthlyIncome
    val working: MonthlyIncome
    if (benef) {
        val amt = r2(Math.max(0.0, inc.optDouble("beneficioAmount", 0.0)))
        received = MonthlyIncome(amt, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0, amt)
        working = received
    } else {
        received = st.computeMonthlyIncome(prevYear, prevMonth)
        working = st.computeMonthlyIncome(year, month1)
    }
    val payDay = nthBusinessDay(year, month1, 5)
    val secMonthly = if (sec.optBoolean("enabled", false)) r2(Math.max(0.0, sec.optDouble("amount", 0.0))) else 0.0
    val totalExpenses = st.objList("expenses").sumOf { it.optDouble("amount", 0.0) }
    val sobra = received.net + secMonthly - totalExpenses
    val planned = (if (st.optBoolean("recurringInvestEnabled", false)) st.optDouble("monthlyContribution", 0.0) else 0.0) +
        st.objList("reserves").sumOf { it.optDouble("monthlyContribution", 0.0) } +
        st.objList("goals").sumOf { if (it.optBoolean("completed", false)) 0.0 else it.optDouble("monthly", 0.0) }
    val sobraFinal = sobra - planned
    CardBox {
        BoxTitle("Resumo do ciclo", "O que entra e sai neste mês")
        MoneyRow(
            (if (benef) inc.str("beneficioLabel") else "Salário") + " recebido este mês (dia " + (if (benef) inc.optInt("beneficioPayDay", 5) else payDay.dayOfMonth) + ")",
            brl(received.net),
            bold = true,
        )
        if (secMonthly > 0) MoneyRow(sec.str("label").ifEmpty { "Renda extra" } + " (dia ${sec.optInt("payDay", 5)})", "+ " + brl(secMonthly), p.emerald)
        MoneyRow("Gastos fixos (1 dia após o salário)", "− " + brl(totalExpenses), p.coral)
        MoneyRow("Sobra antes dos aportes", brl(sobra), bold = true)
        MoneyRow("Aportes planejados (carteira + reservas + metas)", "− " + brl(planned), p.coral)
        MoneyRow("Sobra estimada no caixa livre", brl(sobraFinal), if (sobraFinal < 0) p.coral else p.emerald, bold = true)
        if (!benef) MoneyRow("Sendo trabalhado agora (cai no mês que vem)", brl(working.net), bold = true)
        MoneyRow("FGTS acumulado (não sacável)", brl(st.sub("fgts").optDouble("balance", 0.0)), bold = true)
        if (sobraFinal < 0) WarnBox("Seus aportes planejados somam mais do que a sobra estimada. Quando isso acontece, os aportes são reduzidos automaticamente para caber no caixa.")
    }
    if (tipo != "autonomo") {
        Spacer(Modifier.height(10.dp))
        if (benef) {
            Holerite("Contracheque do mês", inc.str("beneficioLabel") + " · cai todo dia " + inc.optInt("beneficioPayDay", 5), st, received, true)
        } else {
            val nextY = if (month1 == 12) year + 1 else year
            val nextM = if (month1 == 12) 1 else month1 + 1
            Holerite(
                "Contracheque do mês atual (prévia)",
                "Ref. ${MONTH_NAMES[month1 - 1]}/$year · cai dia ${nthBusinessDay(nextY, nextM, 5).dayOfMonth} de ${MONTH_NAMES[nextM - 1]}",
                st, working, false,
            )
            Spacer(Modifier.height(10.dp))
            Holerite(
                "Contracheque do mês passado",
                "Ref. ${MONTH_NAMES[prevMonth - 1]}/$prevYear · caiu dia ${payDay.dayOfMonth}",
                st, received, false,
            )
        }
    }
}

@Composable
fun Holerite(heading: String, comp: String, st: JSONObject, d: MonthlyIncome, benef: Boolean) {
    val p = LocalPalette.current
    val inc = st.sub("income")
    CardBox {
        BoxTitle(heading, comp)
        MoneyRow("Funcionário(a)", st.sub("settings").str("displayName").ifEmpty { "—" })
        if (benef) {
            MoneyRow(inc.str("beneficioLabel").uppercase(), brl(d.net), p.emerald)
            Helper("Sem FGTS — não se aplica a esse tipo de renda.")
        } else {
            MoneyRow("001 SALÁRIO BASE (220:00)", brl(inc.optDouble("baseSalary", 0.0)), p.emerald)
            if (d.extraHoursValue > 0) {
                MoneyRow("400 HORAS EXTRAS ${numText(inc.optDouble("extraHourPct", 50.0))}% (${String.format(Locale("pt", "BR"), "%.1f", d.pontoExtraHours)}h)", brl(d.extraHoursValue), p.emerald)
            }
            if (d.holidaysValue > 0) MoneyRow("410 FERIADO TRABALHADO (${d.holidaysCount} dia(s))", brl(d.holidaysValue), p.emerald)
            MoneyRow("903 INSS", "− " + brl(d.inssValue), p.coral)
            MoneyRow("905 IRRF", "− " + brl(d.irrfValue), p.coral)
            val venc = r2(d.salaryWithExtras + d.holidaysValue)
            MoneyRow("Total de vencimentos", brl(venc))
            MoneyRow("Total de descontos", brl(r2(d.inssValue + d.irrfValue)))
            MoneyRow("Líquido a receber", brl(d.net), p.gold, bold = true)
            MoneyRow("Faixa IRRF", if (d.irrfValue > 0) "Tributável" else "Isento")
            val fg = r2((d.salaryWithExtras + d.holidaysValue + d.sundaysValue) * 0.08)
            Helper("FGTS do mês (8%, não sacável): ${brl(fg)}")
            if (d.sundaysValue > 0) Helper("Domingos trabalhados (${d.sundaysCount}): ${brl(d.sundaysValue)}, pagos à parte por recibo.")
        }
    }
}
