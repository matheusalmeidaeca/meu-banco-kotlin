package com.meubanco.patrimonio.domain

/* ============================================================
   Guia de ativos: qualidade e caminhos (segurança, renda, crescimento, risco)
   Classificação EDUCATIVA do Admoney, simplificada (tamanho, setor, previsibilidade).
   Não é recomendação de investimento: estude o ativo e reveja sempre.
   ============================================================ */

class AssetInfo(val segment: String, val safety: Int, val growth: Int, val income: Int, val note: String)

private fun i(seg: String, sa: Int, gr: Int, inc: Int, note: String) = AssetInfo(seg, sa, gr, inc, note)

val ASSET_INFO: Map<String, AssetInfo> = mapOf(
    // Ações
    "BBAS3" to i("Bancos", 3, 2, 4, "Banco enorme e lucrativo, paga bons dividendos. Risco: interferência do governo."),
    "ITUB4" to i("Bancos", 4, 3, 3, "Banco mais sólido do país, crescimento estável e dividendos moderados."),
    "BBDC4" to i("Bancos", 3, 2, 4, "Banco grande em recuperação. Dividendos razoáveis, lucro menos previsível."),
    "BPAC11" to i("Banco de investimento", 3, 4, 2, "Cresce acima dos bancos tradicionais, mas oscila mais com o mercado."),
    "TAEE11" to i("Transmissão de energia", 4, 2, 5, "Receita regulada e previsível. Costuma ser ótima pagadora de dividendos."),
    "ISAE4" to i("Transmissão de energia", 4, 2, 5, "Transmissora de energia com receita reajustada pela inflação."),
    "TRPL4" to i("Transmissão de energia", 4, 2, 5, "Grande transmissora paulista, receita previsível e dividendos constantes."),
    "EGIE3" to i("Geração de energia", 4, 2, 4, "Geradora com contratos longos. Estável e boa pagadora."),
    "ALUP11" to i("Energia", 4, 2, 4, "Transmissão e geração com contratos longos. Previsível."),
    "CPFE3" to i("Energia", 4, 3, 4, "Distribuição e geração. Equilíbrio entre renda e crescimento."),
    "CPLE6" to i("Energia", 3, 3, 3, "Copel privatizada: potencial de ganhos com eficiência, dividendos menores."),
    "CMIG4" to i("Energia", 3, 2, 4, "Energia estatal de MG. Paga bem, mas sofre com decisões políticas."),
    "EQTL3" to i("Energia (distribuição)", 3, 4, 2, "Cresce comprando e melhorando distribuidoras. Dívida alta, dividendos baixos."),
    "SBSP3" to i("Saneamento", 4, 4, 2, "Sabesp privatizada, com muito investimento e potencial de crescimento."),
    "SAPR11" to i("Saneamento", 3, 3, 4, "Saneamento do Paraná. Receita previsível, risco regulatório."),
    "CSMG3" to i("Saneamento", 3, 3, 4, "Saneamento de MG, bons dividendos e risco político."),
    "VIVT3" to i("Telecom", 4, 2, 4, "Líder de telefonia móvel, caixa forte e dividendos regulares."),
    "TIMS3" to i("Telecom", 4, 3, 3, "Telecom com crescimento estável e dividendos crescentes."),
    "BBSE3" to i("Seguros", 4, 2, 5, "Seguradora com margens altas e distribuição generosa de lucros."),
    "PSSA3" to i("Seguros", 4, 3, 3, "Seguradora de qualidade com crescimento constante."),
    "CXSE3" to i("Seguros", 3, 3, 4, "Seguros da Caixa: bons dividendos, ligada a um banco estatal."),
    "WEGE3" to i("Indústria (motores)", 4, 5, 1, "Empresa de qualidade excepcional e crescimento forte. Paga pouco e custa caro."),
    "RADL3" to i("Varejo (farmácias)", 4, 4, 1, "Líder em farmácias, cresce de forma consistente. Dividendo baixo."),
    "B3SA3" to i("Bolsa de valores", 4, 3, 3, "Monopólio da bolsa. Lucra mais quando o mercado está animado."),
    "ITSA4" to i("Holding (Itaú)", 4, 3, 4, "Dona de parte do Itaú, com desconto e bons dividendos."),
    "PETR4" to i("Petróleo", 2, 3, 5, "Dividendos enormes, mas depende do petróleo e do governo. Oscila muito."),
    "VALE3" to i("Mineração", 3, 3, 4, "Gigante global ligada ao minério. Dividendos variam com o preço."),
    "VBBR3" to i("Combustíveis", 3, 3, 3, "Distribuidora de combustíveis. Margens apertadas, mas bem gerida."),
    "ABEV3" to i("Bebidas", 4, 1, 3, "Empresa sólida e estável, com crescimento fraco nos últimos anos."),
    "KLBN11" to i("Papel e celulose", 3, 3, 3, "Dólar a favor, dividendos regulares, mas setor cíclico."),
    "SUZB3" to i("Celulose", 3, 3, 2, "Líder mundial, ligada ao dólar e ao preço da celulose."),
    "RENT3" to i("Aluguel de carros", 3, 4, 1, "Cresce muito, porém sofre com juros altos e oscila bastante."),
    // FIIs de tijolo
    "HGLG11" to i("Logística", 4, 3, 4, "Galpões logísticos com bons inquilinos. Um dos FIIs mais tradicionais."),
    "XPLG11" to i("Logística", 4, 3, 4, "Galpões bem localizados e contratos longos."),
    "BTLG11" to i("Logística", 4, 3, 4, "Logística diversificada, boa gestão e vacância baixa."),
    "LVBI11" to i("Logística", 4, 3, 4, "Galpões logísticos, diversificação de inquilinos."),
    "BRCO11" to i("Logística", 4, 3, 4, "Galpões com contratos longos e inquilinos fortes."),
    "GGRC11" to i("Logística", 3, 2, 4, "Contratos longos, mas poucos inquilinos concentram a receita."),
    "KNRI11" to i("Lajes e logística", 4, 2, 3, "Misto de lajes corporativas e galpões. Estável, renda moderada."),
    "HGRU11" to i("Renda urbana", 4, 3, 4, "Varejo e serviços (farmácias, mercados) com contratos longos."),
    "TRXF11" to i("Renda urbana", 4, 2, 5, "Contratos atípicos reajustados pela inflação, renda alta."),
    "ALZR11" to i("Renda urbana", 4, 2, 4, "Imóveis com contratos longos e inquilinos conhecidos."),
    "XPML11" to i("Shoppings", 3, 3, 4, "Shoppings diversificados. Sobe quando o consumo vai bem."),
    "VISC11" to i("Shoppings", 3, 3, 4, "Shoppings espalhados pelo país, gestão experiente."),
    "HSML11" to i("Shoppings", 3, 3, 4, "Shoppings de boa localização."),
    "MALL11" to i("Shoppings", 3, 3, 4, "Shoppings e varejo, renda recorrente."),
    "HGBS11" to i("Shoppings", 3, 3, 4, "Shoppings tradicionais, com cotas mais caras."),
    "PVBI11" to i("Lajes corporativas", 3, 3, 3, "Escritórios de alto padrão em SP. Sensível à vacância."),
    "RBRP11" to i("Lajes corporativas", 2, 2, 3, "Escritórios e galpões com maior vacância. Mais arriscado."),
    "JSRE11" to i("Multiestratégia", 3, 2, 3, "Imóveis variados, gestão ativa, renda moderada."),
    "HFOF11" to i("Fundo de fundos", 3, 3, 4, "Diversifica em vários FIIs de uma vez, com gestão ativa."),
    // FIIs de papel
    "KNCR11" to i("Papel (CDI)", 4, 1, 5, "Recebíveis atrelados ao CDI e crédito de boa qualidade. Muito previsível."),
    "RBRR11" to i("Papel (alta qualidade)", 4, 1, 5, "Crédito de alta qualidade, renda estável."),
    "KNIP11" to i("Papel (inflação)", 4, 2, 4, "Recebíveis atrelados ao IPCA: protege da inflação."),
    "HGCR11" to i("Papel", 3, 1, 4, "Recebíveis imobiliários tradicionais, algum risco de crédito."),
    "MXRF11" to i("Papel (misto)", 3, 1, 5, "Muito popular e líquido, mistura crédito seguro e arriscado."),
    "IRDM11" to i("Papel", 3, 1, 5, "Renda alta, crédito com risco moderado."),
    "VGIR11" to i("Papel", 3, 1, 5, "Renda alta, qualidade de crédito média."),
    "CPTS11" to i("Papel", 3, 1, 5, "Recebíveis diversificados; já teve oscilação de cota."),
    "RECR11" to i("Papel", 3, 1, 5, "Recebíveis imobiliários com renda alta e risco moderado."),
    "MCCI11" to i("Papel", 3, 1, 5, "Crédito imobiliário diversificado, renda constante."),
    "SNCI11" to i("Papel", 3, 1, 5, "Recebíveis imobiliários, renda alta."),
    "XPCI11" to i("Papel", 3, 1, 5, "Crédito imobiliário diversificado, boa gestão."),
    "VRTA11" to i("Papel", 3, 1, 5, "Crédito imobiliário com renda alta e risco moderado."),
)

