package app.coc.tracker

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object Alarms {
    private const val MAX_ALARMS = 64
    private const val CHANNEL_ID = "upgrades_finished"

    private fun pendingIntent(context: Context, requestCode: Int, text: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(context: Context, state: State) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (index in 0 until MAX_ALARMS) {
            alarmManager.cancel(pendingIntent(context, index, ""))
        }

        val nowMillis = System.currentTimeMillis()
        state.items.sortedBy(state::endOf).take(MAX_ALARMS).forEachIndexed { index, item ->
            val triggerAtMillis = state.endOf(item) * 1000L
            if (triggerAtMillis <= nowMillis) return@forEachIndexed

            val alarm = pendingIntent(
                context,
                index,
                "${item.name} is now level ${item.lvl + 1}",
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarm)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarm)
            }
        }
    }

    fun reschedule(context: Context) {
        Store.load(context)?.let { schedule(context, it) }
    }

    fun showCompletion(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Upgrades finished", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Upgrade finished")
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(text.hashCode(), notification)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        Alarms.showCompletion(context, text)
        TimerWidget.refresh(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.reschedule(context)
        TimerWidget.refresh(context)
    }
}
