package com.meubanco.nativo

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.Locale

val CATEGORY_LABELS = mapOf("acao" to "Ações", "tijolo" to "FIIs Tijolo", "papel" to "FIIs Papel")

private fun pctText(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

@Composable
fun ColorDonut(values: List<Pair<Double, Color>>, size: Int = 118) {
    val total = values.sumOf { it.first }
    if (total <= 0) return
    Canvas(Modifier.size(size.dp)) {
        val stroke = this.size.minDimension * 0.16f
        val dia = this.size.minDimension - stroke
        var start = -90f
        values.forEach { (v, c) ->
            val sweep = (v / total * 360.0).toFloat()
            drawArc(
                color = c,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(dia, dia),
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

@Composable
fun RiskTag(label: String) {
    val p = LocalPalette.current
    val c = when (label) {
        "Baixo", "Conservador" -> p.emerald
        "Moderado" -> p.gold
        else -> p.coral
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape).border(1.dp, c, shape).padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(label, color = c, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
}

/** Campo numérico que grava a cada digitação. */
@Composable
fun LiveNum(label: String, initial: Double, modifier: Modifier = Modifier, onValue: (Double) -> Unit) {
    var t by remember { mutableStateOf(numText(initial)) }
    GField(label, t, { t = it; onValue(parseNum(it)) }, modifier, keyboard = KeyboardType.Decimal)
}

@Composable
fun CarteiraPanel(store: Store) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var sheet by rememberSaveable { mutableStateOf("") }
    var tradeId by rememberSaveable { mutableStateOf("") }
    var tradeSell by rememberSaveable { mutableStateOf(false) }
    var cat by rememberSaveable { mutableStateOf("todos") }
    var search by rememberSaveable { mutableStateOf("") }
    var editPrices by rememberSaveable { mutableStateOf(false) }

    if (st == null) {
        CardBox {
            BoxTitle("Carteira de investimentos")
            Text("Importe um backup (ou comece uma conta nova) na aba Conta.", color = p.muted, fontSize = 13.sp)
        }
        return
    }

    val assets = st.assetList()
    val owned = assets.filter { it.optInt("quantity") > 0 }
    val openTrade: (String, Boolean) -> Unit = { id, sell -> tradeId = id; tradeSell = sell; sheet = "trade" }

    // ---- contas ----
    CardBox {
        BoxTitle("Contas de investimento", "A conta principal e a de investimentos são separadas")
        MoneyRow("Conta principal", st.maskBrl(st.cash))
        MoneyRow("Conta de investimentos", st.maskBrl(st.leftover), p.gold, bold = true)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("↔ Transferir", Modifier.weight(1f)) { sheet = "transfer" }
            PrimaryButton("Investir", Modifier.weight(1f)) {
                openTrade(owned.firstOrNull()?.opt("id")?.toString() ?: assets.firstOrNull()?.opt("id")?.toString() ?: "", false)
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- automação mensal ----
    var contrib by remember { mutableStateOf(numText(st.optDouble("monthlyContribution", 0.0))) }
    var withdraw by remember { mutableStateOf(numText(st.optDouble("monthlyWithdrawal", 0.0))) }
    var day by remember { mutableStateOf(st.optInt("contributionDay", 7).toString()) }
    var fee by remember { mutableStateOf(numText(st.sub("settings").optDouble("brokerageFee", 0.0))) }
    CardBox {
        BoxTitle("Rotina mensal", "Dividendos, aporte e compra automática acontecem no dia escolhido")
        Toggle("Investimento recorrente", st.optBoolean("recurringInvestEnabled", false), "Move o aporte da conta principal e compra cotas") { on ->
            store.update { it.put("recurringInvestEnabled", on) }
        }
        Toggle("Reinvestir dividendos", st.optBoolean("reinvestDividends", false), "Os dividendos entram na próxima compra automática") { on ->
            store.update { it.put("reinvestDividends", on) }
        }
        Spacer(Modifier.height(6.dp))
        GField("Aporte mensal (R$)", contrib, { contrib = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        GField("Retirada mensal para a conta principal (R$)", withdraw, { withdraw = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GField("Dia do mês (1–28)", day, { day = it.filter { c -> c.isDigit() }.take(2) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            GField("Corretagem por compra (R$)", fee, { fee = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Salvar rotina") {
            store.update {
                it.put("monthlyContribution", Math.max(0.0, parseNum(contrib)))
                it.put("monthlyWithdrawal", Math.max(0.0, parseNum(withdraw)))
                it.put("contributionDay", (day.toIntOrNull() ?: 7).coerceIn(1, 28))
                it.sub("settings").put("brokerageFee", Math.max(0.0, parseNum(fee)))
            }
            showToast(ctx, "Rotina salva")
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- alocação ----
    val sums = mutableMapOf("acao" to 0.0, "tijolo" to 0.0, "papel" to 0.0)
    owned.forEach { a ->
        val k = a.optString("category")
        sums[k] = (sums[k] ?: 0.0) + a.optInt("quantity") * a.optDouble("price")
    }
    val slices = listOf(
        Triple("Ações", sums["acao"] ?: 0.0, Color(0xFF4F7CFF)),
        Triple("FIIs de tijolo", sums["tijolo"] ?: 0.0, Color(0xFFD4A24C)),
        Triple("FIIs de papel", sums["papel"] ?: 0.0, Color(0xFF8B6CFF)),
        Triple("Renda fixa (caixinhas)", st.reservesTotalBalance(), Color(0xFF34C98F)),
        Triple("Caixa de investimento", st.leftover, Color(0xFF8996AB)),
    ).filter { it.second > 0.005 }
    val totalSl = slices.sumOf { it.second }
    val riskProf = st.portfolioRiskProfile()
    CardBox {
        BoxTitle("Alocação", if (riskProf != null) "Perfil da sua carteira: $riskProf" else "Diversificação num relance")
        if (totalSl <= 0) {
            Text("Quando você tiver investimentos, a divisão por classe aparece aqui.", color = p.muted, fontSize = 13.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDonut(slices.map { it.second to it.third })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    slices.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(s.third))
                            Spacer(Modifier.width(6.dp))
                            Text(s.first, color = p.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("${Math.round(s.second / totalSl * 100)}%", color = p.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Helper("Perfil calculado pela composição da carteira — não substitui o teste de perfil de investidor.")
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- perfil de investidor ----
    val label = st.profileLabel()
    CardBox {
        BoxTitle("Perfil de investidor", if (label != null) "Seu perfil: $label" else "Descubra o que combina com você")
        if (label == null) {
            Text("São 8 perguntas rápidas. Com o perfil salvo, o app avisa quando você for comprar um ativo mais arriscado do que combina com você.", color = p.muted, fontSize = 12.5.sp)
        } else {
            val info = PROFILE_INFO[label]
            if (info != null) Text(info.text, color = p.text, fontSize = 12.5.sp)
            val cap = st.sub("investorProfile").str("cap")
            if (cap.isNotEmpty()) Helper(cap)
            val port = riskProf
            if (port != null && info != null) {
                val pr = PROFILE_INFO[port]?.rank ?: 0
                if (pr > info.rank) WarnBox("Sua carteira está $port, mais arriscada que o seu perfil ($label).")
                else Helper("Sua carteira ($port) está dentro do seu perfil. ✓", p.emerald)
            }
            val until = st.profileValidUntil()
            if (until != null) Helper((if (st.profileExpired()) "⚠️ Venceu em " else "Válido até ") + ddmmyyyy(until) + ".")
        }
        Spacer(Modifier.height(10.dp))
        GhostButton(if (label == null) "Fazer o teste de perfil" else if (st.profileExpired()) "Refazer o teste (venceu)" else "Refazer o teste") { sheet = "quiz" }
    }
    Spacer(Modifier.height(14.dp))

    // ---- meus ativos ----
    val ownedTotal = owned.sumOf { it.optInt("quantity") * it.optDouble("price") }
    val ownedCost = owned.sumOf { st.costBasisOf(it) }
    val gain = ownedTotal - ownedCost
    CardBox {
        BoxTitle(
            "Meus ativos",
            if (owned.isEmpty()) "Você ainda não investiu em nenhum ativo"
            else "${owned.size} ativo(s) · ${st.maskBrl(ownedTotal)}" + (if (st.hideBalance()) "" else " · " + (if (gain >= 0) "+" else "−") + brl(Math.abs(gain))),
        )
        if (owned.isEmpty()) {
            Text("Quando você comprar cotas, elas aparecem aqui. Escolha um ativo no cardápio abaixo e toque em \"Investir\".", color = p.muted, fontSize = 13.sp)
        }
        owned.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            val q = a.optInt("quantity")
            val price = a.optDouble("price")
            val value = q * price
            val pm = st.avgPriceOf(a)
            val ret = if (pm > 0) (price / pm - 1) * 100 else 0.0
            val id = a.opt("id").toString()
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.optString("ticker"), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        RiskTag(st.riskLabelOf(a))
                    }
                    Text("$q cota(s) · posição ${st.maskBrl(value)}", color = p.muted, fontSize = 12.sp)
                    Text("PM ${brl(pm)} · atual ${brl(price)}", color = p.muted, fontSize = 12.sp)
                    Text("~${st.maskBrl(value * a.optDouble("yieldPct", 0.0) / 100)}/mês em dividendos", color = p.emerald, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (st.hideBalance()) "••••" else (if (ret >= 0) "+" else "−") + pctText(Math.abs(ret)) + "%",
                        color = if (ret >= 0) p.emerald else p.coral,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GChip("Comprar") { openTrade(id, false) }
                        GChip("Vender") { openTrade(id, true) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- impostos ----
    val rep = st.taxReport()
    CardBox {
        BoxTitle("Impostos da carteira", "IR sobre ganho de capital (swing trade)")
        if (rep.isEmpty()) {
            Text("Você ainda não vendeu nada. Quando vender, o IR estimado aparece aqui.", color = p.muted, fontSize = 13.sp)
        } else {
            val pend = rep.filter { it.tax > 0.005 && !it.paid }
            val rg = st.optDouble("realizedGain", 0.0)
            MoneyRow("Lucro/prejuízo realizado", st.maskBrl(rg), if (rg >= 0) p.emerald else p.coral)
            MoneyRow("DARF pendente", if (pend.isEmpty()) "nenhum" else brl(pend.sumOf { it.tax }), if (pend.isEmpty()) null else p.coral)
            if (pend.isNotEmpty()) MoneyRow("Próximo vencimento", ddmmyyyy(pend.first().dueISO))
            val last = rep.last()
            if (last.carryAcao > 0 || last.carryFii > 0) {
                MoneyRow("Prejuízo a compensar", "ações ${brl(last.carryAcao)} · FIIs ${brl(last.carryFii)}")
            }
            Helper("Estimativa de swing trade — não substitui a apuração oficial.")
            Spacer(Modifier.height(8.dp))
            GhostButton("Ver apuração mês a mês") { sheet = "tax" }
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- distribuição da compra automática ----
    CardBox {
        BoxTitle("Compra automática", "Quanto de cada compra vai para cada ativo que você já tem (em %)")
        if (owned.isEmpty()) {
            Text("Você ainda não tem ativos. Faça a primeira compra em \"Investir\".", color = p.muted, fontSize = 13.sp)
        } else {
            owned.forEach { a ->
                val id = a.opt("id").toString()
                androidx.compose.runtime.key(id) {
                    LiveNum("${a.optString("ticker")} · ${a.optString("name")}", a.optDouble("allocPct", 0.0)) { v ->
                        store.update { s -> s.assetById(id)?.put("allocPct", Math.max(0.0, v)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Helper("Se nenhum percentual for definido, o app usa a sobra para comprar o ativo que deixa o saldo mais perto de zero.")
        }
    }
    Spacer(Modifier.height(14.dp))

    // ---- cardápio de ativos ----
    CardBox {
        BoxTitle("Cardápio de ativos", "Ações e FIIs para investir")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GChip("Todos", cat == "todos") { cat = "todos" }
            CATEGORY_LABELS.forEach { (k, l) -> GChip(l, cat == k) { cat = k } }
        }
        Spacer(Modifier.height(8.dp))
        GField("", search, { search = it }, placeholder = "Buscar ativo...")
        Spacer(Modifier.height(8.dp))
        Toggle("Editar preços e yields", editPrices) { editPrices = it }
        var growth by remember { mutableStateOf(numText(st.sub("market").optDouble("simGrowthPct", 9.5))) }
        if (editPrices) {
            GField("Crescimento médio anual simulado dos ativos (%)", growth, {
                growth = it
                store.update { s -> s.sub("market").put("simGrowthPct", parseNum(it)) }
            }, keyboard = KeyboardType.Decimal)
            Spacer(Modifier.height(8.dp))
        }
        val term = search.trim().lowercase()
        val list = assets.filter { a ->
            (cat == "todos" || a.optString("category") == cat) &&
                (term.isEmpty() || (a.optString("ticker") + " " + a.optString("name")).lowercase().contains(term))
        }
        if (list.isEmpty()) Text("Nenhum ativo encontrado.", color = p.muted, fontSize = 13.sp)
        list.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            val id = a.opt("id").toString()
            val up = a.has("_prevPrice") && a.optDouble("price") >= a.optDouble("_prevPrice")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.optString("ticker"), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        RiskTag(st.riskLabelOf(a))
                    }
                    Text(a.optString("name"), color = p.muted, fontSize = 12.sp)
                    Text(
                        (CATEGORY_LABELS[a.optString("category")] ?: "") + (if (a.optInt("quantity") > 0) " · você tem ${a.optInt("quantity")}" else ""),
                        color = p.mutedDim,
                        fontSize = 11.sp,
                    )
                }
                if (!editPrices) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(brl(a.optDouble("price")), color = if (up) p.emerald else p.coral, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("${pctText(a.optDouble("yieldPct", 0.0))}%/mês", color = p.muted, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        GChip("Investir") { openTrade(id, false) }
                    }
                } else {
                    Column(Modifier.width(120.dp)) {
                        androidx.compose.runtime.key(id) {
                            LiveNum("Preço", a.optDouble("price")) { v ->
                                if (v > 0) store.update { s -> s.assetById(id)?.put("price", v) }
                            }
                            Spacer(Modifier.height(4.dp))
                            LiveNum("Yield %/mês", a.optDouble("yieldPct", 0.0)) { v ->
                                store.update { s -> s.assetById(id)?.put("yieldPct", Math.max(0.0, v)) }
                            }
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        "transfer" -> TransferSheet(store) { sheet = "" }
        "trade" -> TradeSheet(store, tradeId, tradeSell) { sheet = "" }
        "tax" -> TaxSheet(store) { sheet = "" }
        "quiz" -> QuizSheet(store) { sheet = "" }
    }
}

@Composable
fun TransferSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    var toInvest by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    val avail = if (st == null) 0.0 else if (toInvest) st.cash else st.leftover
    GSheet("Transferir", onClose) {
        Seg(listOf("in" to "Para investimentos", "out" to "Para a principal"), if (toInvest) "in" else "out") { toInvest = it == "in" }
        Spacer(Modifier.height(10.dp))
        Text("Disponível pra transferir: ${brl(Math.max(0.0, avail))}", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        GField("Valor (R$)", amount, { amount = it }, keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Confirmar transferência") {
            if (store.act(ctx, "Transferência feita") { it.transferInvestAccounts(toInvest, parseNum(amount)) }) onClose()
        }
    }
}

@Composable
fun TradeSheet(store: Store, assetId: String, startSell: Boolean, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var pickedId by remember { mutableStateOf(assetId) }
    var sell by remember { mutableStateOf(startSell) }
    var qtyText by remember { mutableStateOf("1") }
    var review by remember { mutableStateOf(false) }
    var warn by remember { mutableStateOf<String?>(null) }
    val st = store.current()
    val a = st?.assetById(pickedId)

    GSheet(if (review) "Revisar ordem" else "Boleta", onClose) {
        if (st == null) return@GSheet
        if (!review) {
            Text("Escolha o ativo", color = p.muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                st.assetList().forEach { x ->
                    val id = x.opt("id").toString()
                    GChip(x.optString("ticker"), id == pickedId) { pickedId = id }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (a == null) {
            Text("Nenhum ativo selecionado.", color = p.muted, fontSize = 13.sp)
            return@GSheet
        }
        val fee = st.brokerageFee()
        val price = a.optDouble("price")
        val have = a.optInt("quantity")
        val maxQty = if (sell) have else Math.max(0, Math.floor((st.leftover - fee) / price).toInt())
        val qty = (qtyText.toIntOrNull() ?: 1).coerceAtLeast(1)
        val gross = r2(qty * price)
        val total = if (sell) r2(Math.max(0.0, gross - fee)) else r2(gross + fee)
        val after = if (sell) r2(st.leftover + total) else r2(st.leftover - total)

        if (!review) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${a.optString("ticker")} · ${a.optString("name")}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Você tem $have cota(s) · risco ${st.riskLabelOf(a)}", color = p.muted, fontSize = 12.sp)
                }
                Text(brl(price), color = p.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(10.dp))
            Seg(listOf("buy" to "Compra", "sell" to "Venda"), if (sell) "sell" else "buy") { sell = it == "sell" }
            Spacer(Modifier.height(10.dp))
            Text("Saldo na conta de investimentos: ${brl(st.leftover)}", color = p.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBtn("−") { qtyText = Math.max(1, qty - 1).toString() }
                GField("Quantidade", qtyText, { qtyText = it.filter { c -> c.isDigit() }.take(6) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                MiniBtn("+") { qtyText = (qty + 1).toString() }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GChip("1") { qtyText = "1" }
                GChip("10") { qtyText = "10" }
                GChip("100") { qtyText = "100" }
                GChip("Máx ($maxQty)") { qtyText = Math.max(1, maxQty).toString() }
            }
            Spacer(Modifier.height(10.dp))
            MoneyRow(if (sell) "Você recebe (já sem corretagem)" else "Total com corretagem", brl(total), if (sell) p.emerald else p.gold, bold = true)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Revisar ordem") {
                if (!sell && qty > maxQty) {
                    showToast(ctx, "Saldo insuficiente na conta de investimentos. Transfira mais da conta principal.")
                } else if (sell && qty > have) {
                    showToast(ctx, "Você só tem $have cota(s) de ${a.optString("ticker")}.")
                } else {
                    review = true
                }
            }
        } else {
            Text(brl(total), color = if (sell) p.emerald else p.gold, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            MoneyRow("Operação", if (sell) "Venda" else "Compra")
            MoneyRow("Ativo", "${a.optString("ticker")} · ${a.optString("name")}")
            MoneyRow("Quantidade", "$qty cota(s)")
            MoneyRow("Preço por cota", brl(price))
            MoneyRow("Corretagem", brl(fee))
            var note: String
            if (sell) {
                val est = st.estimateSaleTax(pickedId, qty)
                if (est != null) {
                    MoneyRow("Resultado estimado", (if (est.gain >= 0) "+" else "−") + brl(Math.abs(est.gain)), if (est.gain >= 0) p.emerald else p.coral)
                    MoneyRow("IR estimado desta venda", if (est.delta > 0.005) brl(est.delta) else brl(0.0))
                    note = when {
                        est.gain < 0 -> "Prejuízo: sem IR agora — ele fica guardado pra compensar ganhos futuros da mesma classe (${if (est.cls == "acao") "ações" else "FIIs"})."
                        est.cls == "acao" && est.after.exempt -> "Isento: suas vendas de ações no mês somam ${brl(est.after.salesAcao)} (limite de R$ 20 mil)."
                        est.cls == "acao" -> "Vendas de ações acima de R$ 20 mil no mês: a isenção some e os 15% valem sobre todo o ganho do mês. DARF até ${ddmmyyyy(est.after.dueISO)}."
                        else -> "FIIs: 20% sobre o ganho, sem isenção. DARF até ${ddmmyyyy(est.after.dueISO)}."
                    }
                    note += " O DARF fica pendente em \"Impostos da carteira\" — você paga lá."
                } else {
                    note = ""
                }
            } else {
                val w = st.suitabilityWarning(a)
                note = w.ifEmpty { "Confira o ativo e a quantidade antes de confirmar." }
            }
            MoneyRow("Saldo na conta de investimentos depois", brl(after))
            if (note.isNotEmpty()) Helper(note, if (note.startsWith("⚠️")) p.gold else null)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (sell) "Confirmar venda" else "Confirmar compra") {
                val w = if (sell) "" else st.suitabilityWarning(a)
                if (w.contains("acima do seu perfil")) {
                    warn = w.replace("⚠️ ", "") + "\n\nQuer comprar mesmo assim?"
                } else {
                    val ok = if (sell) {
                        store.act(ctx, "Venda realizada") { it.sellShares(pickedId, qty) }
                    } else {
                        store.act(ctx, "Compra realizada") { it.manualInvest(pickedId, qty) }
                    }
                    if (ok) onClose()
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("← Voltar") { review = false }
        }
    }
    warn?.let { msg ->
        GConfirm(
            msg,
            onYes = {
                warn = null
                if (store.act(ctx, "Compra realizada") { it.manualInvest(pickedId, (qtyText.toIntOrNull() ?: 1).coerceAtLeast(1)) }) onClose()
            },
            onNo = { warn = null },
        )
    }
}

@Composable
fun TaxSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.current()
    GSheet("Apuração de IR", onClose) {
        if (st == null) return@GSheet
        val rep = st.taxReport().reversed()
        if (rep.isEmpty()) {
            Text("Sem vendas até agora.", color = p.muted, fontSize = 13.sp)
            return@GSheet
        }
        Text("Apuração mês a mês das suas vendas. O DARF (código 6015) vence no último dia útil do mês seguinte.", color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        rep.forEach { r ->
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(monthShort(r.ym), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val (txt, col) = when {
                        r.tax > 0.005 && r.paid -> "PAGO" to p.emerald
                        r.tax > 0.005 -> "A PAGAR" to p.coral
                        else -> "SEM IR" to p.muted
                    }
                    Text(txt, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                if (r.salesAcao > 0) {
                    MoneyRow("Vendas de ações", brl(r.salesAcao) + if (r.exempt) " (isento até R$ 20 mil)" else "")
                    MoneyRow("Resultado em ações", (if (r.gainAcao >= 0) "+" else "−") + brl(Math.abs(r.gainAcao)))
                }
                if (r.salesFii > 0) {
                    MoneyRow("Vendas de FIIs", brl(r.salesFii))
                    MoneyRow("Resultado em FIIs", (if (r.gainFii >= 0) "+" else "−") + brl(Math.abs(r.gainFii)))
                }
                if (r.carryAcao > 0 || r.carryFii > 0) MoneyRow("Prejuízo a compensar depois", "ações ${brl(r.carryAcao)} · FIIs ${brl(r.carryFii)}")
                if (r.tax > 0.005) MoneyRow("Imposto (vence ${ddmmyyyy(r.dueISO)})", brl(r.tax), p.coral, bold = true)
                if (r.tax > 0.005 && !r.paid) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Pagar DARF de ${brl(r.tax)}") { store.act(ctx, "DARF pago") { it.payDarf(r.ym) } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Helper("Simplificações: só operações comuns (swing trade), sem day trade, ETFs ou IRRF retido; dividendos entram como isentos. As regras já mudaram antes — confira a legislação atual.")
    }
}

@Composable
fun QuizSheet(store: Store, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val answers = remember { mutableStateListOf<Int>() }
    var idx by remember { mutableStateOf(0) }
    GSheet(if (idx >= QUIZ.size) "Seu perfil" else "Pergunta ${idx + 1} de ${QUIZ.size}", onClose) {
        ProgressBar(idx.toDouble() / QUIZ.size)
        Spacer(Modifier.height(12.dp))
        if (idx >= QUIZ.size) {
            val r = scoreInvestorQuiz(answers.toList())
            val info = PROFILE_INFO[r.label]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { RiskTag(r.label) }
            Spacer(Modifier.height(8.dp))
            if (info != null) Text(info.text, color = p.text, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (r.cap.isNotEmpty()) Helper(r.cap)
            Helper("Pontuação: ${r.score} de 32 · vale por 2 anos (depois, o app pede pra refazer).")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Salvar meu perfil") {
                store.update { it.saveInvestorProfile(answers.toList()) }
                showToast(ctx, "Perfil salvo: ${r.label}")
                onClose()
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("Refazer") { answers.clear(); idx = 0 }
        } else {
            val q = QUIZ[idx]
            Text(q.q, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            q.o.forEachIndexed { i, t ->
                val picked = answers.getOrNull(idx) == i
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(shape)
                        .background(p.cardAlt)
                        .border(1.dp, if (picked) p.brand else p.border, shape)
                        .clickable {
                            while (answers.size <= idx) answers.add(0)
                            answers[idx] = i
                            idx++
                        }
                        .padding(14.dp),
                ) { Text(t, color = p.text, fontSize = 14.sp) }
            }
            if (idx > 0) {
                Spacer(Modifier.height(8.dp))
                GhostButton("‹ Pergunta anterior") { idx-- }
            }
        }
    }
}
