package ma.techmare.recall.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, radius: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(C.Surface).padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = body(15, 700), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) C.Accent else C.Surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) C.Ground else C.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = body(16, 700, if (enabled) C.Ground else C.Muted))
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface2)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = C.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = body(14, 700))
    }
}

@Composable
fun CountChip(label: String, value: Int, color: Color, bg: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = body(12, 500, C.Muted))
        Text(value.toString(), style = body(17, 700, color))
    }
}

@Composable
fun Ring(fraction: Float, size: Dp = 84.dp, label: String) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(C.Track, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
            if (fraction > 0f) drawArc(C.Accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, style = body(16, 700))
    }
}

fun intensity(n: Int, max: Int): Color = when {
    n <= 0 -> C.Surface2
    max <= 0 -> C.AccentDeep
    n >= max * 0.66 -> C.Accent
    n >= max * 0.33 -> C.AccentDim
    else -> C.AccentDeep
}

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home),
    DECKS("decks", "Decks", Icons.Outlined.Layers),
    STATS("stats", "Stats", Icons.Outlined.BarChart),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

@Composable
fun BottomNav(current: String?, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Nav)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Surface2))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)) {
            Tab.entries.forEach { tab ->
                val active = current == tab.route
                Column(
                    Modifier.weight(1f).height(72.dp).clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, null, tint = if (active) C.AccentSoft else C.Muted, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(tab.label, style = body(12, if (active) 700 else 600, if (active) C.AccentSoft else C.Muted))
                }
            }
        }
    }
}

@Composable
fun DeckBadge(name: String, color: Int, size: Dp = 40.dp) {
    val initials = name.split(' ', '›').filter { it.isNotBlank() }.let { words ->
        if (words.size >= 2) "${words[0].first()}${words[1].first()}" else name.take(2)
    }.replaceFirstChar { it.uppercase() }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(C.deckColors[color.mod(4)]), contentAlignment = Alignment.Center) {
        Text(initials, style = body(14, 700, C.Ground))
    }
}

@Composable
fun DuePill(n: Int) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (n > 0) C.AccentChip else C.Surface2).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(if (n > 0) n.toString() else "✓", style = body(13, 700, if (n > 0) C.AccentPale else C.Muted))
    }
}