fun infoOf(a: Asset): AssetInfo = ASSET_INFO[a.ticker] ?: when (a.category) {
    "tijolo" -> AssetInfo("FII de tijolo", 3, 2, 4, "Fundo imobiliário de imóveis físicos.")
    "papel" -> AssetInfo("FII de papel", 3, 1, 5, "Fundo imobiliário de recebíveis (crédito).")
    else -> AssetInfo("Ação", 3, 3, 2, "Ação listada na bolsa.")
}

/** Nota 0 a 100: segurança pesa mais, depois renda e crescimento; o preço recente e a oscilação ajustam um pouco. */
fun qualityScore(a: Asset): Int {
    val inf = infoOf(a)
    var q = (inf.safety * 0.45 + inf.income * 0.30 + inf.growth * 0.25) / 5.0 * 100
    a.valAnnualPct?.let { q += (it / 4.0).coerceIn(-6.0, 6.0) }
    if (a.volatility > 0.03) q -= 5
    return Math.round(q).toInt().coerceIn(0, 100)
}

fun qualityLabel(q: Int) = when { q >= 78 -> "Alta"; q >= 62 -> "Boa"; q >= 48 -> "Média"; else -> "Baixa" }

class InvestPath(val key: String, val icon: String, val title: String, val promise: String, val risk: String)

