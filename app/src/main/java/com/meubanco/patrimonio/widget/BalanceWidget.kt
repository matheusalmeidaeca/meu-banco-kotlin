package com.meubanco.patrimonio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.meubanco.patrimonio.MainActivity
import com.meubanco.patrimonio.R
import com.meubanco.patrimonio.data.Store
import com.meubanco.patrimonio.domain.*

/** Widget da tela inicial: saldo, patrimônio e atalho "registrar gasto". */
class BalanceWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Store.init(ctx)
        ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
    }

    companion object {
        const val ACTION_QUICK_SPEND = "com.meubanco.patrimonio.QUICK_SPEND"

        fun build(ctx: Context): RemoteViews {
            val s = Store.state
            val rv = RemoteViews(ctx.packageName, R.layout.widget_balance)
            val hide = s.settings.hideBalance || s.onboardingCompleted == false
            rv.setTextViewText(R.id.w_balance, if (s.onboardingCompleted == false) "Abra o app" else if (hide) "R$ •••••" else formatBRL(s.cash))
            rv.setTextViewText(R.id.w_net, if (hide) "" else "Patrimônio " + formatBRL(totalNetWorth(s)))
            val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val spend = PendingIntent.getActivity(ctx, 2,
                Intent(ctx, MainActivity::class.java).setAction(ACTION_QUICK_SPEND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.w_root, open)
            rv.setOnClickPendingIntent(R.id.w_spend, spend)
            return rv
        }

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, BalanceWidget::class.java))
                if (ids.isEmpty()) return
                val rv = build(ctx)
                ids.forEach { mgr.updateAppWidget(it, rv) }
            } catch (_: Exception) {}
        }
    }
}
