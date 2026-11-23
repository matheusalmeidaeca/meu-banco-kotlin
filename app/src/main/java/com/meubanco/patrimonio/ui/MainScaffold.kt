package com.meubanco.patrimonio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.launch

val TAB_ICON = mapOf("conta" to "🏦", "historico" to "🧾", "carteira" to "📈", "gastos" to "🛒", "plano" to "🧭", "ponto" to "⏱", "ajuda" to "💡", "config" to "⚙️", "mais" to "☰", "ir" to "🧾", "reais" to "🔗", "capturas" to "🔔", "smart" to "⚡")
val BOTTOM_TABS = listOf("conta" to "Início", "gastos" to "Gastos", "plano" to "Planejar", "carteira" to "Investir", "mais" to "Mais")
val MAIS_TABS = setOf("mais", "historico", "ajuda", "ponto", "config", "ir", "reais", "capturas", "smart")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    val s = state()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val hidden = s.settings.hiddenTabs
    val tabs = MAIN_TABS.filter { it.first == "config" || it.first == "ajuda" || it.first == "mais" || it.first == "ir" || it.first == "reais" || it.first == "capturas" || it.first == "smart" || (it.first !in hidden && !(it.first == "ponto" && s.income.incomeType != "empregado")) }
    val tab = s.activeMainTab.let { t -> if (tabs.any { it.first == t }) t else "conta" }
    var assistant by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    fun go(t: String) { Store.mutate { it.activeMainTab = t }; scope.launch { drawer.close(); scroll.scrollTo(0) } }
    androidx.activity.compose.BackHandler(enabled = drawer.isOpen || tab != "conta") { if (drawer.isOpen) scope.launch { drawer.close() } else go(if (tab in MAIS_TABS && tab != "mais") "mais" else "conta") }

    // avisos
    val toast = Ui.toast
    LaunchedEffect(toast) { if (toast != null) { snack.showSnackbar(toast); Ui.toast = null } }
    // cotações ao vivo: a cada 10 min com a bolsa aberta, a cada 3 h fora do pregão (só com o app aberto)
    LaunchedEffect(Unit) {
        while (true) {
            val st = Store.state
            val every = if (com.meubanco.patrimonio.data.MarketApi.isB3Open()) 10 * 60_000L else 3 * 3_600_000L
            if (st.market.autoAssets && AppClock.nowMs() - (st.market.assetsCheckedAtMs ?: 0L) >= every) {
                com.meubanco.patrimonio.data.MarketApi.refreshAssets(st)
                Store.commit()
            }
            kotlinx.coroutines.delay(60_000)
        }
    }
    // comemorações
    LaunchedEffect(Store.rev) {
        val c = checkCelebrations(Store.state)
        if (c.isNotEmpty()) { Store.commit(); Ui.enqueue(c) }
    }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = P.card) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().background(P.card2).padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        GoogleAvatar(40.dp)
                        Txt(if (s.settings.displayName.isBlank()) "Admoney" else "Olá, ${s.settings.displayName}", style = T.title, bold = true)
                    }
                    Muted("Conta corrente")
                    Txt(money(s.cash), style = T.h2, bold = true)
                    Muted("Patrimônio ${if (s.settings.hideBalance) "R$ •••••" else formatBRL(totalNetWorth(s))}")
                    val h = remember(Store.rev) { financialHealth(s) }
                    Txt("Saúde financeira: ${h.score}/100 · ${h.label}", color = P.gold, style = T.small)
                }
                Spacer(Modifier.height(6.dp))
                tabs.filter { it.first != "mais" }.forEach { (k, label) ->
                    NavigationDrawerItem(label = { Text(label) }, icon = { Text(TAB_ICON[k] ?: "•") }, selected = k == tab, onClick = { go(k) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = P.card2, unselectedContainerColor = P.card,
                            selectedTextColor = P.text, unselectedTextColor = P.text))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = P.line)
                NavigationDrawerItem(label = { Text("Registrar gasto rápido") }, icon = { Text("➕") }, selected = false,
                    onClick = { Ui.quickSpend = true; scope.launch { drawer.close() } }, modifier = Modifier.padding(horizontal = 8.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = P.card, unselectedTextColor = P.text))
                NavigationDrawerItem(label = { Text(if (s.settings.hideBalance) "Mostrar saldos" else "Esconder saldos") }, icon = { Text("👁") }, selected = false,
                    onClick = { Store.mutate { it.settings.hideBalance = !it.settings.hideBalance } }, modifier = Modifier.padding(horizontal = 8.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = P.card, unselectedTextColor = P.text))
                Spacer(Modifier.height(16.dp))
            }
        }
    }) {
        Scaffold(
            containerColor = P.bg,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = { Column { Txt(MAIN_TABS.first { it.first == tab }.second, bold = true); Muted(formatSimDate(s.simDateISO)) } },
                    navigationIcon = { TextButton({ scope.launch { drawer.open() } }) { Txt("☰", style = T.title) } },
                    actions = { TextButton({ Store.mutate { it.settings.hideBalance = !it.settings.hideBalance } }) { Txt(if (s.settings.hideBalance) "🙈" else "👁") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = P.bg, titleContentColor = P.text),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = P.card) {
                    BOTTOM_TABS.filter { it.first !in hidden }.forEach { (k, label) ->
                        NavigationBarItem(selected = k == tab || (k == "mais" && tab in MAIS_TABS), onClick = { go(k) }, icon = { Text(TAB_ICON[k] ?: "•") }, label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = P.card2, selectedTextColor = P.text, unselectedTextColor = P.muted))
                    }
                }
            },
            floatingActionButton = {
                if (s.settings.assistantEnabled) {
                    val n = remember(Store.rev) { computeInsights(s).size }
                    BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) {
                        FloatingActionButton({ assistant = true }, containerColor = P.brand) { Txt("💡", style = T.title) }
                    }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 8.dp)) {
                when (tab) {
                    "conta" -> ContaScreen(::go)
                    "historico" -> HistoricoScreen()
                    "carteira" -> CarteiraScreen()
                    "gastos" -> GastosScreen()
                    "plano" -> PlanoScreen()
                    "ponto" -> PontoScreen()
                    "ajuda" -> AprenderScreen(::go)
                    "ir" -> IrScreen()
                    "reais" -> ReaisScreen()
                    "capturas" -> CapturasScreen()
                    "smart" -> SmartScreen()
                    "mais" -> MaisScreen(::go, tabs.map { it.first })
                    else -> ConfigScreen()
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }

    if (Ui.quickSpend) {
        var v by remember { mutableStateOf<Double?>(null) }; var cat by remember { mutableStateOf(s.freeSpendCategories.firstOrNull() ?: "Outro") }
        FormDialog("Registrar gasto livre", { Ui.quickSpend = false }, "Registrar", { handle(Store.mutate { spendFree(it, v ?: 0.0, cat) }) }) {
            NumField(v, { v = it }, "Valor", suffix = "R$")
            ChipRow(s.freeSpendCategories.map { it to it }, cat) { cat = it }
        }
    }
    if (assistant) {
        val insights = computeInsights(s)
        Sheet("💡 Assistente", { assistant = false }) {
            if (insights.isEmpty()) Muted("Tudo certo por aqui. Nenhuma dica agora.")
            insights.forEach { i ->
                Column { Txt("${i.ico} ${i.t}", bold = true); Muted(i.d)
                    if (i.goTab != null) TextButton({ assistant = false; go(i.goTab) }) { Text(i.goLabel ?: "Ir") } }
            }
        }
    }
    Ui.celebration?.let { c -> CelebrationDialog(c) { Ui.nextCelebration() } }
}