val PATHS = listOf(
    InvestPath("seguranca", "🛡", "Segurança e estabilidade", "Empresas e fundos grandes, com receita previsível. Tende a oscilar menos e pagar com regularidade; o crescimento costuma ser mais calmo.", "Risco menor: quedas existem, mas costumam ser menores e se recuperam mais rápido."),
    InvestPath("renda", "💰", "Renda mensal", "Ativos que pagam bastante dividendo. É o caminho para o dinheiro cair todo mês na conta.", "Risco moderado: o valor da renda pode variar e a cota pode cair quando os juros sobem."),
    InvestPath("crescimento", "🚀", "Crescimento", "Empresas que tendem a crescer mais e valorizar a cota ao longo dos anos. Pagam pouco dividendo.", "Risco maior: oscila bastante e exige paciência de anos. Pode ficar muito tempo no vermelho."),
    InvestPath("risco", "⚠️", "Alto risco, alto potencial", "Setores cíclicos ou dependentes de decisões políticas e de preços de commodities. Podem render muito ou frustrar.", "Risco alto: potencial de desvalorização forte. Use só uma parte pequena da carteira."),
)

fun pathScore(path: String, a: Asset): Double {
    val i = infoOf(a)
    return when (path) {
        "seguranca" -> i.safety * 3.0 + i.income * 1.5 + i.growth * 0.5 - (if (a.volatility > 0.025) 1.0 else 0.0)
        "renda" -> i.income * 3.0 + i.safety * 1.5 + i.growth * 0.3
        "crescimento" -> i.growth * 3.0 + i.safety * 1.2 + i.income * 0.3
        else -> i.growth * 1.5 + (5 - i.safety) * 2.5 + i.income * 0.5 + (if (a.volatility > 0.025) 1.0 else 0.0)
    }
}

