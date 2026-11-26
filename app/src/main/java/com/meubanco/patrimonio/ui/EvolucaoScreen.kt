package com.meubanco.patrimonio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

private fun pctTxt(v: Double?) = if (v == null) "—" else (if (v >= 0) "+" else "") + String.format(java.util.Locale("pt", "BR"), "%.2f", v) + "%"
@Composable private fun sgn(v: Double) = (if (v >= 0) "+" else "−") + money(Math.abs(v))

@Composable
fun EvolucaoTab(s: AppState) {
    val pp = remember(Store.rev) { portfolioPerf(s) }
    val perfs = remember(Store.rev) { s.assets.filter { it.quantity > 0 }.map { assetPerf(s, it) } }
    var asPct by remember { mutableStateOf(false) }
    val anim by animateFloatAsState(pp.total.toFloat(), tween(1200), label = "rend")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            H2("🌱 Quanto seus ativos renderam")
            Txt(sgn(anim.toDouble()), style = T.h2, bold = true, color = if (pp.total >= 0) P.green else P.red)
            Muted("Só rendimento: valorização das cotas + dividendos + ganhos de vendas. Seus aportes não entram na conta.")
            KV("Valorização das cotas", sgn(pp.unrealized), if (pp.unrealized >= 0) P.green else P.red)
            KV("Dividendos recebidos", sgn(pp.divs), P.green)
            KV("Ganho de vendas", sgn(pp.realized), if (pp.realized >= 0) P.green else P.red)
            KV("Rendimento sobre o investido", pctTxt(pp.pct), if ((pp.pct ?: 0.0) >= 0) P.green else P.red)
            KV("Total aplicado (custo)", money(pp.cost))
        }
        val ser = s.perfHistory
        Card {
            H2("Evolução do rendimento")
            if (ser.size < 2) Muted("O app guarda um retrato por dia. Volte amanhã para ver a linha crescer. Cada dia aberto é um ponto novo.")
            else {
                ChipRow(listOf("0" to "R$ ganhos", "1" to "% sobre o custo"), if (asPct) "1" else "0") { asPct = it == "1" }
                val vals = ser.map { if (asPct) (if (it.cost > 0) it.gain / it.cost * 100 else 0.0) else it.gain }
                LineChart(vals, color = if (vals.last() >= 0) P.green else P.red)
                Muted("De ${ser.first().d} até ${ser.last().d}: ${if (asPct) pctTxt(vals.last()) else sgn(vals.last())}. Aportes novos aumentam o custo, não o ganho.")
            }
        }
        if (perfs.isEmpty()) Card { Muted("Compre cotas na aba Ativos para acompanhar a evolução de cada uma.") }
        else {
            val ranked = perfs.sortedByDescending { it.totalPct ?: -999.0 }
            Card {
                H2("🏆 Ranking dos seus ativos")
                Muted("Rendimento total (valorização + dividendos) sobre o custo de cada um.")
                ranked.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Txt("${i + 1}º ${p.a.ticker}", style = T.small, bold = true)
                        Txt(pctTxt(p.totalPct), style = T.small, bold = true, color = if ((p.totalPct ?: 0.0) >= 0) P.green else P.red)
                    }
                }
            }
            ranked.forEach { p -> AssetEvolutionCard(s, p) }
        }
    }
}

@Composable
private fun AssetEvolutionCard(s: AppState, p: AssetPerf) {
    val a = p.a
    val q = qualityScore(a)
    val ser = remember(Store.rev, a.ticker) { priceSeries(s, a).map { it.p } }
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("${a.ticker} · ${infoOf(a).segment}", bold = true)
            Txt("Qualidade ${qualityLabel(q)} $q", style = T.small, color = P.gold)
        }
        if (ser.size >= 2) LineChart(ser, color = if (ser.last() >= ser.first()) P.green else P.red)
        else Muted("Histórico de preço começa a ser gravado hoje.")
        KV("Cotas · valor", "${p.qty} · ${money(p.value)}")
        KV("Preço médio → atual", "${money(p.pm)} → ${money(a.price)}")
        KV("Valorização", "${sgn(p.gain)} (${pctTxt(p.gainPct)})", if (p.gain >= 0) P.green else P.red)
        KV("Dividendos recebidos", money(p.divs), P.green)
        KV("Total (valorização + dividendos)", "${sgn(p.total)} (${pctTxt(p.totalPct)})", if (p.total >= 0) P.green else P.red)
        p.ret12m?.let { KV("Variação no histórico gravado", pctTxt(it)) }
        p.maxDrawdown?.let { KV("Maior queda no período", pctTxt(it), P.red) }
    }
}

@Composable
fun CaminhosTab(s: AppState) {
    var path by remember { mutableStateOf("seguranca") }
    val info = PATHS.first { it.key == path }
    val total = remember(Store.rev) { portfolioValue(s) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            H2("🧭 Seu próximo passo")
            nextStepAdvice(s).forEach { Txt(it, style = T.small) }
            Muted("Guia educativo do Admoney, baseado em tamanho, setor e previsibilidade. Não é recomendação de investimento: estude antes de comprar.")
        }
        ChipRow(PATHS.map { it.key to "${it.icon} ${it.title.substringBefore(" ")}" }, path) { path = it }
        Card {
            H2("${info.icon} ${info.title}")
            Txt(info.promise, style = T.small)
            Txt(info.risk, style = T.small, color = if (path == "risco") P.red else P.muted)
            val mine = s.assets.filter { it.quantity > 0 && it.price > 0 }
            if (total > 0 && mine.isNotEmpty()) {
                val top = pathRanking(path, s, 20).map { it.ticker }.take(8).toSet()
                val share = mine.filter { it.ticker in top }.sumOf { it.quantity * it.price } / total * 100
                Muted("${Math.round(share)}% da sua carteira está em ativos deste caminho.")
            }
        }
        pathRanking(path, s, 8).forEach { a ->
            val i = infoOf(a); val q = qualityScore(a)
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("${a.ticker} · ${i.segment}", bold = true)
                    Txt("$q · ${qualityLabel(q)}", style = T.small, color = P.gold)
                }
                Txt(i.note, style = T.small)
                Muted("Segurança ${"●".repeat(i.safety)}${"○".repeat(5 - i.safety)}  Renda ${"●".repeat(i.income)}${"○".repeat(5 - i.income)}  Crescimento ${"●".repeat(i.growth)}${"○".repeat(5 - i.growth)}")
                a.valAnnualPct?.let { Muted("Valorização em 12 meses: ${pctTxt(it)}") }
                if (a.quantity > 0) Muted("Você tem ${a.quantity} cota(s).")
            }
        }
    }
}
