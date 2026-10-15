package com.meubanco.nativo

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

class ObExp(val name: String, val category: String, amount: String) {
    var amount by mutableStateOf(amount)
}

/** Rascunho das respostas do cadastro inicial (só vai pro estado ao final). */
class ObDraft {
    var name by mutableStateOf("")
    var birth by mutableStateOf("")
    var sex by mutableStateOf("")
    var incomeType by mutableStateOf("empregado")
    var hire by mutableStateOf("")
    var income by mutableStateOf("")
    var benLabel by mutableStateOf("")
    var hasSec by mutableStateOf(false)
    var secLabel by mutableStateOf("")
    var secType by mutableStateOf("salario")
    var secAmount by mutableStateOf("")
    var secPayDay by mutableStateOf("5")
    var hasCard by mutableStateOf(true)
    var cardLimit by mutableStateOf("")
    var wantExp by mutableStateOf(false)
    val exps = mutableStateListOf<ObExp>()
    var email by mutableStateOf("")
    var notify by mutableStateOf(true)
    var cash by mutableStateOf("")
}

fun obSteps(d: ObDraft): List<String> {
    val s = mutableListOf("welcome", "name", "birthSex", "incomeType")
    if (d.incomeType == "empregado") s.add("hireDate")
    if (d.incomeType != "autonomo") s.add("income")
    s.add("hasSec")
    if (d.hasSec) s.add("secDetails")
    s.add("hasCard")
    if (d.hasCard) s.add("cardLimit")
    s.add("wantExp")
    if (d.wantExp) s.add("expenses")
    s.add("email")
    s.add("balance")
    s.add("done")
    return s
}

fun ObDraft.loadFrom(st: JSONObject) {
    name = st.sub("settings").str("displayName")
    birth = st.sub("profile").str("birthDateISO")
    sex = st.sub("profile").str("sex")
    val inc = st.sub("income")
    incomeType = inc.optString("incomeType", "empregado")
    hire = inc.str("hireDateISO")
    val sal = inc.optDouble("baseSalary", 0.0)
    income = if (sal > 0) numText(sal) else ""
    benLabel = inc.str("beneficioLabel")
    val card = st.sub("card")
    hasCard = card.optBoolean("enabled", true)
    val lim = card.optDouble("limit", 0.0)
    cardLimit = if (lim > 0) numText(lim) else ""
    val linked = st.optInt("shoppingLinkedExpenseId", -1)
    st.objList("expenses").filter { it.optInt("id") != linked }.forEach { e ->
        exps.add(ObExp(e.str("name"), e.str("category").ifEmpty { "Outros" }, numText(e.optDouble("amount", 0.0))))
    }
}

fun JSONObject.applyOnboarding(d: ObDraft) {
    val set = sub("settings")
    set.put("displayName", d.name.trim())
    if (d.email.isNotBlank()) set.put("backupEmail", d.email.trim())
    set.put("notificationsEnabled", d.notify)
    val pr = sub("profile")
    pr.put("birthDateISO", if (d.birth.isEmpty()) JSONObject.NULL else d.birth)
    pr.put("sex", if (d.sex.isEmpty()) JSONObject.NULL else d.sex)
    val inc = sub("income")
    inc.put("incomeType", d.incomeType)
    when (d.incomeType) {
        "renda" -> {
            inc.put("beneficioLabel", d.benLabel.ifBlank { "Benefício social" })
            inc.put("beneficioAmount", Math.max(0.0, parseNum(d.income)))
        }
        "autonomo" -> {}
        else -> {
            val v = parseNum(d.income)
            if (v > 0) inc.put("baseSalary", v)
            inc.put("hireDateISO", if (d.hire.isEmpty()) JSONObject.NULL else d.hire)
            if (d.hire.isNotEmpty()) inc.put("feriasMonth", isoDate(d.hire).monthValue - 1)
            recomputeFgtsBackfill()
        }
    }
    val sec = sub("secondaryIncome")
    sec.put("enabled", d.hasSec)
    if (d.hasSec) {
        sec.put("label", d.secLabel.ifBlank { "Renda extra" })
        sec.put("type", d.secType)
        sec.put("amount", Math.max(0.0, parseNum(d.secAmount)))
        sec.put("payDay", (d.secPayDay.toIntOrNull() ?: 5).coerceIn(1, 28))
    }
    val card = sub("card")
    card.put("enabled", d.hasCard)
    if (d.hasCard) card.put("limit", Math.max(0.0, parseNum(d.cardLimit)))

    // gastos fixos: o que a pessoa montou vira a lista; quem escolheu "Não" começa sem gastos inventados
    val keep = if (d.wantExp) d.exps.map { it.name } else emptyList()
    val linked = optInt("shoppingLinkedExpenseId", -1)
    val list = objList("expenses").filter { it.optInt("id") == linked || it.str("name") in keep }.toMutableList()
    if (d.wantExp) {
        d.exps.forEach { e ->
            val amt = Math.max(0.0, parseNum(e.amount))
            val t = list.firstOrNull { it.str("name") == e.name }
            if (t != null) {
                t.put("amount", amt)
                t.put("category", e.category)
            } else {
                val id = takeId("nextExpenseId", "expenses")
                list.add(JSONObject().put("id", id).put("name", e.name).put("category", e.category).put("amount", amt))
            }
        }
    }
    putList("expenses", list)
    cash = Math.max(0.0, parseNum(d.cash))
    put("onboardingCompleted", true)
}

