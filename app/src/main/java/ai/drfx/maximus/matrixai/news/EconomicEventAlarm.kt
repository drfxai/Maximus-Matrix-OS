package ai.drfx.maximus.matrixai.news

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R

/** A 15-minute advance reminder for dated high-impact releases. */
object EconomicEventAlarm {
    private const val CHANNEL = "economic_calendar_events"
    fun schedule(context: Context, events: List<EconomicEvent>): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val preferences = context.getSharedPreferences("economic_event_alarms", Context.MODE_PRIVATE)
        preferences.getStringSet("scheduled_ids", emptySet()).orEmpty().forEach { id ->
            manager.cancel(pendingIntent(context, id, "", "", ""))
        }
        val now = System.currentTimeMillis()
        val scheduledIds = mutableSetOf<String>()
        events.filter { it.impact == MarketImpact.HIGH }.forEach { event ->
            val trigger = (event.startsAtMillis ?: return@forEach) - 15 * 60_000L
            if (trigger <= now) return@forEach
            val pending = pendingIntent(context, event.id, event.eventName, event.currency, event.time)
            if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            scheduledIds.add(event.id)
        }
        preferences.edit().putStringSet("scheduled_ids", scheduledIds).apply()
        return exact
    }

    private fun pendingIntent(context: Context, id: String, title: String, currency: String, time: String): PendingIntent {
        val intent = Intent(context, EconomicEventAlarmReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("currency", currency)
            putExtra("time", time)
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, title: String, currency: String, time: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Economic calendar reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify("$currency:$title:$time".hashCode(), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_signal_alert)
            .setContentTitle("In 15 minutes · $currency")
            .setContentText("$title · $time local time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}

class EconomicEventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EconomicEventAlarm.show(context, intent.getStringExtra("title").orEmpty(), intent.getStringExtra("currency").orEmpty(), intent.getStringExtra("time").orEmpty())
    }
}
