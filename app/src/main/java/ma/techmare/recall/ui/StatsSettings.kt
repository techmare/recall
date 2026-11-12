package ma.techmare.recall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun StatsScreen(vm: AppViewModel) {
    val st by vm.stats.collectAsState()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Stats", style = display(30))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Retention", st.retention?.let { "$it%" } ?: "–", "Last 30 days", C.Green, Modifier.weight(1f))
            Tile("Streak", "${st.streak} ${if (st.streak == 1) "day" else "days"}", "Best ${st.bestStreak}", C.Amber, Modifier.weight(1f))
        }

        Panel(padding = 18.dp, radius = 20.dp) {
            SectionHeader("Reviews per day") { Text("avg ${st.weekDays.average().roundToInt()}", style = body(13, 500, C.Muted)) }
            Bars(st.weekDays, highlightLast = true)
            DayLabels(past = true)
        }

        Panel(padding = 18.dp, radius = 20.dp) {
            SectionHeader("Last 12 weeks") { Text("%,d reviews".format(st.total84), style = body(13, 500, C.Muted)) }
            val max = st.weeks12.maxOrNull() ?: 0
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                st.weeks12.chunked(12).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { n -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(intensity(n, max))) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text("Less", style = body(11, 500, C.Muted), modifier = Modifier.padding(end = 4.dp))
                listOf(C.Surface2, C.AccentDeep, C.AccentDim, C.Accent).forEach {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(it))
                }
                Text("More", style = body(11, 500, C.Muted), modifier = Modifier.padding(start = 4.dp))
            }
        }

        Panel(padding = 18.dp, radius = 20.dp) {
            SectionHeader("Coming up") { Text("${st.forecast.sum()} reviews this week", style = body(13, 500, C.Muted)) }
            Bars(st.forecast, highlightLast = false, color = C.AccentDim, height = 80)
            DayLabels(past = false)
        }
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = body(13, 600, C.Muted))
        Text(value, style = display(30, 700, color))
        Text(sub, style = body(12, 500, C.Muted))
    }
}

@Composable
private fun Bars(values: List<Int>, highlightLast: Boolean, color: Color = C.AccentDim, height: Int = 120) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (v > 0) Text(v.toString(), style = body(10, 600, C.Muted), textAlign = TextAlign.Center)
                val f = if (v == 0) 0.04f else (v.toFloat() / max).coerceIn(0.06f, 0.85f)
                Box(Modifier.fillMaxWidth().fillMaxHeight(f).clip(RoundedCornerShape(8.dp))
                    .background(if (v == 0) C.Surface2 else if (highlightLast && i == values.lastIndex) C.Accent else color))
            }
        }
    }
}

@Composable
private fun DayLabels(past: Boolean) {
    val today = LocalDate.now().minusDays(if (java.time.LocalTime.now().hour < 4) 1 else 0)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until 7).forEach { i ->
            val d = if (past) today.minusDays((6 - i).toLong()) else today.plusDays((i + 1).toLong())
            val isToday = past && i == 6
            Text(d.dayOfWeek.name.take(1), style = body(11, if (isToday) 700 else 500, if (isToday) C.Text else C.Muted),
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.repo.settings
    var newPerDay by remember { mutableFloatStateOf(s.newPerDay.toFloat()) }
    var retention by remember { mutableFloatStateOf(s.retention) }
    var autoplay by remember { mutableStateOf(s.autoplayAudio) }
    val sliderColors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent, inactiveTrackColor = C.Surface2)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = display(30))
        Panel(padding = 18.dp, radius = 20.dp) {
            SectionHeader("New cards per day") { Text(newPerDay.roundToInt().toString(), style = body(15, 700, C.AccentSoft)) }
            Slider(newPerDay, { newPerDay = it }, valueRange = 0f..100f, steps = 19, colors = sliderColors,
                onValueChangeFinished = { s.newPerDay = newPerDay.roundToInt(); vm.settingsChanged() })
            Text("How many never-seen cards each deck introduces per day.", style = body(13, 500, C.Muted))
        }
        Panel(padding = 18.dp, radius = 20.dp) {
            SectionHeader("Desired retention") { Text("${(retention * 100).roundToInt()}%", style = body(15, 700, C.AccentSoft)) }
            Slider(retention, { retention = (it * 100).roundToInt() / 100f }, valueRange = 0.80f..0.97f, colors = sliderColors,
                onValueChangeFinished = { s.retention = retention; vm.settingsChanged() })
            Text("Higher means you remember more but review more often. 90% is a good balance.", style = body(13, 500, C.Muted))
        }
        Panel(padding = 18.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Play audio automatically", style = body(15, 700))
                    Text("For imported cards that have sound", style = body(13, 500, C.Muted))
                }
                Switch(autoplay, { autoplay = it; s.autoplayAudio = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.Ground))
            }
        }
        Text("Recall uses the FSRS scheduler, the same algorithm as modern Anki. Days start at 4 a.m.",
            style = body(12, 500, C.Muted), modifier = Modifier.padding(horizontal = 4.dp))
    }
}
