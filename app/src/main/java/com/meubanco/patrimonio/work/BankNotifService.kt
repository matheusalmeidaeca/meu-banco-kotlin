package com.meubanco.patrimonio.work

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.meubanco.patrimonio.data.BankNotif

/** Só olha notificações de apps de banco conhecidos. Ignora todo o resto. */
class BankNotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (BankNotif.bankOf(sbn.packageName) == null) return
            val ex = sbn.notification.extras
            val title = ex.getCharSequence("android.title")?.toString() ?: ""
            val text = (ex.getCharSequence("android.bigText") ?: ex.getCharSequence("android.text"))?.toString() ?: ""
            val cap = BankNotif.parse(sbn.packageName, title, text, System.currentTimeMillis()) ?: return
            BankNotif.add(applicationContext, cap)
        } catch (e: Exception) { /* nunca derruba o serviço */ }
    }
}
