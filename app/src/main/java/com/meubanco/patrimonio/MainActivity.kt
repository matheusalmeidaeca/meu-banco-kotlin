package com.meubanco.patrimonio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*
import com.meubanco.patrimonio.ui.*
import com.meubanco.patrimonio.work.DailyWorker
import com.meubanco.patrimonio.work.Notifier

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        handleIntent(intent)
        Notifier.ensureChannel(this)
        DailyWorker.schedule(this)
        setContent {
            val s = state()
            var unlocked by rememberSaveable { mutableStateOf(false) }
            // volta do segundo plano: exige PIN de novo e acompanha a data real
            var stoppedAt by rememberSaveable { mutableStateOf(0L) }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stoppedAt = System.currentTimeMillis(); Store.writeNow(); Store.flushCloud() }
            LaunchedEffect(s.settings.secureScreen) {
                if (s.settings.secureScreen) window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt >= Store.state.settings.lockAfterSec * 1000L) unlocked = false
                if (Store.state.onboardingCompleted != false) {
                    val n = Store.mutate { syncToRealDate(it) }
                    if (n > 0) Ui.show("Contas em dia", "$n dia(s) processado(s)")
                }
            }
            AppTheme(s.settings.theme, s.settings.fontSize, s.settings.highContrast) {
                val needLock = s.settings.pinEnabled && s.settings.pinHash != null && !unlocked && s.onboardingCompleted != false
                when {
                    needLock -> LockScreen { unlocked = true }
                    s.onboardingCompleted == false -> OnboardingScreen()
                    else -> MainScaffold()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(i: android.content.Intent?) {
        if (i?.action == com.meubanco.patrimonio.widget.BalanceWidget.ACTION_QUICK_SPEND) Ui.quickSpend = true
        if (i?.action == android.content.Intent.ACTION_SEND && i.type == "text/plain") {
            val txt = i.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
            val cap = com.meubanco.patrimonio.data.BankNotif.parseText(txt, System.currentTimeMillis())
            if (cap != null) { com.meubanco.patrimonio.data.BankNotif.add(applicationContext, cap); Ui.show("Sugestão criada: Mais → Capturas dos bancos") }
            else Ui.show("Não achei um valor em R$ nesse texto.")
        }
        if (i?.action == android.content.Intent.ACTION_SEND && i.type != "text/plain") {
            @Suppress("DEPRECATION") val uri = i.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (uri != null) {
                val txt = try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    val u = String(bytes, Charsets.UTF_8); if (u.contains('\uFFFD')) String(bytes, Charsets.ISO_8859_1) else u
                } catch (e: Exception) { "" }
                if (txt.isNotBlank()) { Ui.sharedStatement = txt; Store.mutate { it.activeMainTab = "importar" } }
                else Ui.show("Não consegui abrir esse arquivo.")
            }
        }
        val d = i?.data
        if (d != null && d.scheme == "admoney" && d.host == "pluggy") Ui.pluggyResult = d.query ?: "closed=1"
    }

    override fun onPause() { super.onPause(); Store.writeNow() }
}
