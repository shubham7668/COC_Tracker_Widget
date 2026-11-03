package app.coc.tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class TimerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { m.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, TimerWidget::class.java)).forEach { m.updateAppWidget(it, build(c)) }
        }

        private fun color(g: Group): Int = when (g) {
            Group.BUILDER -> 0xFFF2C14E
            Group.RESEARCH -> 0xFFD98BF0
            Group.BUILDER_HALL -> 0xFF7FB2E5
            Group.PET -> 0xFF6FD3C0
            Group.HELPER, Group.SUPER_OR_EVENT -> 0xFF8E89A6
        }.toInt()

        private fun build(c: Context): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget)
            val fl = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), fl))
            rv.setOnClickPendingIntent(R.id.import_btn, PendingIntent.getActivity(c, 1, Intent(c, ImportActivity::class.java), fl))
            rv.removeAllViews(R.id.list)

            val st = Store.load(c)
            val nowS = System.currentTimeMillis() / 1000

            if (st == null || st.items.isEmpty()) {
                val row = RemoteViews(c.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.name, if (st == null) "Copy your export, then tap Import" else "Nothing upgrading")
                row.setViewVisibility(R.id.time, View.GONE)
                row.setViewVisibility(R.id.lvl, View.GONE)
                rv.addView(R.id.list, row)
                return rv
            }

            for (g in Group.display) {
                val rows = st.items.filter { it.g == g }.sortedBy { st.endOf(it) }
                if (rows.isEmpty()) continue
                val sec = RemoteViews(c.packageName, R.layout.widget_section)
                sec.setTextViewText(R.id.title, g.label)
                rv.addView(R.id.list, sec)
                for (item in rows) {
                    val row = RemoteViews(c.packageName, R.layout.widget_row)
                    row.setTextViewText(R.id.name, item.name)
                    row.setTextViewText(R.id.lvl, "Lv ${item.lvl + 1}")
                    val end = st.endOf(item)
                    val left = end - nowS
                    if (left <= 0) {
                        row.setViewVisibility(R.id.time, View.GONE)
                        row.setViewVisibility(R.id.done, View.VISIBLE)
                        row.setTextColor(R.id.done, color(g))
                    } else if (left >= 86400) {
                        row.setViewVisibility(R.id.time, View.GONE)
                        row.setViewVisibility(R.id.days, View.VISIBLE)
                        row.setTextViewText(R.id.days, "${left / 86400}d ${left % 86400 / 3600}h")
                        row.setTextColor(R.id.days, color(g))
                    } else {
                        row.setViewVisibility(R.id.days, View.GONE)
                        row.setViewVisibility(R.id.time, View.VISIBLE)
                        row.setTextViewText(R.id.time, dur(left))
                        row.setTextColor(R.id.time, color(g))
                    }
                    rv.addView(R.id.list, row)
                }
            }
            return rv
        }
    }
}
