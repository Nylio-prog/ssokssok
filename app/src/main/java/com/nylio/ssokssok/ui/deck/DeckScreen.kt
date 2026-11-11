package com.nylio.ssokssok.ui.deck

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.data.db.CardState
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.data.db.ProgressEntity
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.ui.Routes
import com.nylio.ssokssok.ui.appViewModel
import com.nylio.ssokssok.ui.components.DeckTile
import com.nylio.ssokssok.ui.components.EmptyState
import com.nylio.ssokssok.ui.components.Metric
import com.nylio.ssokssok.ui.components.Pill
import com.nylio.ssokssok.ui.review.StudySetupSheet
import com.nylio.ssokssok.ui.theme.KoreanDisplay
import com.nylio.ssokssok.ui.theme.Ssok
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CardLine(val card: CardEntity, val status: CardStatus)

enum class CardStatus { NEW, LEARNING, YOUNG, MATURE, TOUGH }

fun statusOf(rows: List<ProgressEntity>): CardStatus {
    val studied = rows.filter { it.state != CardState.NEW }
    return when {
        studied.isEmpty() -> CardStatus.NEW
        studied.any { it.lapses >= 3 || it.difficulty >= 8.5 } -> CardStatus.TOUGH
        studied.any { it.state == CardState.LEARNING || it.state == CardState.RELEARNING } -> CardStatus.LEARNING
        studied.all { it.scheduledDays >= 21 } -> CardStatus.MATURE
        else -> CardStatus.YOUNG
    }
}

data class DeckUi(
    val deck: DeckEntity? = null,
    val lines: List<CardLine> = emptyList(),
    val loaded: Boolean = false,
)

class DeckViewModel(private val container: AppContainer, private val deckId: Long) : ViewModel() {
    private val repo = container.repository

