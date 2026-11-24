package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

/** Laboratório: ferramentas para caixinhas e investimentos. Tudo é simulação, sem recomendação de investimento. */
@Composable
fun LabScreen() {
    val s = state()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H2("🧪 Laboratório")
        Muted("Simulações para você se organizar. Não é recomendação de investimento.")
        ScoreCard(s); WeekCard(s); RadarCard(s); WhatIfCard(); RunwayCard(s); GoalsCard(s); AutoCard(s); AporteCard(s); StressCard(s); ConcCard(s)
    }
}

@Composable
private fun ScoreCard(s: AppState) {
    val h = healthScore(s)
    Card {
        H2("❤️ Saúde financeira")
        Txt("${h.total}/100", bold = true, style = T.title)
        LiquidPot(h.total / 100.0, if (h.total >= 70) P.green else if (h.total >= 40) P.gold else P.red)
        h.parts.forEach { Muted("${it.label}: ${Math.round(it.pts)}/${Math.round(it.max)}") }
        Txt("👉 " + h.nextStep, bold = true)
    }
}

@Composable
private fun RadarCard(s: AppState) {
    val dups = duplicateCharges(s); val rec = recurringCharges(s)
    Card {
        H2("📡 Radar")
        if (dups.isEmpty() && rec.isEmpty()) { Muted("Nenhuma cobrança em dobro nem assinatura escondida encontrada. Quanto mais extratos você importa, melhor ele enxerga."); return@Card }
        dups.forEach { Txt("⚠️ Possível cobrança em dobro: ${it.label} (${formatBRL(it.amount)}) em ${it.dateA} e ${it.dateB}") }
        rec.take(5).forEach { r ->
            Txt("🔁 ${r.name}: ${formatBRL(r.avg)} todo mês (${r.months} meses seguidos)", bold = true)
            GhostButton("Cadastrar como assinatura") { Store.mutate { st -> addSubscription(st, r.name, r.avg, r.day, "Outro") } }
        }
    }
}

@Composable
private fun WhatIfCard() {
    var v by remember { mutableStateOf<Double?>(null) }
    Card {
        H2("💡 E se eu cortar?")
        NumField(v, { v = it }, "Quanto cortaria por mês", suffix = "R$")
        val m = v ?: 0.0
        if (m > 0) listOf(1, 5, 10).forEach { y -> Muted("Em $y ano(s): ${formatBRL(whatIfCut(m, y, 0.0))} guardados, ou ${formatBRL(whatIfCut(m, y, 8.0))} investindo a 8% ao ano.") }
        else Muted("Veja quanto um pequeno corte mensal vira com o tempo.")
    }
}

@Composable
private fun WeekCard(s: AppState) {
    val w = weekSummary(s)
    Card {
        H2("📅 Resumo da semana")
        Txt("Gastou ${formatBRL(w.spent)} · recebeu ${formatBRL(w.received)}", bold = true)
        Muted("Guardou ${formatBRL(w.saved)}" + (w.topCategory?.let { " · maior gasto: $it" } ?: ""))
        w.goalLine?.let { Muted(it) }; w.cardLine?.let { Muted(it) }
        Muted("Domingo você recebe esse resumo em uma notificação (desligue em Configurações → Avisos).")
    }
}

@Composable
private fun RunwayCard(s: AppState) {
    val r = runway(s)
    Card {
        H2("🕳 Dia Zero")
        if (r.months == null) { Muted("Cadastre seus gastos fixos para ver quanto tempo sua reserva sustenta você."); return@Card }
        Txt("${fmtPlain(Math.round(r.months * 10) / 10.0)} meses de fôlego", bold = true, style = T.title)
        LiquidPot((r.months / 6.0), if (r.months >= 3) P.green else P.gold)
        Muted("Custo essencial ${formatBRL(r.essential)}/mês · reserva ${formatBRL(r.balance)}")
        r.levels.forEach { Txt((if (it.reached) "✅ " else "⬜ ") + it.label) }
        r.zeroDateISO?.let { Muted("Sem nenhuma renda, o dinheiro duraria até $it.") }
    }
}

@Composable
private fun GoalsCard(s: AppState) {
    val plans = goalPlans(s)
    Card {
        H2("⏳ Custo de esperar")
        if (plans.isEmpty()) { Muted("Metas com prazo aparecem aqui, com o aporte necessário e quanto custa adiar."); return@Card }
        plans.forEach { p ->
            Txt("${p.goal.name}: ${formatBRL(p.required)}/mês por ${p.monthsLeft} meses", bold = true)
            p.ifWait3?.let { w -> if (w > p.required + 0.5) Muted("Se esperar 3 meses para começar, sobe para ${formatBRL(w)}/mês (+${formatBRL(w - p.required)}).") }
        }
    }
}

