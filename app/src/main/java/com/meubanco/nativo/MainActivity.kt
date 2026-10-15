package com.meubanco.nativo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var store: Store? = null

    /** Texto de um backup recebido (por exemplo, aberto a partir do anexo no Gmail), esperando confirmação. */
    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = Store(applicationContext)
        store = s
        Notifier.ensureChannel(this)
        Notifier.schedule(this)
        readIncoming(intent)
        setContent { AppRoot(s, incoming) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIncoming(intent)
    }

    @Suppress("DEPRECATION")
    private fun readIncoming(i: Intent?) {
        if (i == null) return
        val uri: Uri? = if (i.action == Intent.ACTION_VIEW) i.data else i.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) return
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) incoming.value = text
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        // Voltou para o app (talvez em outro dia): avança a simulação até hoje.
        store?.syncNow()
    }
}

data class TabInfo(val id: String, val label: String, val icon: String)

val ALL_TABS = listOf(
    TabInfo("conta", "Conta & cartão", "🏠"),
    TabInfo("historico", "Extrato & histórico", "📄"),
    TabInfo("carteira", "Carteira de investimentos", "📈"),
    TabInfo("gastos", "Gastos", "💸"),
    TabInfo("ponto", "Ponto", "🕐"),
    TabInfo("config", "Configurações", "⚙️"),
)

@Composable
fun AppRoot(store: Store, incoming: MutableState<String?>) {
    val ctx = LocalContext.current
    val st = store.current()
    val light = st?.optJSONObject("settings")?.optString("theme", "dark") == "light"
    val p = if (light) LightPalette else DarkPalette
    var tab by rememberSaveable { mutableStateOf("conta") }
    var menuOpen by remember { mutableStateOf(false) }
    var unlocked by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            val ok = text != null && store.importText(text)
            Toast.makeText(
                ctx,
                if (ok) "Backup importado!" else "Arquivo inválido: use um backup gerado pelo app.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportText()) }
                true
            } catch (e: Exception) {
                false
            }
            if (ok) store.markBackup()
            Toast.makeText(ctx, if (ok) "Backup exportado!" else "Não foi possível exportar.", Toast.LENGTH_LONG).show()
        }
    }
    val onImport: () -> Unit = { importLauncher.launch(arrayOf("*/*")) }
    val onExport: () -> Unit = { exportLauncher.launch("backup-carteira.json") }

    // Aviso quando a simulação avançou vários dias de uma vez.
    LaunchedEffect(store.lastSyncDays) {
        if (store.lastSyncDays > 1) {
            Toast.makeText(ctx, "Atualizei ${store.lastSyncDays} dia(s): salário, gastos e fatura já foram processados.", Toast.LENGTH_LONG).show()
        }
    }
    // CDI do dia (uma vez por dia, se houver internet).
    LaunchedEffect(st != null) {
        val s = store.state
        if (s != null && s.sub("market").str("cdiUpdatedAt") != todayISO()) store.updateCdi { }
    }

    val isEmployee = st == null || st.optJSONObject("income")?.optString("incomeType", "empregado") == "empregado"
    val tabs = ALL_TABS.filter { it.id != "ponto" || isEmployee }
    if (tabs.none { it.id == tab }) tab = "conta"

    val scheme = if (light) {
        lightColorScheme(primary = p.brand, background = p.bg, surface = p.card, onBackground = p.text, onSurface = p.text)
    } else {
        darkColorScheme(primary = p.brand, background = p.bg, surface = p.card, onBackground = p.text, onSurface = p.text)
    }

    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme) {
            val pinOn = st?.optJSONObject("settings")?.optBoolean("pinEnabled", false) == true
            if (pinOn && !unlocked) {
                PinLock(st?.optJSONObject("settings")?.optString("pinHash", "") ?: "") { unlocked = true }
                return@MaterialTheme
            }
            if (st != null && !st.optBoolean("onboardingCompleted", true)) {
                OnboardingScreen(store)
                return@MaterialTheme
            }
            val pending = incoming.value
            if (pending != null) {
                GConfirm(
                    "Recebi um arquivo de backup. Substituir os dados atuais por ele? Isso não pode ser desfeito.",
                    onYes = {
                        val ok = store.importText(pending)
                        incoming.value = null
                        Toast.makeText(ctx, if (ok) "Backup restaurado!" else "Arquivo inválido: use um backup gerado pelo app.", Toast.LENGTH_LONG).show()
                    },
                    onNo = { incoming.value = null },
                )
            }
            Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.card)
                                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                                .clickable { menuOpen = !menuOpen },
                            contentAlignment = Alignment.Center,
                        ) { Text("☰", color = p.text, fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Text("Meu Banco & Patrimônio", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Conta, cartão, investimentos, gastos e contabilidade num só lugar",
                        color = p.muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(14.dp))

                    if (menuOpen) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(p.card)
                                .border(1.dp, p.border, RoundedCornerShape(16.dp))
                                .padding(6.dp),
                        ) {
                            tabs.forEach { t ->
                                val active = t.id == tab
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) p.brandDim else Color.Transparent)
                                        .clickable { tab = t.id; menuOpen = false }
                                        .padding(horizontal = 12.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(t.icon, fontSize = 18.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        t.label,
                                        color = if (active) p.text else p.muted,
                                        fontSize = 15.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    when (tab) {
                        "conta" -> ContaPanel(store, onImport) { tab = it }
                        "historico" -> HistoricoPanel(store)
                        "carteira" -> CarteiraPanel(store)
                        "gastos" -> GastosPanel(store)
                        "ponto" -> PontoPanel(store)
                        "config" -> ConfigPanel(store, onImport, onExport)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun PinLock(hash: String, onOk: () -> Unit) {
    val p = LocalPalette.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text("Digite seu PIN", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            GField("", pin, { pin = it.filter { c -> c.isDigit() }.take(6); wrong = false }, keyboard = KeyboardType.NumberPassword)
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text("PIN incorreto.", color = p.coral, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Entrar") {
                if (simpleHash(pin) == hash) onOk() else wrong = true
            }
        }
    }
}

@Composable
fun CardBox(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.border, shape)
            .padding(18.dp),
    ) { content() }
}

@Composable
fun QuickAction(icon: String, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(74.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.cardAlt, p.card)))
            .border(1.dp, p.border, shape)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = p.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.brand)
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.brandInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, p.border, shape)
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}
