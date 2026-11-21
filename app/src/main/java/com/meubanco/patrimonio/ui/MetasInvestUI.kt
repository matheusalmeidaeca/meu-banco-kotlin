package com.meubanco.patrimonio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.MarketApi
import com.meubanco.patrimonio.data.Store
import kotlinx.coroutines.launch
import com.meubanco.patrimonio.domain.*

private fun whenLabel(m: Int?, todayISO: String): String =
    if (m == null) "mais de 100 anos" else if (m == 0) "já atingida" else "${monthsLabel(m)} (${formatMonthLabel(addMonthsYM(todayISO, m))})"

@Composable
fun MetasInvestBlock() {
    val s = state()
    val today = AppClock.todayISO()
    val cs = remember(Store.rev) { contributionStats(s) }
    val ds = remember(Store.rev) { dividendStats(s) }
    val pBase = remember(Store.rev) { investParams(s) }
    val carteira = portfolioValue(s) + s.leftover
    LaunchedEffect(Unit) { if (s.market.macroUpdatedAt != AppClock.todayISO()) { MarketApi.fetchCdi(Store.state); Store.commit() } }

    Card {
        H2("🎯 Metas de investimento")
        Muted("Só a carteira de investimentos entra na conta (caixinhas e metas de compra ficam de fora).")
        KV("Na carteira hoje", money(carteira), P.gold)
        NumField(s.investGoalPatrimony.takeIf { it > 0 }, { v -> Store.mutate { it.investGoalPatrimony = v ?: 0.0 } }, "Quero ter investido (ex.: 250.000)", suffix = "R$")
        NumField(s.investGoalIncome.takeIf { it > 0 }, { v -> Store.mutate { it.investGoalIncome = v ?: 0.0 } }, "Quero receber em dividendos por mês (ex.: 2.000)", suffix = "R$")
    }

    Card {
        H2("📐 Premissas (dados reais do app)")
        if (cs != null) {
            Txt("Aporte médio real: ${money(cs.avg)} por mês", bold = true)
            Muted("Média desde ${formatMonthLabel(cs.firstYM)}: ${money(cs.total)} em ${cs.months} mês(es) fechado(s). Você aportou em ${cs.monthsWithContribution} deles; meses sem aporte entram como zero.")
        } else Muted("Ainda não há aportes registrados. Use o aporte recorrente ou transfira para a carteira, e o app calcula a média sozinho.")
        NumField(s.investContribOverride, { v -> Store.mutate { it.investContribOverride = v } }, "Usar outro aporte mensal (vazio = média real)", suffix = "R$")

        if (ds.fromHistory) {
            Txt("Rendimento médio em dividendos: ${"%.2f".format(ds.annualYieldPct)}% ao ano", bold = true)
            Muted("Calculado sobre todo o seu histórico (${ds.months} mês(es)), contando os meses sem dividendo. Você recebeu ${money(ds.total)} no total, média de ${money(ds.avgMonthly)}/mês.")
        } else {
            Txt("Rendimento estimado: ${"%.2f".format(ds.annualYieldPct)}% ao ano", bold = true)
            Muted("Ainda não há histórico suficiente de dividendos; usando o rendimento atual dos seus ativos.")
        }
        NumField(s.investYieldOverride, { v -> Store.mutate { it.investYieldOverride = v } }, "Usar outro rendimento anual (vazio = real)", suffix = "%")
        val gi = portfolioGrowth(s)
        if (gi != null) {
            Txt("Valorização dos seus ativos: ${"%.1f".format(gi.pct)}% ao ano", bold = true)
            Muted("Média do preço de ${gi.assets} ativo(s) nos últimos ${gi.months} meses (histórico do arquivo de cotações), sem contar dividendos." + if (gi.clamped) " Limitada a 15% ao ano por prudência." else "")
        } else {
            Txt("Valorização: ${"%.1f".format(s.market.simGrowthPct)}% ao ano (padrão do app)", bold = true)
            Muted("O arquivo de cotações ainda não trouxe o histórico de preços dos seus ativos.")
        }
        NumField(s.investGrowthPct, { v -> Store.mutate { it.investGrowthPct = v } }, "Usar outra valorização anual (vazio = histórico)", suffix = "%")
        if (s.market.ipcaPct != null) {
            Txt("Inflação (IPCA 12 meses): ${"%.2f".format(s.market.ipcaPct)}%", bold = true)
            Muted("Atualizada em ${s.market.macroUpdatedAt?.let { formatSimDate(it) } ?: "—"}" + (s.market.selicPct?.let { " · Selic ${"%.2f".format(it)}% a.a." } ?: ""))
        } else Txt("Inflação: 4,5% ao ano (padrão, ainda sem consulta)", bold = true)
        NumField(s.investInflationOverride, { v -> Store.mutate { it.investInflationOverride = v } }, "Usar outra inflação anual (vazio = IPCA atual)", suffix = "%")
        val scope = rememberCoroutineScope()
        GhostButton("Atualizar IPCA e Selic agora") { scope.launch { val ok = MarketApi.fetchCdi(Store.state); Store.commit(); Ui.show(if (ok) "IPCA, Selic e CDI atualizados" else "Sem resposta agora") } }
        NumField(s.investContribGrowthPct, { v -> Store.mutate { it.investContribGrowthPct = v ?: 0.0 } }, "Aumento do aporte por ano (%) (opcional)", suffix = "%")
        SwitchRow("Reinvestir os dividendos (juros compostos)", s.investReinvest) { on -> Store.mutate { it.investReinvest = on } }
    }

    if (s.investGoalPatrimony > 0) GoalResult("🏁 Meta de patrimônio: ${money(s.investGoalPatrimony)}", today, s) { p, infl -> patrimonyGoal(p, s.investGoalPatrimony, infl) }
    if (s.investGoalIncome > 0) {
        val req = requiredPatrimony(s.investGoalIncome, pBase.yieldPct)
        Card {
            H2("💰 Renda de ${money(s.investGoalIncome)}/mês em dividendos")
            if (req == null) Txt("Sem rendimento em dividendos registrado, não dá para calcular. Informe um rendimento anual acima.", color = P.gold)
            else {
                KV("Patrimônio necessário (hoje)", money(req), P.gold)
                Progress(if (req > 0) carteira / req else 0.0, P.green)
                Muted("Você tem ${"%.0f".format(Math.min(carteira / req, 9.99) * 100)}% disso. Dividendos estimados hoje: ${money(carteira * pBase.yieldPct / 100 / 12)}/mês.")
            }
        }
        GoalResult("⏳ Quando chego em ${money(s.investGoalIncome)}/mês", today, s) { p, infl -> incomeGoal(p, s.investGoalIncome, infl) }
    }
    if (s.investGoalPatrimony <= 0 && s.investGoalIncome <= 0) Muted("Defina uma meta acima para ver em quanto tempo você chega lá.")
    Muted("Projeção com taxas constantes: não é garantia nem recomendação de investimento. A rentabilidade real varia e pode ser negativa. Os cenários mostram 3 pontos acima e abaixo da valorização informada.")
}

