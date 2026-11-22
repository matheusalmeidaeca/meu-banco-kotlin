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
        val d = i?.data
        if (d != null && d.scheme == "admoney" && d.host == "pluggy") Ui.pluggyResult = d.query ?: "closed=1"
    }

    override fun onPause() { super.onPause(); Store.writeNow() }
}
