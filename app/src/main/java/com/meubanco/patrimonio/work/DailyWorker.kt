package com.meubanco.patrimonio.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.meubanco.patrimonio.MainActivity
import com.meubanco.patrimonio.data.Backup
import com.meubanco.patrimonio.data.MarketApi
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*
import java.util.concurrent.TimeUnit

object Notifier {
    const val CH = "avisos"
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Avisos do banco", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(ctx)
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

/** Uma vez por dia: acompanha a data real, atualiza CDI/cotações, faz cópia automática e avisa vencimentos. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Store.init(ctx)
        val s = Store.state
        if (s.onboardingCompleted == false) return Result.success()
        try {
            syncToRealDate(s)
            MarketApi.fetchCdi(s)
            MarketApi.refreshAssets(s)
            try { if (com.meubanco.patrimonio.data.PluggyApi.hasCreds(ctx)) com.meubanco.patrimonio.data.PluggyApi.syncNow(ctx, s) } catch (e: Exception) {}
            Store.commit()
            Store.writeNow()
            if (s.settings.autoBackupEnabled) { Backup.autoLocalCopy(ctx); Backup.writeCloud(ctx); com.meubanco.patrimonio.data.GoogleDrive.dailyBackup(ctx) }
            if (s.settings.notifyBudget) newBudgetAlerts(s).forEach { Notifier.show(ctx, 30 + Math.abs(it.category.hashCode() % 1000), "Orçamento: ${it.category}", "Você usou ${formatBRL(it.spent)} de ${formatBRL(it.limit)} neste mês.") }
            Store.commit()
            // avisos
            for (c in allCards(s)) {
                if (!c.enabled || !s.settings.notifyCard) continue
                if (c.pendingAmount > 0 && c.pendingDueYM != null) {
                    val due = c.pendingDueYM + "-" + c.dueDay.toString().padStart(2, '0')
                    val left = try { daysBetweenISO(AppClock.todayISO(), due) } catch (e: Exception) { 99 }
                    if (left in 0..s.settings.notifyCardDays)
                        Notifier.show(ctx, 10 + c.last4.toIntOrNull().orEmpty(), "Fatura do cartão", "Fatura de " + formatBRL(c.pendingAmount) + " vence em " + left + " dia(s).")
                }
            }
            if (s.settings.notifyWeekly && isoToDate(AppClock.todayISO()).dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                val key = "week|" + AppClock.todayISO()
                if (s.budgetAlerted[key] != true) {
                    s.budgetAlerted[key] = true
                    s.budgetAlerted.keys.filter { it.startsWith("week|") && it < "week|" + addDaysISO(AppClock.todayISO(), -60) }.forEach { s.budgetAlerted.remove(it) }
                    Notifier.show(ctx, 50, "Seu resumo da semana", weekSummary(s).text())
                }
            }
            if (s.settings.notifyDebt && s.debts.any { it.balance > 0 }) {
                val key = "debt|" + ymKey(s.simDateISO)
                if (isoToDate(s.simDateISO).dayOfMonth <= 5 && s.budgetAlerted[key] != true) {
                    s.budgetAlerted[key] = true
                    Notifier.show(ctx, 40, "Dívidas do mês", "Parcelas mínimas somam ${formatBRL(debtMinTotal(s))}. Veja o plano de quitação em Planejamento.")
                }
            }
            val ds = daysSinceBackup(s.lastBackupAt ?: "")
            if (s.settings.notifyBackupDays > 0 && (ds == null || ds >= s.settings.notifyBackupDays))
                Notifier.show(ctx, 20, "Hora do backup", "Exporte um backup em Configurações → Dados.")
        } catch (e: Exception) { return Result.retry() }
        return Result.success()
    }

    private fun Int?.orEmpty() = this ?: 0

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