fun pathRanking(path: String, s: AppState, n: Int = 6): List<Asset> =
    s.assets.filter { it.price > 0 }.sortedWith(compareByDescending<Asset> { pathScore(path, it) }.thenByDescending { qualityScore(it) }).take(n)

/** Texto de "pegar na mão": o que falta na carteira do usuário e por qual caminho ir. */
fun nextStepAdvice(s: AppState): List<String> {
    val out = mutableListOf<String>()
    val held = s.assets.filter { it.quantity > 0 && it.price > 0 }
    val perfil = s.settings.allocPreset
    val target = (ALLOC_PRESETS[perfil] ?: ALLOC_PRESETS["moderado"]!!).filterKeys { it != "rf" }
    val tsum = target.values.sum()
    if (held.isEmpty()) {
        val seg = pathRanking("seguranca", s, 2).joinToString(" e ") { it.ticker }
        out += "Você ainda não tem cotas. Pelo caminho da segurança, uma boa forma de começar é com poucos ativos grandes e previsíveis, como $seg. Compre pouco e acompanhe a evolução aqui."
        return out
    }
    val total = held.sumOf { it.quantity * it.price }
    val cur = target.keys.associateWith { k -> held.filter { it.category == k }.sumOf { it.quantity * it.price } / total * 100 }
    val deficit = target.mapValues { (k, p) -> p / tsum * 100 - (cur[k] ?: 0.0) }
    val worst = deficit.maxByOrNull { it.value }
    if (worst != null && worst.value > 8) {
        val cls = CLASS_LABEL[worst.key] ?: worst.key
        val pool = s.assets.filter { it.category == worst.key && it.price > 0 }
        fun top(path: String) = pool.sortedByDescending { pathScore(path, it) }.firstOrNull { x -> held.none { it.ticker == x.ticker } } ?: pool.maxByOrNull { pathScore(path, it) }
        val seg = top("seguranca"); val cres = top("crescimento")
        out += "Para o seu perfil ${perfil}, faltam ${cls}: hoje ${Math.round(cur[worst.key] ?: 0.0)}% da carteira e a referência é ${Math.round(target[worst.key]!! / tsum * 100)}%."
        if (seg != null) out += "🛡 Caminho seguro: ${seg.ticker}. ${infoOf(seg).note}"
        if (cres != null && cres.ticker != seg?.ticker) out += "🚀 Caminho de crescimento: ${cres.ticker}. ${infoOf(cres).note} Oscila mais."
    } else out += "A divisão entre ações, FIIs de tijolo e de papel está perto da referência do perfil $perfil. Continue aportando de forma regular."
    concentration(s)?.let { c -> if (c.topAssetPct > 30) out += "⚠️ ${c.topAsset} pesa ${Math.round(c.topAssetPct)}% da carteira. Novos aportes em outros ativos reduzem o risco." }
    val weak = held.filter { qualityScore(it) < 48 }.map { it.ticker }
    if (weak.isNotEmpty()) out += "Atenção à qualidade de ${weak.joinToString(", ")}: nota baixa no guia. Vale estudar antes de aportar mais."
    return out
}
