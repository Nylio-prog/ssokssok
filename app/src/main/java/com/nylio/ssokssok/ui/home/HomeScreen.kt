package com.nylio.ssokssok.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.StatsSnapshot
import com.nylio.ssokssok.data.db.DeckSummary
import com.nylio.ssokssok.ui.Routes
import com.nylio.ssokssok.ui.appViewModel
import com.nylio.ssokssok.ui.components.DeckTile
import com.nylio.ssokssok.ui.components.EmptyState
import com.nylio.ssokssok.ui.components.Pill
import com.nylio.ssokssok.ui.components.SectionHeader
import com.nylio.ssokssok.ui.components.SlimProgress
import com.nylio.ssokssok.ui.review.StudySetupSheet
import com.nylio.ssokssok.ui.theme.KoreanDisplay
import com.nylio.ssokssok.ui.theme.Ssok
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class HomeState(
    val loading: Boolean = true,
    val decks: List<DeckSummary> = emptyList(),
    val stats: StatsSnapshot = StatsSnapshot(),
    val newPerDay: Int = 15,
) {
    fun newToday(d: DeckSummary) = minOf(d.newCount, (newPerDay - d.introducedToday).coerceAtLeast(0))
    val totalDue get() = decks.sumOf { it.due }
    val totalNew get() = decks.sumOf { newToday(it) }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        container.repository.deckSummaries(),
        container.repository.stats(),
        container.settings.flow,
    ) { decks, stats, settings ->
        HomeState(false, decks, stats, settings.newPerDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun createDeck(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(container.repository.createDeck(name)) }
    }
}

private data class SheetTarget(val title: String, val deckIds: List<Long>?)

@Composable
fun HomeScreen(container: AppContainer, nav: NavController) {
    val vm = appViewModel(container) { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    var newDeckDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.decks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.editor()) },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Add card") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(state.stats, onSettings = { nav.navigate(Routes.SETTINGS) }) }
            if (!state.loading && state.decks.isEmpty()) {
                item {
                    EmptyState(
                        glyph = "쏙",
                        title = "Start your first deck",
                        body = "Make a deck and add cards by hand, or import a whole list from a file or a chat with an AI.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { newDeckDialog = true }) { Text("New deck") }
                            OutlinedButton(onClick = { nav.navigate(Routes.import()) }) { Text("Import") }
                        }
                    }
                }
            } else if (state.decks.isNotEmpty()) {
                item {
                    TodayCard(state, onStudy = { sheet = SheetTarget("Study everything", null) })
                }
                item {
                    SectionHeader("Your decks") {
                        TextButton(onClick = { nav.navigate(Routes.import()) }) {
                            Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                            Text("Import", Modifier.padding(start = 6.dp))
                        }
                        TextButton(onClick = { newDeckDialog = true }) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Text("New deck", Modifier.padding(start = 6.dp))
                        }
                    }
                }
                items(state.decks, key = { it.deck.id }) { d ->
                    DeckRow(
                        d,
                        newToday = state.newToday(d),
                        onOpen = { nav.navigate(Routes.deck(d.deck.id)) },
                        onStudy = { sheet = SheetTarget(d.deck.name, listOf(d.deck.id)) },
                    )
                }
            }
        }
    }

    sheet?.let { target ->
        StudySetupSheet(
            container = container,
            title = target.title,
            deckIds = target.deckIds,
            onDismiss = { sheet = null },
            onStart = { cfg ->
                sheet = null
                nav.navigate(Routes.review(cfg))
            },
        )
    }

    if (newDeckDialog) {
        NewDeckDialog(
            onDismiss = { newDeckDialog = false },
            onCreate = { name ->
                newDeckDialog = false
                vm.createDeck(name) { id -> nav.navigate(Routes.deck(id)) }
            },
        )
    }
}

@Composable
private fun Header(stats: StatsSnapshot, onSettings: () -> Unit) {
    val hour = remember { LocalTime.now().hour }
    val (ko, en) = when (hour) {
        in 5..10 -> "좋은 아침이에요" to "Good morning"
        in 11..17 -> "안녕하세요" to "Hello"
        in 18..22 -> "좋은 저녁이에요" to "Good evening"
        else -> "늦게까지 공부하네요" to "Studying late"
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ko, fontFamily = KoreanDisplay, fontSize = 28.sp, lineHeight = 34.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(en, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val streakColor = if (stats.studiedToday) Ssok.colors.streak else MaterialTheme.colorScheme.onSurfaceVariant
        Pill(
            text = if (stats.streak == 1) "1 day" else "${stats.streak} days",
            icon = Icons.Outlined.LocalFireDepartment,
            container = if (stats.studiedToday) Ssok.colors.hardContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            content = streakColor,
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodayCard(state: HomeState, onStudy: () -> Unit) {
    val due = state.totalDue
    val fresh = state.totalNew
    val waiting = due + fresh
    val done = state.stats.reviewsToday
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            if (waiting > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$waiting", style = MaterialTheme.typography.displayMedium)
                    Text(
                        if (waiting == 1) " card waiting" else " cards waiting",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                    )
                }
                val minutes = ((due * 8 + fresh * 20) / 60).coerceAtLeast(1)
                Text(
                    "$due to review · $fresh new · about $minutes min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            } else {
                Text("All caught up", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (done > 0) "$done reviews done today. Come back tomorrow, or practise a bit more."
                    else "Nothing is due. Practise a deck or add new words.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
            if (done > 0 && waiting > 0) {
                SlimProgress(
                    progress = done.toFloat() / (done + waiting),
                    color = MaterialTheme.colorScheme.onPrimary,
                    track = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                )
            }
            Button(
                onClick = onStudy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (waiting > 0) "Start studying" else "Practise", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun DeckRow(d: DeckSummary, newToday: Int, onOpen: () -> Unit, onStudy: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckTile(d.deck, size = 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(d.deck.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CountLabel(d.due, "due", MaterialTheme.colorScheme.primary)
                    CountLabel(newToday, "new", MaterialTheme.colorScheme.secondary)
                    Text(
                        "${d.total} cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SlimProgress(
                    progress = if (d.total == 0) 0f else d.learned.toFloat() / d.total,
                    height = 4.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            FilledTonalIconButton(
                onClick = onStudy,
                enabled = d.total > 0,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Outlined.PlayArrow, "Study ${d.deck.name}")
            }
        }
    }
}

@Composable
private fun CountLabel(n: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(7.dp).clip(CircleShape)
                .background(if (n > 0) color else MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            "$n $label",
            style = MaterialTheme.typography.bodySmall,
            color = if (n > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NewDeckDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New deck") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. TTMIK Level 1, Food, Verbs") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
