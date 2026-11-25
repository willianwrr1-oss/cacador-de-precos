package com.willian.cacadorprecos.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.willian.cacadorprecos.MainActivity
import com.willian.cacadorprecos.R
import com.willian.cacadorprecos.data.AlertItem

object Notifier {
    // Canal novo (v2): o padrão de vibração de um canal não pode ser alterado depois de criado.
    private const val CHANNEL = "alertas_preco_v2"
    private const val CANAL_ANTIGO = "alertas_preco"

    /** Vibração de 3 segundos: espera 0 ms e vibra 3000 ms. */
    private val VIBRACAO = longArrayOf(0L, 3000L)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.deleteNotificationChannel(CANAL_ANTIGO)
        val ch = NotificationChannel(CHANNEL, "Alertas de preço", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Aviso com vibração de 3 segundos quando o preço cai"
        ch.enableVibration(true)
        ch.vibrationPattern = VIBRACAO
        nm.createNotificationChannel(ch)
    }

    fun show(ctx: Context, a: AlertItem) {
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_price)
            .setColor(0xFF0E7C6B.toInt())
            .setContentTitle(a.title)
            .setContentText(a.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVibrate(VIBRACAO)
            .build()
        try {
            nm.notify(a.id.hashCode(), n)
        } catch (e: SecurityException) {
            // permissão de notificação negada
        }
    }
}
