package app.coc.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Night = Color(0xFF15131F)
private val Card = Color(0xFF1F1C2E)
private val Txt = Color(0xFFECE8F5)
private val Dim = Color(0xFF8E89A6)

private fun accent(g: Group) = when (g) {
    Group.BUILDER -> Color(0xFFF2C14E)
    Group.RESEARCH -> Color(0xFFD98BF0)
    Group.BUILDER_HALL -> Color(0xFF7FB2E5)
    Group.PET -> Color(0xFF6FD3C0)
    Group.HELPER, Group.SUPER_OR_EVENT -> Color(0xFF8E89A6)
}

class MainActivity : ComponentActivity() {
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { Screen(version) }
    }

    override fun onResume() {
        super.onResume()
        version++
    }
}

@Composable
fun Screen(version: Int) {
    val ctx = LocalContext.current
    var st by remember(version) { mutableStateOf(Store.load(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    var dialog by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    val commit = { n: State -> Store.commit(ctx, n); st = n }
    val cur = st

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent(Group.BUILDER), surface = Card, onSurface = Txt, background = Night, onBackground = Txt,
        ),
    ) {
        Column(Modifier.fillMaxSize().background(Night).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Upgrades", color = Txt, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { ctx.startActivity(Intent(ctx, ImportActivity::class.java)) }) {
                    Text("Import", color = accent(Group.BUILDER), fontSize = 16.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PotionChip("Builder potion", "10×", 1, cur, now, accent(Group.BUILDER), Modifier.weight(1f)) { dialog = 1 }
                PotionChip("Research potion", "24×", 2, cur, now, accent(Group.RESEARCH), Modifier.weight(1f)) { dialog = 2 }
            }
            if (cur == null || cur.items.isEmpty()) {
                Text(
                    if (cur == null) "Copy your data export in Clash of Clans, then tap Import." else "No upgrades running in this export.",
                    color = Dim, fontSize = 15.sp, modifier = Modifier.padding(top = 32.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
                    for (g in Group.display) {
                        val rows = cur.items.filter { it.g == g }.sortedBy { cur.endOf(it) }
                        if (rows.isEmpty()) continue
                        item { Text(g.label, color = Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) }
                        items(rows) { ItemCard(it, cur, now, accent(g)) }
                    }
                }
            }
        }

        if (dialog != 0 && cur != null) {
            val p = dialog
            val hasManual = (if (p == 1) cur.bMan else cur.rMan).isNotEmpty()
            AlertDialog(
                onDismissRequest = { dialog = 0 },
                title = { Text(if (p == 1) "Use a builder potion?" else "Use a research potion?") },
                text = {
                    Text(
                        if (p == 1) "Builders work 10× faster for one hour, starting now. Every builder timer adjusts."
                        else "Lab research runs 24× faster for one hour, starting now. Every research timer adjusts.",
                    )
                },
                confirmButton = { TextButton(onClick = { commit(cur.usePotion(p, now)); dialog = 0 }) { Text("Use now") } },
                dismissButton = {
                    Row {
                        if (hasManual) TextButton(onClick = { commit(cur.clearManual(p)); dialog = 0 }) { Text("Undo my potions") }
                        TextButton(onClick = { dialog = 0 }) { Text("Cancel") }
                    }
                },
            )
        }
    }
}

@Composable
fun PotionChip(label: String, rate: String, pot: Int, s: State?, now: Long, accent: Color, mod: Modifier, onClick: () -> Unit) {
    val left = (s?.boostEnd(pot, now) ?: 0L) - now
    Column(mod.clip(RoundedCornerShape(16.dp)).background(Card).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = Dim, fontSize = 12.sp)
        Text(
            if (left > 0) "$rate, ${dur(left)} left" else "Tap to use",
            color = if (left > 0) accent else Txt, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun ItemCard(i: Item, s: State, now: Long, accent: Color) {
    val end = s.endOf(i)
    val left = end - now
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(i.name, color = Txt, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("To level ${i.lvl + 1}", color = Dim, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (left <= 0) "Ready" else dur(left),
                color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(if (left <= 0) "" else clock(end), color = Dim, fontSize = 12.sp)
        }
    }
}
