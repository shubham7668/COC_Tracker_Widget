package app.coc.tracker

import android.content.Context
import org.json.JSONObject

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("coc", Context.MODE_PRIVATE)

    fun load(c: Context): State? = try {
        sp(c).getString("state", null)?.let { State.fromJson(JSONObject(it)) }
    } catch (e: Exception) {
        null
    }

    /** Save imported timers and refresh the widget. */
    fun commit(c: Context, s: State) {
        sp(c).edit().putString("state", s.toJson().toString()).apply()
        Alarms.schedule(c, s)
        TimerWidget.refresh(c)
    }
}
