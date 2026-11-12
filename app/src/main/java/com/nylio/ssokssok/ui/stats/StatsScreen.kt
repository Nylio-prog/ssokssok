package com.nylio.ssokssok.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.StatsSnapshot
import com.nylio.ssokssok.ui.components.SectionHeader
import com.nylio.ssokssok.ui.theme.Ssok
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsScreen(container: AppContainer) {
    val flow = remember { container.repository.stats() }
    val stats by flow.collectAsStateWithLifecycle(StatsSnapshot())

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp))
        StreakCard(stats)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberTile("${stats.reviewsToday}", "answers today", Modifier.weight(1f))
            NumberTile(
                stats.retention30?.let { "${(it * 100).roundToInt()}%" } ?: "–",
                "recall, 30 days",
                Modifier.weight(1f),
            )
        }

        SectionHeader("Activity, last 18 weeks")
        Panel { Heatmap(stats) }

        SectionHeader("Due in the next 7 days")
        Panel { Forecast(stats.forecast) }

        SectionHeader("Your cards")
        Panel { Breakdown(stats) }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}

@Composable
private fun StreakCard(stats: StatsSnapshot) {
    val c = Ssok.colors
    Surface(color = c.hardContainer, shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalFireDepartment, null, tint = c.streak, modifier = Modifier.size(44.dp))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    if (stats.streak == 1) "1 day streak" else "${stats.streak} day streak",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        stats.streak == 0 -> "Answer one card today to start a streak."
                        !stats.studiedToday -> "Study today to keep it going."
                        else -> "Best so far: ${stats.bestStreak} " + if (stats.bestStreak == 1) "day" else "days"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** GitHub-style grid, one column per week, Monday on top. Tap a cell to read it. */
@Composable
private fun Heatmap(stats: StatsSnapshot) {
    val scale = Ssok.colors.heatScale
    val heat = stats.heat
    if (heat.isEmpty()) return
    val end = stats.heatEnd
    // Align the grid so the last column ends on today's weekday.
    val todayRow = end.dayOfWeek.value - 1 // Monday = 0
    val totalCells = heat.size
    val weeks = (totalCells + (6 - todayRow) + 6) / 7
    val max = heat.maxOrNull()?.coerceAtLeast(1) ?: 1
    var selected by remember { mutableStateOf<Int?>(null) }

    fun level(n: Int): Color = when {
        n == 0 -> scale[0]
        n <= max * 0.25 -> scale[1]
        n <= max * 0.5 -> scale[2]
        n <= max * 0.75 -> scale[3]
        else -> scale[4]
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(weeks / 7f)
                .pointerInput(heat) {
                    detectTapGestures { pos ->
                        val cell = size.width / weeks.toFloat()
                        val col = (pos.x / cell).toInt()
                        val row = (pos.y / cell).toInt()
                        val idx = col * 7 + row - (weeks * 7 - totalCells - (6 - todayRow))
                        selected = idx.takeIf { it in heat.indices }
                    }
                },
        ) {
            val cell = size.width / weeks
            val gap = 2.dp.toPx()
            val offset = weeks * 7 - totalCells - (6 - todayRow)
            for (i in heat.indices) {
                val slot = i + offset
                val col = slot / 7
                val row = slot % 7
                val topLeft = Offset(col * cell + gap / 2, row * cell + gap / 2)
                drawRoundRect(
                    level(heat[i]),
                    topLeft,
                    Size(cell - gap, cell - gap),
                    CornerRadius(3.dp.toPx()),
                )
                if (selected == i) {
                    drawRoundRect(
                        color = scale[4],
                        topLeft = topLeft,
                        size = Size(cell - gap, cell - gap),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sel = selected
            Text(
                if (sel == null) {
                    heat.count { it > 0 }.let { if (it == 1) "1 active day" else "$it active days" } + " · " +
                        heat.sum().let { if (it == 1) "1 answer" else "$it answers" }
                } else {
                    val date = end.minusDays((heat.size - 1 - sel).toLong())
                    val n = heat[sel]
                    "${date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}: " +
                        if (n == 1) "1 answer" else "$n answers"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            scale.forEach { col ->
                Box(Modifier.padding(horizontal = 1.5.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(col))
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Forecast(values: List<Int>) {
    if (values.isEmpty()) return
    val max = values.max().coerceAtLeast(1)
    val today = java.time.LocalDate.now()
    val barColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    "$v",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (v > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.height(4.dp))
                val frac = v.toFloat() / max
                Canvas(Modifier.fillMaxWidth().weight(1f, fill = true)) {
                    val h = size.height * frac
                    val r = 4.dp.toPx()
                    if (v == 0) {
                        drawRoundRect(track, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()), CornerRadius(r))
                    } else {
                        drawRoundRect(
                            if (i == 0) barColor else barColor.copy(alpha = 0.55f),
                            Offset(0f, size.height - h),
                            Size(size.width, h.coerceAtLeast(r * 2)),
                            CornerRadius(r),
                        )
                    }
                }
                Box(Modifier.height(6.dp))
                Text(
                    if (i == 0) "Today" else today.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Cards by stage of memory, from new to mastered: one hue getting deeper. */
@Composable
private fun Breakdown(stats: StatsSnapshot) {
    val scale = Ssok.colors.heatScale
    val parts = listOf(
        Triple("New", stats.newCards, scale[1]),
        Triple("Learning", stats.learning, scale[2]),
        Triple("Growing", stats.young, scale[3]),
        Triple("Mastered", stats.mature, scale[4]),
    )
    val total = parts.sumOf { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp))) {
            if (total == 0) {
                Box(Modifier.fillMaxSize().background(scale[0]))
            } else {
                parts.filter { it.second > 0 }.forEachIndexed { i, (_, n, color) ->
                    if (i > 0) Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLowest))
                    Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(color))
                }
            }
        }
        parts.forEach { (label, n, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text("$n", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (total == 0) "" else "  ${(n * 100f / total).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
        if (stats.tough > 0) {
            Text(
                "${stats.tough} tough cards. Drill them from a deck's study sheet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Mastered: next review 3+ weeks away.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
