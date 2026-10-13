package com.meubanco.nativo

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent { AppRoot(store) }
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
fun AppRoot(store: Store) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf("conta") }
    var menuOpen by remember { mutableStateOf(false) }

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
            Toast.makeText(ctx, if (ok) "Backup exportado!" else "Não foi possível exportar.", Toast.LENGTH_LONG).show()
        }
    }
    val onImport: () -> Unit = { importLauncher.launch(arrayOf("*/*")) }
    val onExport: () -> Unit = { exportLauncher.launch("backup-carteira.json") }

    val st = store.state
    val isEmployee = st == null || st.optJSONObject("income")?.optString("incomeType", "empregado") == "empregado"
    val tabs = ALL_TABS.filter { it.id != "ponto" || isEmployee }
    if (tabs.none { it.id == tab }) tab = "conta"

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = p.brand,
            background = p.bg,
            surface = p.card,
            onBackground = p.text,
            onSurface = p.text,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Cabeçalho: botão ☰ + título
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
                    "historico" -> Placeholder("Extrato & histórico", "etapa 4")
                    "carteira" -> Placeholder("Carteira de investimentos", "etapa 5")
                    "gastos" -> Placeholder("Gastos", "etapa 3")
                    "ponto" -> PontoPanel(store)
                    "config" -> ConfigPanel(store, onImport, onExport)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ContaPanel(store: Store, onImport: () -> Unit, goTo: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val st = store.state
    var hidden by rememberSaveable { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(p.brand))
        Spacer(Modifier.width(6.dp))
        Text("Banco", color = p.muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))

    if (st == null) {
        CardBox {
            Text("Nenhum dado ainda", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "No app antigo, abra Configurações → Exportar backup. Depois toque abaixo e escolha o arquivo.",
                color = p.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("⬆ Importar backup", onClick = onImport)
        }
        return
    }

    val cash = st.optDouble("cash", 0.0)
    val acc = st.optJSONObject("account")
    val agency = acc?.optString("agency", "0001") ?: "0001"
    val number = acc?.optString("number", "") ?: ""
    val heroShape = RoundedCornerShape(22.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(Brush.linearGradient(listOf(p.brandDim, p.bgSoft)))
            .border(1.dp, p.border, heroShape)
            .padding(22.dp),
    ) {
        Column {
            Text("Saldo em conta corrente", color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (hidden) "R$ ••••" else brl(cash),
                color = p.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text("Agência $agency · Conta $number", color = p.muted, fontSize = 12.sp)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .clip(CircleShape)
                .clickable { hidden = !hidden },
            contentAlignment = Alignment.Center,
        ) { Text("👁", fontSize = 18.sp) }
    }
    Spacer(Modifier.height(14.dp))

    // Ações rápidas
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction("🐷", "Caixinhas") { Toast.makeText(ctx, "Em breve", Toast.LENGTH_SHORT).show() }
        QuickAction("🔁", "Pix") { Toast.makeText(ctx, "Em breve", Toast.LENGTH_SHORT).show() }
        QuickAction("💳", "Cartão") { Toast.makeText(ctx, "Em breve", Toast.LENGTH_SHORT).show() }
        QuickAction("📄", "Extrato") { goTo("historico") }
        QuickAction("📈", "Investir") { goTo("carteira") }
        QuickAction("🛒", "Compras") { goTo("gastos") }
    }
    Spacer(Modifier.height(16.dp))

    CardBox {
        Text("Etapa 1 concluída ✔", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Seu saldo veio do backup do app antigo. As demais telas chegam nas próximas etapas.",
            color = p.muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun ConfigPanel(store: Store, onImport: () -> Unit, onExport: () -> Unit) {
    val p = LocalPalette.current
    val st = store.state
    CardBox {
        Text("💾 Dados & backup", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (st == null) "Nenhum backup carregado."
            else "Backup carregado (data da simulação: ${st.optString("simDateISO", "—")}).",
            color = p.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton("⬆ Importar backup", onClick = onImport)
        Spacer(Modifier.height(8.dp))
        GhostButton("⬇ Exportar backup agora", onClick = onExport)
    }
}

@Composable
fun Placeholder(title: String, etapa: String) {
    val p = LocalPalette.current
    CardBox {
        Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Esta tela chega na $etapa da reescrita.", color = p.muted, fontSize = 13.sp)
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
