package com.meubanco.patrimonio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.fragment.app.FragmentActivity
import com.meubanco.patrimonio.data.Backup
import com.meubanco.patrimonio.data.MarketApi
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*
import kotlinx.coroutines.launch

val LocalCfgQuery = compositionLocalOf { "" }

@Composable
private fun Section(title: String, open: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val q = LocalCfgQuery.current.trim()
    if (q.isNotEmpty() && !title.contains(q, ignoreCase = true)) return
    var o by remember { mutableStateOf(open) }
    if (q.isNotEmpty()) o = true
    Card(onClick = { o = !o }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { H2(title); Txt(if (o) "▲" else "▼", color = P.muted) }
        if (o) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
fun ConfigScreen() {
    val s = state()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var cdiLoading by remember { mutableStateOf(false) }
    var stmtItems by remember { mutableStateOf<List<StmtItem>?>(null) }
    var cloudTick by remember { mutableIntStateOf(0) }
    val cloudPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { Backup.setCloudFolder(ctx, uri); Ui.show(if (Backup.writeCloud(ctx)) "Backup salvo na pasta" else "Não consegui gravar nessa pasta"); cloudTick++ }
    }
    val stmtPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = try { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() } catch (e: Exception) { "" }
            val items = parseStatement(txt)
            if (items.isEmpty()) Ui.show("Não encontrei lançamentos nesse arquivo.") else stmtItems = items
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) Ui.show(if (Backup.export(ctx, uri)) "Backup salvo" else "Falha ao salvar o backup")
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Ui.show(if (Backup.import(ctx, uri)) "Backup restaurado" else "Arquivo inválido")
    }

    var query by remember { mutableStateOf("") }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) Ui.show(try { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(historyToCsv(Store.state.history).toByteArray()) }; "Extrato CSV salvo" } catch (e: Exception) { "Falha ao salvar" })
    }
    var restore by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalCfgQuery provides query) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(query, { query = it }, "🔎 Buscar configuração (ex.: PIN, cartão, backup)")
        Section("🧭 Objetivo e abas") {
            Muted("Seu objetivo principal define as dicas em destaque.")
            ChipRow(GOALS, s.mainGoal) { Store.mutate { st -> st.mainGoal = it } }
            H2("Abas visíveis")
            Muted("Esconda o que você não usa para deixar o app mais simples.")
            listOf("carteira" to "Carteira de investimentos", "gastos" to "Gastos", "plano" to "Planejamento", "historico" to "Extrato & histórico", "ponto" to "Ponto (módulo opcional)").forEach { (k, label) ->
                SwitchRow(label, k !in s.settings.hiddenTabs) { on -> Store.mutate { st -> if (on) st.settings.hiddenTabs.remove(k) else if (k !in st.settings.hiddenTabs) st.settings.hiddenTabs.add(k) } }
            }
            GhostButton("Modo simples (só conta, gastos e planejamento)") { Store.mutate { st -> st.settings.hiddenTabs = mutableListOf("carteira", "ponto", "historico") } }
            GhostButton("Mostrar tudo") { Store.mutate { st -> st.settings.hiddenTabs = mutableListOf() } }
        }
        Section("👤 Perfil") {
            var name by remember { mutableStateOf(s.settings.displayName) }
            Field(name, { name = it; Store.mutate { st -> st.settings.displayName = it } }, "Nome")
            var bd by remember { mutableStateOf(s.profile.birthDateISO.orEmpty()) }
            Field(bd, { bd = it; if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(it)) Store.mutate { st -> st.profile.birthDateISO = it } }, "Nascimento (AAAA-MM-DD)", keyboard = KeyboardType.Number)
            ChipRow(listOf("" to "—", "F" to "Feminino", "M" to "Masculino"), s.profile.sex.orEmpty()) { Store.mutate { st -> st.profile.sex = it.ifEmpty { null } } }
        }
        Section("💼 Renda") {
            ChipRow(listOf("empregado" to "CLT", "autonomo" to "Autônomo", "renda" to "Benefício"), s.income.incomeType) { Store.mutate { st -> st.income.incomeType = it } }
            when (s.income.incomeType) {
                "empregado" -> {
                    NumField(s.income.baseSalary, { Store.mutate { st -> st.income.baseSalary = it ?: 0.0; recomputeFgtsBackfill(st) } }, "Salário bruto", suffix = "R$")
                    NumField(s.income.extraHourPct, { Store.mutate { st -> st.income.extraHourPct = it ?: 50.0 } }, "Adicional de hora extra", suffix = "%")
                    NumField(s.income.sundayBonus, { Store.mutate { st -> st.income.sundayBonus = it ?: 0.0 } }, "Valor por domingo trabalhado", suffix = "R$")
                    var hd by remember { mutableStateOf(s.income.hireDateISO.orEmpty()) }
                    Field(hd, { hd = it; if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(it)) Store.mutate { st -> st.income.hireDateISO = it; recomputeFgtsBackfill(st) } }, "Admissão (AAAA-MM-DD)", keyboard = KeyboardType.Number)
                    SwitchRow("13º salário", s.income.thirteenthEnabled) { Store.mutate { st -> st.income.thirteenthEnabled = it } }
                    SwitchRow("Férias", s.income.feriasEnabled) { Store.mutate { st -> st.income.feriasEnabled = it } }
                    val d = isoToDate(s.simDateISO)
                    val mi = computeMonthlyIncome(s, d.year, d.monthIdx())
                    Muted("Líquido do mês: ${formatBRL(mi.net)} (INSS ${formatBRL(mi.inssValue)} · IR ${formatBRL(mi.irrfValue)})")
                    val t = compute13thBreakdown(s); Muted("13º: 1ª parcela ${formatBRL(t.firstInstallment)} · 2ª líquida ${formatBRL(t.secondInstallmentNet)}")
                    val f = computeFeriasBreakdown(s); Muted("Férias líquidas: ${formatBRL(f.net)} (com 1/3)")
                }
                "renda" -> {
                    Field(s.income.beneficioLabel, { Store.mutate { st -> st.income.beneficioLabel = it } }, "Nome do benefício")
                    NumField(s.income.beneficioAmount, { Store.mutate { st -> st.income.beneficioAmount = it ?: 0.0 } }, "Valor mensal", suffix = "R$")
                }
                else -> Muted("Autônomo: registre entradas pelo Pix recebido.")
            }
            H2("Outra renda")
            SwitchRow("Tenho outra renda", s.secondaryIncome.enabled) { Store.mutate { st -> st.secondaryIncome.enabled = it } }
            if (s.secondaryIncome.enabled) {
                Field(s.secondaryIncome.label, { Store.mutate { st -> st.secondaryIncome.label = it } }, "Nome")
                NumField(s.secondaryIncome.amount, { Store.mutate { st -> st.secondaryIncome.amount = it ?: 0.0 } }, "Valor mensal", suffix = "R$")
                NumField(s.secondaryIncome.payDay.toDouble(), { Store.mutate { st -> st.secondaryIncome.payDay = clampI((it ?: 5.0).toInt(), 1, 28) } }, "Dia do pagamento", decimals = false)
            }
        }
        Section("💳 Cartão") {
            SwitchRow("Usar cartão de crédito", s.card.enabled) { Store.mutate { st -> st.card.enabled = it } }
            if (s.card.enabled) {
                NumField(s.card.limit, { Store.mutate { st -> st.card.limit = it ?: 0.0 } }, "Limite", suffix = "R$")
                NumField(s.card.closingDay.toDouble(), { Store.mutate { st -> st.card.closingDay = clampI((it ?: 25.0).toInt(), 1, 28) } }, "Dia de fechamento", decimals = false)
                NumField(s.card.dueDay.toDouble(), { Store.mutate { st -> st.card.dueDay = clampI((it ?: 5.0).toInt(), 1, 28) } }, "Dia de vencimento", decimals = false)
            }
        }
        if (s.extraCards.isNotEmpty()) Section("💳 Outros cartões") {
            s.extraCards.forEach { c ->
                H2(c.name.ifBlank { "Cartão" })
                Field(c.name, { Store.mutate { _ -> c.name = it } }, "Nome")
                NumField(c.limit, { Store.mutate { _ -> c.limit = it ?: 0.0 } }, "Limite", suffix = "R$")
                NumField(c.closingDay.toDouble(), { Store.mutate { _ -> c.closingDay = clampI((it ?: 25.0).toInt(), 1, 28) } }, "Dia de fechamento", decimals = false)
                NumField(c.dueDay.toDouble(), { Store.mutate { _ -> c.dueDay = clampI((it ?: 5.0).toInt(), 1, 28) } }, "Dia de vencimento", decimals = false)
            }
        }
        Section("🔐 Segurança") {
            if (s.settings.pinEnabled) {
                H2("Bloquear automaticamente")
                ChipRow(listOf("0" to "Na hora", "60" to "1 min", "300" to "5 min", "900" to "15 min"), s.settings.lockAfterSec.toString()) { Store.mutate { st -> st.settings.lockAfterSec = it.toInt() } }
                GhostButton("Trocar PIN") { pinDialog = true }
            }
            SwitchRow("Bloquear capturas de tela e prévia nos apps recentes", s.settings.secureScreen) { Store.mutate { st -> st.settings.secureScreen = it } }
            SwitchRow("Esconder saldos", s.settings.hideBalance) { Store.mutate { st -> st.settings.hideBalance = it } }
            SwitchRow("Pedir PIN ao abrir", s.settings.pinEnabled && s.settings.pinHash != null) { on ->
                if (on) pinDialog = true else Store.mutate { st -> st.settings.pinEnabled = false; st.settings.pinHash = null; st.settings.bioEnabled = false }
            }
            if (s.settings.pinEnabled) {
                val act = ctx as FragmentActivity
                SwitchRow("Entrar com digital / rosto", s.settings.bioEnabled) { on ->
                    if (on && !biometricAvailable(act)) Ui.show("Nenhuma digital/rosto cadastrada no aparelho.")
                    else if (on) promptBiometric(act, { Store.mutate { st -> st.settings.bioEnabled = true } }) else Store.mutate { st -> st.settings.bioEnabled = false }
                }
            }
            NumField(s.settings.pixConfirmThreshold, { Store.mutate { st -> st.settings.pixConfirmThreshold = it ?: 0.0 } }, "Confirmar Pix a partir de (0 = nunca)", suffix = "R$")
            NumField(s.settings.pixDailyLimit, { Store.mutate { st -> st.settings.pixDailyLimit = it ?: 0.0 } }, "Limite diário de Pix (0 = sem limite)", suffix = "R$")
        }
        Section("📈 Corretora e mercado") {
            NumField(s.settings.brokerageFee, { Store.mutate { st -> st.settings.brokerageFee = it ?: 0.0 } }, "Corretagem por ordem", suffix = "R$")
            Field(s.market.quotesUrl, { Store.mutate { st -> st.market.quotesUrl = it } }, "Endereço do arquivo de cotações")
            Muted("CDI atual: ${fmtPlain(s.reserveConfig.annualRatePct)}% a.a." + (s.market.cdiUpdatedAt?.let { " · atualizado em ${formatSimDate(it)}" } ?: ""))
            s.market.cdiError?.let { Txt(it, color = P.red, style = T.small) }
            GhostButton(if (cdiLoading) "Atualizando…" else "Atualizar CDI agora", enabled = !cdiLoading) {
                cdiLoading = true
                scope.launch { val ok = MarketApi.fetchCdi(Store.state); Store.commit(); cdiLoading = false; Ui.show(if (ok) "CDI atualizado" else "Sem resposta agora") }
            }
        }
        Section("🔔 Avisos") {
            SwitchRow("Fatura do cartão", s.settings.notifyCard) { Store.mutate { st -> st.settings.notifyCard = it } }
            SwitchRow("Orçamento por categoria", s.settings.notifyBudget) { Store.mutate { st -> st.settings.notifyBudget = it } }
            SwitchRow("Parcelas de dívidas", s.settings.notifyDebt) { Store.mutate { st -> st.settings.notifyDebt = it } }
            NumField(s.settings.notifyCardDays.toDouble(), { Store.mutate { st -> st.settings.notifyCardDays = (it ?: 3.0).toInt() } }, "Avisar fatura X dias antes", decimals = false)
            NumField(s.settings.notifyBackupDays.toDouble(), { Store.mutate { st -> st.settings.notifyBackupDays = (it ?: 14.0).toInt() } }, "Lembrar de backup a cada X dias", decimals = false)
            NumField(s.settings.notifyGoalPct.toDouble(), { Store.mutate { st -> st.settings.notifyGoalPct = (it ?: 90.0).toInt() } }, "Avisar meta ao atingir", suffix = "%", decimals = false)
            NumField(s.settings.notifyFreeSpendPct.toDouble(), { Store.mutate { st -> st.settings.notifyFreeSpendPct = (it ?: 90.0).toInt() } }, "Avisar gasto livre ao atingir", suffix = "%", decimals = false)
        }
        Section("🎨 Aparência") {
            ChipRow(listOf("dark" to "Escuro", "light" to "Claro", "auto" to "Automático"), s.settings.theme) { Store.mutate { st -> st.settings.theme = it } }
            ChipRow(listOf("normal" to "Normal", "grande" to "Grande", "xgrande" to "Maior"), s.settings.fontSize) { Store.mutate { st -> st.settings.fontSize = it } }
            SwitchRow("Alto contraste", s.settings.highContrast) { Store.mutate { st -> st.settings.highContrast = it } }
            SwitchRow("Vibração", s.settings.hapticEnabled) { Store.mutate { st -> st.settings.hapticEnabled = it } }
            SwitchRow("Assistente (dicas)", s.settings.assistantEnabled) { Store.mutate { st -> st.settings.assistantEnabled = it } }
            SwitchRow("Comemorações", s.settings.celebrationsEnabled) { Store.mutate { st -> st.settings.celebrationsEnabled = it } }
        }
        Section("💾 Dados") {
            Muted(s.lastBackupAt?.let { "Último backup: ${it.take(10)}" } ?: "Nenhum backup exportado ainda.")
            SwitchRow("Cópia automática diária (no aparelho)", s.settings.autoBackupEnabled) { Store.mutate { st -> st.settings.autoBackupEnabled = it } }
            PrimaryButton("Exportar backup") { export.launch(Backup.suggestedName()) }
            GhostButton("Importar backup (.json)", Modifier.fillMaxWidth()) { import.launch(arrayOf("application/json", "text/plain", "*/*")) }
            Muted("Aceita também backups exportados pelo app em HTML.")
            GhostButton("Exportar extrato em CSV (planilha)", Modifier.fillMaxWidth()) { csv.launch("extrato-" + AppClock.todayISO() + ".csv") }
            GhostButton("Restaurar de uma cópia automática", Modifier.fillMaxWidth()) { restore = true }
            H2("☁️ Backup em nuvem")
            key(cloudTick) {
                val folder = Backup.cloudFolderName(ctx)
                Muted(if (folder == null) "Escolha uma pasta do Google Drive, Dropbox, OneDrive… (elas aparecem na lista de pastas do Android). O backup é regravado todo dia sozinho."
                      else "Pasta: $folder · último envio: ${Backup.cloudLast(ctx)?.take(16)?.replace('T', ' ') ?: "—"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(if (folder == null) "Escolher pasta" else "Trocar pasta", Modifier.weight(1f)) { cloudPick.launch(null) }
                    if (folder != null) GhostButton("Salvar agora", Modifier.weight(1f)) { Ui.show(if (Backup.writeCloud(ctx)) "Backup enviado" else "Falhou — escolha a pasta de novo"); cloudTick++ }
                }
            }
            H2("🏦 Importar extrato do banco")
            Muted("Arquivo CSV ou OFX baixado do app/site do seu banco. Entradas e saídas ajustam o saldo e entram no histórico; lançamentos repetidos são ignorados.")
            GhostButton("Escolher arquivo (.csv / .ofx)", Modifier.fillMaxWidth()) { stmtPick.launch(arrayOf("*/*")) }
            H2("Simulação de tempo")
            Muted("Hoje na simulação: ${formatSimDate(s.simDateISO)}. O app acompanha a data real sozinho; use isto só para testar.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("+1 dia", Modifier.weight(1f)) { Store.mutate { simulateOneDay(it) } }
                GhostButton("+7 dias", Modifier.weight(1f)) { Store.mutate { st -> repeat(7) { simulateOneDay(st) } } }
                GhostButton("+30 dias", Modifier.weight(1f)) { Store.mutate { st -> repeat(30) { simulateOneDay(st) } } }
            }
            H2("Zona de perigo")
            GhostButton("Zerar histórico e movimentos", danger = true) { confirm = "Apagar histórico, extrato e estatísticas? Saldos não mudam." to { Store.mutate { resetPartial(it, "history") } } }
            GhostButton("Apagar tudo e recomeçar", danger = true) { confirm = "Apagar TODOS os dados e recomeçar o cadastro? Isso não pode ser desfeito." to { Store.resetAll() } }
        }
        Muted("Meu Banco & Patrimônio · versão 51.0-kotlin")
    }
    }
    pinDialog.let { if (it) PinSetDialog { pinDialog = false } }
    if (restore) {
        val files = remember { java.io.File(ctx.filesDir, "backups").listFiles()?.sortedByDescending { it.name }.orEmpty() }
        Sheet("Cópias automáticas", { restore = false }) {
            if (files.isEmpty()) Muted("Ainda não há cópias automáticas.")
            files.forEach { f ->
                GhostButton(f.name.removePrefix("auto-").removeSuffix(".json"), Modifier.fillMaxWidth()) {
                    try { Store.replace(decodeState(f.readText())); Ui.show("Cópia restaurada") } catch (e: Exception) { Ui.show("Cópia inválida") }
                    restore = false
                }
            }
            Muted("Restaurar substitui os dados atuais.")
        }
    }
    stmtItems?.let { items ->
        val known = s.importedKeys.toHashSet()
        val fresh = items.filter { it.amount != 0.0 && stmtKey(it) !in known }
        Sheet("Importar extrato", { stmtItems = null }) {
            Txt("${items.size} lançamento(s) no arquivo · ${fresh.size} novo(s)")
            KV("Entradas", formatBRL(fresh.filter { it.amount > 0 }.sumOf { it.amount }), P.green)
            KV("Saídas", formatBRL(fresh.filter { it.amount < 0 }.sumOf { it.amount }), P.red)
            fresh.take(8).forEach { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Txt(it.dateISO.substring(5) + " " + it.desc, Modifier.weight(1f), style = T.small, maxLines = 1); Txt(formatBRL(it.amount), style = T.small) } }
            if (fresh.size > 8) Muted("… e mais ${fresh.size - 8}")
            PrimaryButton("Importar ${fresh.size}", enabled = fresh.isNotEmpty()) {
                val r = Store.mutate { applyStatement(it, items) }
                Ui.show("Importado", "${r.added} novo(s), ${r.skipped} ignorado(s)"); stmtItems = null
            }
        }
    }
    confirm?.let { (t, f) -> Confirm(t, { f(); confirm = null }, { confirm = null }, "Apagar") }
}

@Composable
private fun PinSetDialog(onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    FormDialog("Definir PIN", onDismiss, "Salvar", {
        if (a.length < 4) { Ui.show("Use ao menos 4 dígitos."); false }
        else if (a != b) { Ui.show("Os PINs não conferem."); false }
        else { Store.mutate { st -> st.settings.pinHash = hashPin(a); st.settings.pinEnabled = true }; true }
    }) {
        Field(a, { a = it.filter { c -> c.isDigit() }.take(8) }, "Novo PIN", keyboard = KeyboardType.NumberPassword)
        Field(b, { b = it.filter { c -> c.isDigit() }.take(8) }, "Repita o PIN", keyboard = KeyboardType.NumberPassword)
    }
}