private fun optionsOf(s: AppState): List<Pair<String, String>> =
    listOf("" to "Desligado") + s.reserves.map { "reserve:${it.id}" to "📦 ${it.name}" } + s.goals.filter { !it.completed }.map { "goal:${it.id}" to "🎯 ${it.name}" }

@Composable
private fun AutoCard(s: AppState) {
    val opts = optionsOf(s)
    Card {
        H2("🤖 Piloto automático")
        Muted("Guarda o troco dos gastos e uma parte do que você recebe por Pix, sem esforço.")
        Txt("Arredondar gastos para cima", bold = true)
        ChipRow(listOf(0, 1, 5, 10).map { it.toString() to if (it == 0) "Não" else "R$ $it" }, s.settings.autoRoundStep.toString()) { v -> Store.mutate { it.settings.autoRoundStep = v.toInt() } }
        if (s.settings.autoRoundStep > 0) { Muted("O troco vai para:"); ChipRow(opts.drop(1), s.settings.autoRoundTarget) { v -> Store.mutate { it.settings.autoRoundTarget = v } } }
        Txt("Guardar parte do que recebe", bold = true)
        ChipRow(listOf(0, 5, 10, 20).map { it.toString() to if (it == 0) "Não" else "$it%" }, s.settings.autoSavePct.toInt().toString()) { v -> Store.mutate { it.settings.autoSavePct = v.toDouble() } }
        if (s.settings.autoSavePct > 0) { Muted("Vai para:"); ChipRow(opts.drop(1), s.settings.autoSaveTarget) { v -> Store.mutate { it.settings.autoSaveTarget = v } } }
        if ((s.settings.autoRoundStep > 0 && s.settings.autoRoundTarget.isBlank()) || (s.settings.autoSavePct > 0 && s.settings.autoSaveTarget.isBlank())) Muted("⚠️ Escolha o destino para a regra funcionar.")
    }
}

@Composable
private fun AporteCard(s: AppState) {
    var v by remember { mutableStateOf<Double?>(null) }
    val adv = rebalanceAdvice(s, v ?: 0.0)
    Card {
        H2("🧭 Aporte guiado")
        Muted("Você escolhe o perfil; o app mostra onde o próximo aporte aproxima sua carteira dele. Nunca sugere vender.")
        ChipRow(listOf("conservador" to "Conservador", "moderado" to "Moderado", "arrojado" to "Arrojado"), s.settings.allocPreset) { p -> Store.mutate { it.settings.allocPreset = p } }
        NumField(v, { v = it }, "Quanto vai aportar", suffix = "R$")
        adv.forEach { a ->
            Txt("${a.label}: ${formatBRL(a.amount)}", bold = true)
            Muted("hoje ${fmtPlain(Math.round(a.currentPct * 10) / 10.0)}% → meta ${fmtPlain(a.targetPct)}%" + (a.hint?.let { " · $it" } ?: ""))
        }
    }
}

@Composable
private fun StressCard(s: AppState) {
    val r = stressTest(s)
    Card {
        H2("🌪 Teste de estresse")
        if (r.first().before <= 0) { Muted("Cadastre ativos na carteira para simular quedas do mercado."); return@Card }
        r.forEach {
            Txt(it.label, bold = true)
            Muted("Carteira ${formatBRL(it.before)} → ${formatBRL(it.after)} (${formatBRL(it.delta)}) · renda ${formatBRL(it.incomeBefore)} → ${formatBRL(it.incomeAfter)}/mês" +
                (it.fiCoverageAfterPct?.let { c -> " · cobre ${fmtPlain(Math.round(c * 10) / 10.0)}% da meta de independência" } ?: ""))
        }
    }
}

@Composable
private fun ConcCard(s: AppState) {
    val c = concentration(s)
    Card {
        H2("🎯 Diversificação")
        if (c == null) { Muted("Sem ativos na carteira ainda."); return@Card }
        Txt("Nota ${c.score}/100", bold = true, style = T.title)
        LiquidPot(c.score / 100.0, if (c.score >= 70) P.green else P.gold)
        c.alerts.forEach { Muted(it) }
    }
}
