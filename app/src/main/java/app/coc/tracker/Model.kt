package app.coc.tracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Group(val label: String) {
    BUILDER("Builders"), RESEARCH("Research"), BUILDER_HALL("Builder Hall"), PET("Pets"), HELPER("Helpers"), SUPER_OR_EVENT("Other");

    companion object {
        /** Only upgrade categories appear in the app and widget. */
        val display = listOf(BUILDER, RESEARCH, PET, BUILDER_HALL)
    }
}

/** pot: 1 = Builder Potion applies, 2 = Research Potion applies, 0 = neither. rem = seconds left at export time. */
data class Item(val id: Long, val name: String, val lvl: Int, val g: Group, val rem: Long, val pot: Int)

const val BUILDER_RATE = 10L
const val RESEARCH_RATE = 24L
const val POTION_SECONDS = 3600L

/** Finish time (epoch seconds) of [rem] seconds of work started at [t0], with k-times speed inside [wins]. */
fun finish(rem: Long, t0: Long, wins: List<Pair<Long, Long>>, k: Long): Long {
    var t = t0
    var r = rem
    for ((s, e) in wins) {
        if (e <= t) continue
        val s2 = maxOf(s, t)
        val gap = s2 - t
        if (r <= gap) return t + r
        r -= gap
        t = s2
        val cap = (e - t) * k
        if (r <= cap) return t + (r + k - 1) / k
        r -= cap
        t = e
    }
    return t + r
}

data class State(
    val ts: Long,
    val items: List<Item>,
    val bEnd: Long,
    val rEnd: Long,
    val bMan: List<Pair<Long, Long>> = emptyList(),
    val rMan: List<Pair<Long, Long>> = emptyList(),
) {
    /** Boost windows for a potion type: the one active in the export, plus any potions used in-app. */
    fun windows(pot: Int): List<Pair<Long, Long>> {
        val end = if (pot == 1) bEnd else rEnd
        val man = if (pot == 1) bMan else rMan
        return (listOfNotNull(if (end > ts) ts to end else null) + man).sortedBy { it.first }
    }

    fun endOf(i: Item): Long = when (i.pot) {
        1 -> finish(i.rem, ts, windows(1), BUILDER_RATE)
        2 -> finish(i.rem, ts, windows(2), RESEARCH_RATE)
        else -> ts + i.rem
    }

    /** End of the running boost (epoch seconds), or 0 if none is running at [now]. */
    fun boostEnd(pot: Int, now: Long): Long =
        windows(pot).maxOfOrNull { it.second }?.takeIf { it > now } ?: 0L

    /** A potion used at [now] starts right away, or after the boost already running. */
    fun usePotion(pot: Int, now: Long): State {
        val start = maxOf(now, windows(pot).maxOfOrNull { it.second } ?: 0L)
        val w = start to start + POTION_SECONDS
        return if (pot == 1) copy(bMan = bMan + w) else copy(rMan = rMan + w)
    }

    fun clearManual(pot: Int): State = if (pot == 1) copy(bMan = emptyList()) else copy(rMan = emptyList())

    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", ts)
        put("bEnd", bEnd)
        put("rEnd", rEnd)
        put("bMan", wj(bMan))
        put("rMan", wj(rMan))
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("id", it.id).put("n", it.name).put("l", it.lvl).put("g", it.g.ordinal).put("r", it.rem).put("p", it.pot))
        }
        put("items", a)
    }

    companion object {
        fun fromJson(o: JSONObject): State {
            val a = o.getJSONArray("items")
            val items = (0 until a.length()).map {
                val x = a.getJSONObject(it)
                Item(x.getLong("id"), x.getString("n"), x.getInt("l"), Group.values()[x.getInt("g")], x.getLong("r"), x.getInt("p"))
            }
            return State(o.getLong("ts"), items, o.getLong("bEnd"), o.getLong("rEnd"), rw(o.getJSONArray("bMan")), rw(o.getJSONArray("rMan")), )
        }

        private fun rw(a: JSONArray): List<Pair<Long, Long>> =
            (0 until a.length()).map { val p = a.getJSONArray(it); p.getLong(0) to p.getLong(1) }
    }
}