@Composable
private fun ObOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(if (selected) p.brandDim else p.cardAlt)
            .border(1.dp, if (selected) p.brand else p.border, shape)
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Text(text, color = p.text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun ObHeader(icon: String, title: String, sub: String) {
    val p = LocalPalette.current
    Text(icon, fontSize = 44.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(title, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (sub.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(sub, color = p.muted, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
fun OnboardingScreen(store: Store) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val d = remember { ObDraft().also { dr -> store.state?.let { dr.loadFrom(it) } } }
    var idx by remember { mutableStateOf(0) }
    val steps = obSteps(d)
    val i = idx.coerceIn(0, steps.size - 1)
    val step = steps[i]

    val finish: () -> Unit = { store.update { it.applyOnboarding(d) } }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> finish() }

    fun next() {
        when (step) {
            "income" -> if (parseNum(d.income) <= 0) {
                showToast(ctx, "Digite um valor de renda pra continuar.")
                return
            }
            "secDetails" -> if (parseNum(d.secAmount) <= 0) {
                showToast(ctx, "Digite o valor dessa renda pra continuar, ou volte e escolha 'Não'.")
                return
            }
        }
        if (i < steps.size - 1) idx = i + 1
    }

    Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
            ProgressBar(i.toDouble() / (steps.size - 1))
            Spacer(Modifier.height(28.dp))
            when (step) {
                "welcome" -> {
                    ObHeader("🏦", "Bem-vindo(a) ao Meu Banco", "Vamos configurar sua conta em menos de 2 minutos — só o essencial. O resto você ajusta quando quiser, em Configurações.")
                }
                "name" -> {
                    ObHeader("👋", "Como podemos te chamar?", "Só pra deixar o app com a sua cara. Pode deixar em branco se preferir.")
                    GField("", d.name, { d.name = it }, placeholder = "Seu nome")
                }
                "birthSex" -> {
                    ObHeader("🎂", "Mais alguns detalhes", "Opcional — pode deixar em branco se preferir.")
                    DateField("Data de nascimento", d.birth) { d.birth = it }
                    Spacer(Modifier.height(12.dp))
                    Text("Sexo", color = p.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("" to "Prefiro não informar", "M" to "Masculino", "F" to "Feminino", "outro" to "Outro").forEach { (k, l) ->
                            GChip(l, d.sex == k) { d.sex = k }
                        }
                    }
                }
                "incomeType" -> {
                    ObHeader("💼", "Qual seu tipo de renda?", "Isso muda o que o app pergunta a seguir — e algumas telas só fazem sentido pra quem tem carteira assinada.")
                    ObOption("Empregado(a) com carteira assinada", d.incomeType == "empregado") { d.incomeType = "empregado" }
                    ObOption("Autônomo(a) / renda variável", d.incomeType == "autonomo") { d.incomeType = "autonomo" }
                    ObOption("Benefício ou renda fixa (ex.: Bolsa Família, aposentadoria)", d.incomeType == "renda") { d.incomeType = "renda" }
                }
                "hireDate" -> {
                    ObHeader("📅", "Quando você foi admitido(a)?", "A data em que sua carteira de trabalho foi assinada. Usamos isso pra estimar o FGTS de meses anteriores e acertar o mês das suas férias. Pode deixar em branco se preferir.")
                    DateField("Data de admissão", d.hire) { d.hire = it }
                }
                "income" -> {
                    if (d.incomeType == "renda") {
                        ObHeader("💵", "Sua renda mensal", "Ex.: Bolsa Família, bicos, freelas — o que for. Sem INSS, IRRF ou 13º, já que não se aplicam aqui.")
                        GField("Nome do benefício (opcional)", d.benLabel, { d.benLabel = it }, placeholder = "Ex.: Bolsa Família")
                        Spacer(Modifier.height(8.dp))
                    } else {
                        ObHeader("💵", "Qual sua renda mensal?", "O salário base que você recebe todo mês, editável depois em Configurações. Horas extras, feriados e outros detalhes você configura por lá também.")
                    }
                    GField("Valor (R$)", d.income, { d.income = it }, keyboard = KeyboardType.Decimal)
                }
                "hasSec" -> {
                    ObHeader("👨‍👩‍👧", "Há outra renda na família?", "O salário ou benefício de outra pessoa da casa, pra somar no planejamento.")
                    ObOption("Não", !d.hasSec) { d.hasSec = false }
                    ObOption("Sim", d.hasSec) { d.hasSec = true }
                }
                "secDetails" -> {
                    ObHeader("➕", "Essa outra renda", "Um valor líquido mensal fixo — sem cálculo detalhado de INSS/IRRF pra essa segunda renda.")
                    GField("Nome", d.secLabel, { d.secLabel = it }, placeholder = "Ex.: Salário da esposa")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GField("Valor (R$)", d.secAmount, { d.secAmount = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                        GField("Dia do pagamento", d.secPayDay, { d.secPayDay = it.filter { c -> c.isDigit() }.take(2) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                    }
                }
                "hasCard" -> {
                    ObHeader("💳", "Você tem cartão de crédito?", "Se não tiver, a gente nem mostra as opções de cartão no app.")
                    ObOption("Sim", d.hasCard) { d.hasCard = true }
                    ObOption("Não", !d.hasCard) { d.hasCard = false }
                }
                "cardLimit" -> {
                    ObHeader("💳", "Qual o limite do seu cartão?", "Dia de fechamento e vencimento você ajusta depois, em Configurações. Se tiver mais de um cartão, também dá pra adicionar por lá.")
                    GField("Limite (R$)", d.cardLimit, { d.cardLimit = it }, keyboard = KeyboardType.Decimal)
                }
                "wantExp" -> {
                    ObHeader("🧾", "Deseja adicionar seus gastos fixos?", "Aluguel, contas, internet — dá pra fazer isso agora ou deixar pra depois, em Gastos.")
                    ObOption("Agora", d.wantExp) { d.wantExp = true }
                    ObOption("Deixar pra depois", !d.wantExp) { d.wantExp = false }
                }
                "expenses" -> {
                    ObHeader("🧾", "Seus gastos fixos", "Ajuste os valores, remova o que não é seu e toque numa opção do catálogo pra adicionar. Dá pra mudar tudo depois, em Gastos.")
                    d.exps.toList().forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.name, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            GField("", e.amount, { e.amount = it }, Modifier.weight(0.8f), placeholder = "R$", keyboard = KeyboardType.Decimal)
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { d.exps.remove(e) }.padding(10.dp)) {
                                Text("✕", color = p.coral, fontSize = 15.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Adicionar do catálogo", color = p.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    EXPENSE_CATALOG.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                            pair.forEach { c ->
                                val has = d.exps.any { it.name == c.name }
                                GChip((if (has) "✓ " else "+ ") + c.name, has) {
                                    if (!has) d.exps.add(ObExp(c.name, c.category, numText(c.value)))
                                }
                            }
                        }
                    }
                }
                "email" -> {
                    ObHeader("✉️", "Onde guardar seus backups?", "Seus dados ficam só neste celular. Informe um e-mail e, quando quiser, o app abre o Gmail com o backup anexado pra você enviar a si mesmo. Opcional.")
                    GField("Seu e-mail", d.email, { d.email = it.trim() }, placeholder = "voce@email.com", keyboard = KeyboardType.Email)
                }
                "balance" -> {
                    ObHeader("💰", "Quanto você tem hoje na conta?", "O saldo atual da sua conta corrente, pra começar do jeito certo.")
                    GField("Saldo (R$)", d.cash, { d.cash = it }, keyboard = KeyboardType.Decimal)
                }
                else -> {
                    ObHeader("🎉", "Tudo pronto" + (if (d.name.isNotBlank()) ", ${d.name.trim()}" else "") + "!", "Confira o resumo antes de entrar:")
                    CardBox {
                        MoneyRow("Tipo de renda", when (d.incomeType) { "empregado" -> "Empregado(a)"; "autonomo" -> "Autônomo(a)"; else -> "Benefício" })
                        if (d.incomeType != "autonomo") MoneyRow("Renda mensal", brl(parseNum(d.income)))
                        if (d.hasSec) MoneyRow("Outra renda", brl(parseNum(d.secAmount)))
                        MoneyRow("Cartão de crédito", if (d.hasCard) "Limite ${brl(parseNum(d.cardLimit))}" else "não tenho")
                        MoneyRow("Gastos fixos", if (d.wantExp) "${d.exps.size} item(ns)" else "depois")
                        MoneyRow("Saldo inicial", brl(parseNum(d.cash)))
                        if (d.email.isNotBlank()) MoneyRow("E-mail de backup", d.email)
                    }
                    Spacer(Modifier.height(10.dp))
                    Toggle("Receber avisos", d.notify, "Fatura chegando, ponto, DARF e lembrete de backup — 1 aviso por dia, às 9h") { d.notify = it }
                }
            }
            Spacer(Modifier.height(22.dp))
            if (step == "done") {
                PrimaryButton("Entrar no app") {
                    if (d.notify && Build.VERSION.SDK_INT >= 33 && !hasPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        finish()
                    }
                }
            } else {
                PrimaryButton(if (step == "welcome") "Começar" else "Continuar →") { next() }
            }
            if (i > 0) {
                Spacer(Modifier.height(8.dp))
                GhostButton("‹ Voltar") { idx = i - 1 }
            }
        }
    }
}
