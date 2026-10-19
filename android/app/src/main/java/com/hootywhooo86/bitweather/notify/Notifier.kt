package com.hootywhooo86.bitweather.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hootywhooo86.bitweather.R
import com.hootywhooo86.bitweather.data.Api
import com.hootywhooo86.bitweather.data.Derive
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.WeatherBundle
import com.hootywhooo86.bitweather.data.Wmo
import com.hootywhooo86.bitweather.pixel.PixelIcons
import com.hootywhooo86.bitweather.ui.MainActivity

object Notifier {
    const val CH_ALERTS = "alerts"
    const val CH_NOW = "conditions"
    const val CH_UPDATES = "updates"
    private const val ID_NOW = 1
    private const val ID_UPDATE = 2
    private const val ID_TEST = 3

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Weather alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Official warnings and your custom alerts"
        })
        nm.createNotificationChannel(NotificationChannel(CH_NOW, "Current conditions", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Quiet update every 30 minutes"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A new version of Bitweather is available"
        })
    }

    fun allowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("MissingPermission")
    private fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        if (!allowed(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(id, b.build()) }
    }

    /** New alerts notify once each; the conditions notification is refreshed in place. */
    fun process(ctx: Context, b: WeatherBundle) {
        val alerts = Derive.allAlerts(b)
        val where = b.place.name
        val w = b.weather
        val icon = PixelIcons.bitmap(Wmo.kind(w.current.code), w.current.isDay, 128)
        synchronized(Prefs) {
            for (a in alerts) {
                if (!Prefs.seen.add(a.key)) continue
                if (!Prefs.notifyAlerts) continue
                val nb = NotificationCompat.Builder(ctx, CH_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_bw)
                    .setContentTitle("${if (a.type == "warning") "⚠ " else ""}${a.title} — $where")
                    .setContentText(a.name.ifBlank { a.text.take(140) })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(a.name, a.text).filter { it.isNotBlank() }.joinToString("\n\n").take(1500)))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setColor(0xFFB388FF.toInt())
                    .setContentIntent(openApp(ctx))
                    .setAutoCancel(true)
                post(ctx, 1000 + (a.key.hashCode() and 0xFFFFF), nb)
            }
            Prefs.save()
        }
        if (Prefs.notifyConditions) {
            val nb = NotificationCompat.Builder(ctx, CH_NOW)
                .setSmallIcon(R.drawable.ic_stat_bw)
                .setLargeIcon(icon)
                .setContentTitle("$where  ${com.hootywhooo86.bitweather.data.Fmt.t(w.current.temp)}")
                .setContentText(Derive.nowSummary(w))
                .setStyle(NotificationCompat.BigTextStyle().bigText(Derive.nowSummary(w) + "\n" + Derive.insight(b).second))
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(true)
                .setColor(0xFFB388FF.toInt())
                .setContentIntent(openApp(ctx))
            post(ctx, ID_NOW, nb)
        } else {
            NotificationManagerCompat.from(ctx).cancel(ID_NOW)
        }
    }

    fun appUpdate(ctx: Context, rel: Api.Release) {
        val url = rel.apkUrl ?: rel.pageUrl
        val pi = PendingIntent.getActivity(ctx, 1, Intent(Intent.ACTION_VIEW, Uri.parse(url)), PendingIntent.FLAG_IMMUTABLE)
        val nb = NotificationCompat.Builder(ctx, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_bw)
            .setContentTitle("Bitweather update available (${rel.name})")
            .setContentText("Tap to download and install the new version.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Tap to download and install the new version.\n\n" + rel.notes.take(600)))
            .setColor(0xFFB388FF.toInt())
            .setContentIntent(pi)
            .setAutoCancel(true)
        post(ctx, ID_UPDATE, nb)
    }

    fun test(ctx: Context, b: WeatherBundle?) {
        val nb = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bw)
            .setContentTitle("Bitweather — ${b?.place?.name ?: "test"}")
            .setContentText(b?.let { Derive.nowSummary(it.weather) } ?: "Notifications are working.")
            .setColor(0xFFB388FF.toInt())
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
        post(ctx, ID_TEST, nb)
    }
}