private fun wj(l: List<Pair<Long, Long>>): JSONArray {
    val a = JSONArray()
    l.forEach { a.put(JSONArray().put(it.first).put(it.second)) }
    return a
}

object Names {
    private var map: Map<String, String>? = null
    fun get(c: Context): Map<String, String> = map ?: run {
        val o = JSONObject(c.assets.open("names.json").bufferedReader().use { it.readText() })
        val m = HashMap<String, String>()
        o.keys().forEach { m[it] = o.getString(it) }
        map = m
        m
    }
}

object ItemCategorizer {
    private val builderHallBuildings = setOf(1000063L, 1000065L, 1000078L, 1000080L, 1000081L, 1000082L)
    private val builderHallHeroes = setOf(28000003L, 28000005L)
    private val builderHallTroops = setOf(4000070L, 4000106L)
    private val superEventIds = setOf(
        1000083L,
        4000026L, 4000027L, 4000028L, 4000029L, 4000055L, 4000056L, 4000057L,
        4000063L, 4000064L, 4000066L, 4000076L, 4000080L, 4000081L, 4000083L,
        4000084L, 4000098L, 4000147L,
        4000030L, 4000045L, 4000047L, 4000048L, 4000050L, 4000061L, 4000067L,
        4000072L, 4000094L, 4000101L, 4000102L, 4000103L, 4000104L, 4000118L,
        4000119L, 4000120L, 4000121L, 4000122L, 4000125L, 4000130L, 4000136L,
        4000142L, 4000156L, 4000157L, 4000158L, 4000159L, 4000167L, 4000185L,
        4000190L, 4000194L, 26000006L, 26000073L
    )

    fun categorize(id: Long): Group = when {
        id in 1000033L..1000058L || id in builderHallBuildings ||
            id in 12000010L..12000014L || id in builderHallHeroes ||
            id in 4000031L..4000042L || id in builderHallTroops -> Group.BUILDER_HALL
        id in superEventIds -> Group.SUPER_OR_EVENT
        id in 93000000L..93999999L -> Group.HELPER
        id in 73000000L..73999999L -> Group.PET
        id / 1_000_000L in setOf(1L, 12L, 28L, 102L, 103L, 107L) -> Group.BUILDER
        id / 1_000_000L in setOf(4L, 26L) -> Group.RESEARCH
        else -> Group.SUPER_OR_EVENT
    }

}

object Parser {
    /** Parses active upgrade timers; category is derived from the item ID and export source. */
    fun parse(text: String, names: Map<String, String>): State? {
        val o = try { JSONObject(text.trim()) } catch (e: Exception) { return null }
        val ts = o.optLong("timestamp", 0)
        if (ts == 0L || !o.has("buildings")) return null
        val items = ArrayList<Item>()
        fun scan(key: String, potion: Int) {
            val a = o.optJSONArray(key) ?: return
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val remaining = x.optLong("timer", 0)
                if (remaining <= 0) continue
                val id = x.optLong("data", 0)
                val group = if (key.endsWith("2")) Group.BUILDER_HALL else ItemCategorizer.categorize(id)
                if (group == Group.HELPER || group == Group.SUPER_OR_EVENT) continue
                val potion = when (group) {
                    Group.BUILDER -> 1
                    Group.RESEARCH -> 2
                    else -> 0
                }
                items.add(Item(id, names[id.toString()] ?: "#$id", x.optInt("lvl", 0), group, remaining, potion))
            }
        }
        listOf("buildings", "traps", "heroes", "guardians").forEach { scan(it, 1) }
        listOf("units", "siege_machines", "spells").forEach { scan(it, 2) }
        scan("pets", 0)
        listOf("buildings2", "traps2", "heroes2", "units2").forEach { scan(it, 0) }
        val boosts = o.optJSONObject("boosts")
        return State(ts, items, ts + (boosts?.optLong("builder_boost", 0) ?: 0L), ts + (boosts?.optLong("research_boost", 0) ?: 0L))
    }
}

fun dur(s: Long): String {
    val d = s / 86400
    val h = s % 86400 / 3600
    val m = s % 3600 / 60
    return when {
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m ${s % 60}s"
    }
}

fun clock(epochSec: Long): String = SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(epochSec * 1000))
