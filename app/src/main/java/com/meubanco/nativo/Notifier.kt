package com.meubanco.nativo

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Notifier {
    const val CHANNEL = "avisos"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos do Meu Banco", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    /** Agenda uma verificação por dia, por volta das 9h. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("avisos-diarios", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || hasPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
}

class DailyWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        try {
            val ctx = applicationContext
            val f = File(ctx.filesDir, "state.json")
            if (!f.exists() || !Notifier.canNotify(ctx)) return Result.success()
            val s = mergeWithDefaults(JSONObject(f.readText()))
            if (!s.sub("settings").optBoolean("notificationsEnabled", true)) return Result.success()
            s.syncToRealDate() // só em memória: o app grava quando abrir
            val wanted = setOf("Fatura chegando", "DARF a pagar", "Ponto de hoje", "Hoje é domingo", "Saldo abaixo dos gastos fixos", "Faça um backup")
            val items = s.computeInsights().filter { it.title in wanted }.take(3)
            if (items.isEmpty()) return Result.success()
            Notifier.ensureChannel(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = items.joinToString("\n") { "${it.ico} ${it.title}: ${it.desc}" }
            val n = NotificationCompat.Builder(ctx, Notifier.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Meu Banco")
                .setContentText(items.first().title + ": " + items.first().desc)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(1001, n)
        } catch (e: Exception) {
        }
        return Result.success()
    }
}