    val ui: StateFlow<DeckUi> = combine(
        repo.observeDeck(deckId),
        repo.observeCards(deckId),
        repo.observeProgress(deckId),
    ) { deck, cards, progress ->
        val byCard = progress.groupBy { it.cardId }
        DeckUi(deck, cards.map { CardLine(it, statusOf(byCard[it.id].orEmpty())) }, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckUi())

    fun rename(name: String, description: String, symbol: String) {
        val d = ui.value.deck ?: return
        viewModelScope.launch {
            repo.updateDeck(d.copy(name = name.trim(), description = description.trim(), symbol = symbol.trim().take(2).ifEmpty { d.symbol }))
        }
    }

    fun recolor(color: Int) {
        val d = ui.value.deck ?: return
        viewModelScope.launch { repo.updateDeck(d.copy(color = color)) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repo.deleteDeck(deckId); onDone() }
    }

    suspend fun exportJson(): String = repo.exportDeck(deckId)
}

@Composable
fun DeckScreen(container: AppContainer, nav: NavController, deckId: Long) {
    val vm = appViewModel(container, key = "deck-$deckId") { DeckViewModel(it, deckId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var studying by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = vm.exportJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }.onSuccess { Toast.makeText(context, "Deck exported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    val deck = ui.deck
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.editor(deckId = deckId)) }) { Icon(Icons.Outlined.Add, "Add card") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit deck") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; editing = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Import cards into this deck") },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                            onClick = { menu = false; nav.navigate(Routes.import(deckId)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export as file") },
                            leadingIcon = { Icon(Icons.Outlined.IosShare, null) },
                            onClick = {
                                menu = false
                                val safe = (deck?.name ?: "deck").replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "deck" }
                                exporter.launch("$safe.json")
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete deck", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (deck == null) return@Scaffold
        val filtered = remember(ui.lines, query) {
            if (query.isBlank()) ui.lines else ui.lines.filter { line ->
                val q = query.trim().lowercase()
                line.card.korean.lowercase().contains(q) || line.card.english.lowercase().contains(q) ||
                    line.card.romanization.lowercase().contains(q) || line.card.tags.lowercase().contains(q)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { DeckHeader(deck, ui.lines, onStudy = { studying = true }, onAdd = { nav.navigate(Routes.editor(deckId = deckId)) }) }
            if (ui.loaded && ui.lines.isEmpty()) {
                item {
                    EmptyState(
                        glyph = deck.symbol.ifBlank { "가" },
                        title = "No cards yet",
                        body = "Add cards one by one, or import a list you already have.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { nav.navigate(Routes.editor(deckId = deckId)) }) { Text("Add a card") }
                            TextButton(onClick = { nav.navigate(Routes.import(deckId)) }) { Text("Import") }
                        }
                    }
                }
            } else if (ui.lines.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        placeholder = { Text("Search ${ui.lines.size} cards") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(filtered, key = { it.card.id }) { line ->
                    CardRow(line, onClick = { nav.navigate(Routes.editor(cardId = line.card.id)) })
                }
            }
        }
    }

    if (studying && deck != null) {
        StudySetupSheet(
            container = container,
            title = deck.name,
            deckIds = listOf(deck.id),
            onDismiss = { studying = false },
            onStart = { cfg -> studying = false; nav.navigate(Routes.review(cfg)) },
        )
    }

    if (editing && deck != null) {
        EditDeckDialog(
            deck = deck,
            onDismiss = { editing = false },
            onSave = { name, desc, symbol, color ->
                editing = false
                vm.rename(name, desc, symbol)
                if (color != deck.color) vm.recolor(color)
            },
        )
    }

    if (confirmDelete && deck != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${deck.name}”?") },
            text = { Text("This removes the deck and its ${ui.lines.size} cards, with their progress. Export it first if you may want it back.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete { nav.popBackStack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeckHeader(deck: DeckEntity, lines: List<CardLine>, onStudy: () -> Unit, onAdd: () -> Unit) {
    val total = lines.size
    val learned = lines.count { it.status != CardStatus.NEW }
    val mature = lines.count { it.status == CardStatus.MATURE }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeckTile(deck, size = 72.dp)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (deck.description.isNotBlank()) {
                    Text(deck.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("$total", "cards")
            Metric("$learned", "studied")
            Metric("$mature", "mastered", valueColor = Ssok.colors.easy)
            Metric(if (total == 0) "–" else "${(learned * 100f / total).roundToInt()}%", "seen")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onStudy, enabled = total > 0, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.PlayArrow, null)
                Text("Study", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalButton(onClick = onAdd, modifier = Modifier.height(52.dp)) {
                Icon(Icons.Outlined.Add, null)
                Text("Card", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
fun CardRow(line: CardLine, onClick: () -> Unit, deckLabel: String? = null) {
    val c = line.card
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    Cloze.plain(c.korean),
                    fontFamily = KoreanDisplay,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    c.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (deckLabel != null) {
                    Text(deckLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            StatusPill(line.status)
        }
    }
}

@Composable
fun StatusPill(status: CardStatus) {
    val c = Ssok.colors
    when (status) {
        CardStatus.NEW -> Pill("New", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
        CardStatus.LEARNING -> Pill("Learning", container = c.mediumContainer, content = c.medium)
        CardStatus.YOUNG -> Pill("Growing", container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        CardStatus.MATURE -> Pill("Mastered", container = c.easyContainer, content = c.easy)
        CardStatus.TOUGH -> Pill("Tough", container = c.hardContainer, content = c.hard)
    }
}

@Composable
private fun EditDeckDialog(deck: DeckEntity, onDismiss: () -> Unit, onSave: (String, String, String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(deck.name) }
    var desc by rememberSaveable { mutableStateOf(deck.description) }
    var symbol by rememberSaveable { mutableStateOf(deck.symbol) }
    var color by rememberSaveable { mutableStateOf(deck.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(
                    symbol, { symbol = it.take(2) },
                    label = { Text("Symbol (1–2 characters)") },
                    placeholder = { Text("e.g. 밥") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Text("Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(8) { i ->
                        val selected = i == color
                        DeckTile(
                            symbol = if (selected) symbol.ifBlank { "가" } else " ",
                            colorIndex = i,
                            size = if (selected) 32.dp else 26.dp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { color = i },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, desc, symbol, color) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
