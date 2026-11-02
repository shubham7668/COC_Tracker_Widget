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
    private const val MAX = 64

    private fun pi(c: Context, i: Int, text: String): PendingIntent =
        PendingIntent.getBroadcast(
            c, i,
            Intent(c, AlarmReceiver::class.java).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(c: Context, s: State) {
        val am = c.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX) am.cancel(pi(c, i, ""))
        val now = System.currentTimeMillis()
        s.items.sortedBy { s.endOf(it) }.take(MAX).forEachIndexed { i, item ->
            val at = s.endOf(item) * 1000
            if (at <= now) return@forEachIndexed
            val p = pi(c, i, "${item.name} is now level ${item.lvl + 1}")
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = i.getStringExtra("text") ?: return
        if (text.isEmpty()) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("done", "Upgrades finished", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, "done")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Upgrade finished")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(text.hashCode(), n)
        TimerWidget.refresh(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.load(c)?.let { Alarms.schedule(c, it) }
        TimerWidget.refresh(c)
    }
}