@Composable
private fun GoalResult(title: String, today: String, s: AppState, run: (InvestParams, Boolean) -> InvestSim) {
    val base = remember(Store.rev, title) { run(investParams(s, withInflation = false), false) }
    val baseInfl = remember(Store.rev, title) { run(investParams(s), true) }
    val pess = remember(Store.rev, title) { run(investParams(s, growthDelta = -3.0), false) to run(investParams(s, growthDelta = -3.0), true) }
    val otim = remember(Store.rev, title) { run(investParams(s, growthDelta = 3.0), false) to run(investParams(s, growthDelta = 3.0), true) }
    val plus100 = remember(Store.rev, title) { run(investParams(s, extraMonthly = 100.0, withInflation = false), false) }
    Card {
        H2(title)
        Txt("Cenário base: ${whenLabel(base.months, today)}", bold = true, color = P.green)
        Muted("Corrigindo a meta pela inflação (${"%.1f".format(inflationPct(s))}% a.a.): ${whenLabel(baseInfl.months, today)}")
        Spacer8()
        Muted("Pessimista: ${whenLabel(pess.first.months, today)} · com inflação ${whenLabel(pess.second.months, today)}")
        Muted("Otimista: ${whenLabel(otim.first.months, today)} · com inflação ${whenLabel(otim.second.months, today)}")
        if (base.months != null && base.months > 0) {
            Spacer8()
            LineChart(base.yearly, color = P.green)
            Muted("Evolução do patrimônio ano a ano (cenário base, sem inflação).")
            KV("Seu dinheiro (hoje + aportes)", money(investParams(s).start + base.contributed))
            KV("Juros compostos e valorização", money(base.gains), P.gold)
            if (base.gains > base.contributed) Muted("Os juros já rendem mais que o total aportado. É o efeito bola de neve.")
            val m100 = plus100.months
            if (m100 != null && m100 < base.months) Txt("💡 Aportando R$ 100 a mais por mês você chega ${monthsLabel(base.months - m100)} antes.", color = P.gold)
        }
        if (base.months == null) Txt("Com esses valores a meta não é atingida em 100 anos. Aumente o aporte ou revise as premissas.", color = P.red)
    }
}
