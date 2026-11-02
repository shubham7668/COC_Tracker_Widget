package app.coc.tracker

import android.app.Activity
import android.content.ClipboardManager
import android.widget.Toast

/** Invisible. Reads the clipboard the moment it gets focus (Android only allows that to the focused app), then closes. */
class ImportActivity : Activity() {
    private var done = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        done = true
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        val st = Parser.parse(text, Names.get(this))
        if (st == null) {
            Toast.makeText(this, "Clipboard doesn't hold a Clash of Clans export", Toast.LENGTH_LONG).show()
        } else {
            Store.commit(this, st)
            Toast.makeText(this, "Updated, ${st.items.size} timers", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
