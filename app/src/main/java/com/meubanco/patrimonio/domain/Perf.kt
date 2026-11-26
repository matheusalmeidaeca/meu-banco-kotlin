package com.meubanco.patrimonio.domain

import kotlinx.serialization.Serializable

/* ============================================================
   Desempenho da carteira: quanto os ativos renderam, sem contar os seus aportes
   ============================================================ */

@Serializable
class PxPoint(var d: String = "", var p: Double = 0.0)

/** Retrato diário da carteira. Rendimento = valor − custo + dividendos + ganhos já realizados. */
@Serializable
class PerfPoint(var d: String = "", var value: Double = 0.0, var cost: Double = 0.0, var divs: Double = 0.0, var realized: Double = 0.0) {
    val gain get() = round2(value - cost + divs + realized)
}

class PortPerf(val value: Double, val cost: Double, val unrealized: Double, val divs: Double, val realized: Double) {
    val total get() = round2(unrealized + divs + realized)
    /** Rendimento sobre o que você aplicou. Aportes novos não inflam esse número. */
    val pct: Double? get() = if (cost > 0.005) round2(total / cost * 100) else null
}

fun portfolioPerf(s: AppState): PortPerf {
    val held = s.assets.filter { it.quantity > 0 }
    val cost = held.sumOf { assetCostBasis(it) }
    val value = held.sumOf { it.quantity * it.price }
    return PortPerf(round2(value), round2(cost), round2(value - cost), round2(s.totalDividendsReceived), round2(s.realizedGain))
}

/** Grava o retrato de hoje (um por dia) e estende as curvas de preço com a cotação atual. Devolve true se mudou algo. */
fun recordSnapshot(s: AppState): Boolean {
    val today = AppClock.todayISO()
    var changed = false
    s.assets.forEach { a ->
        if (a.price <= 0.0) return@forEach
        val l = s.priceHistory.getOrPut(a.ticker) { mutableListOf() }
        val last = l.lastOrNull()
        if (last == null || last.d != today || last.p != a.price) {
            if (last != null && last.d == today) last.p = a.price else l.add(PxPoint(today, a.price))
            while (l.size > 420) l.removeAt(0)
            changed = true
        }
    }
    val pp = portfolioPerf(s)
    if (pp.cost > 0.0 || s.totalDividendsReceived != 0.0 || s.realizedGain != 0.0) {
        val last = s.perfHistory.lastOrNull()
        if (last == null || last.d != today || last.value != pp.value || last.cost != pp.cost || last.divs != pp.divs || last.realized != pp.realized) {
            if (last != null && last.d == today) { last.value = pp.value; last.cost = pp.cost; last.divs = pp.divs; last.realized = pp.realized }
            else s.perfHistory.add(PerfPoint(today, pp.value, pp.cost, pp.divs, pp.realized))
            while (s.perfHistory.size > 800) s.perfHistory.removeAt(0)
            changed = true
        }
    }
    return changed
}

/** Acrescenta ao catálogo do usuário os ativos novos do app (sem mexer nos que ele já tem). */
fun ensureCatalog(s: AppState): Boolean {
    val have = s.assets.map { it.ticker }.toHashSet()
    val add = DEFAULT_ASSETS.filter { it.ticker !in have }
    if (add.isEmpty()) return false
    add.forEach { s.assets.add(it.copyAsset()) }
    s.market.assetsCheckedAtMs = 0L     // força buscar as cotações reais dos novos
    return true
}

class AssetPerf(
    val a: Asset, val qty: Int, val pm: Double, val value: Double, val gain: Double, val gainPct: Double?,
    val divs: Double, val total: Double, val totalPct: Double?, val ret12m: Double?, val annualVol: Double?, val maxDrawdown: Double?,
)

fun priceSeries(s: AppState, a: Asset): List<PxPoint> {
    val l = s.priceHistory[a.ticker].orEmpty()
    return if (a.price > 0.0 && (l.isEmpty() || l.last().d != AppClock.todayISO())) l + PxPoint(AppClock.todayISO(), a.price) else l
}

fun assetDividends(s: AppState, ticker: String) = round2(s.dividendBreakdown.values.sumOf { it[ticker] ?: 0.0 })

fun assetPerf(s: AppState, a: Asset): AssetPerf {
    val cost = if (a.quantity > 0) assetCostBasis(a) else 0.0
    val value = a.quantity * a.price
    val gain = if (a.quantity > 0) round2(value - cost) else 0.0
    val divs = assetDividends(s, a.ticker)
    val ser = priceSeries(s, a).map { it.p }
    val ret12 = if (ser.size >= 4 && ser.first() > 0) round2((ser.last() / ser.first() - 1) * 100) else null
    val rets = ser.zipWithNext { x, y -> if (x > 0) y / x - 1 else 0.0 }
    val vol = if (rets.size >= 8) { val m = rets.average(); round2(Math.sqrt(rets.sumOf { (it - m) * (it - m) } / rets.size) * Math.sqrt(52.0) * 100) } else null
    var peak = 0.0; var dd = 0.0
    ser.forEach { if (it > peak) peak = it; if (peak > 0) dd = Math.min(dd, it / peak - 1) }
    return AssetPerf(a, a.quantity, if (a.quantity > 0) round2(cost / a.quantity) else 0.0, round2(value), gain,
        if (a.quantity > 0 && cost > 0) round2(gain / cost * 100) else null, divs, round2(gain + divs),
        if (a.quantity > 0 && cost > 0) round2((gain + divs) / cost * 100) else null, ret12, vol, if (ser.size >= 8) round2(dd * 100) else null)
}
