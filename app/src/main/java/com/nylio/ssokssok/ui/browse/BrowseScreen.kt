package com.nylio.ssokssok.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.db.CardType
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.korean.Hangul
import com.nylio.ssokssok.ui.Routes
import com.nylio.ssokssok.ui.appViewModel
import com.nylio.ssokssok.ui.components.EmptyState
import com.nylio.ssokssok.ui.deck.CardLine
import com.nylio.ssokssok.ui.deck.CardRow
import com.nylio.ssokssok.ui.deck.CardStatus
import com.nylio.ssokssok.ui.deck.statusOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BrowseData(val lines: List<CardLine> = emptyList(), val decks: List<DeckEntity> = emptyList())

class BrowseViewModel(container: AppContainer) : ViewModel() {
    val data: StateFlow<BrowseData> = combine(
        container.repository.observeAllCards(),
        container.repository.observeAllProgress(),
        container.repository.observeDecks(),
    ) { cards, progress, decks ->
        val byCard = progress.groupBy { it.cardId }
        BrowseData(cards.map { CardLine(it, statusOf(byCard[it.id].orEmpty())) }, decks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseData())
}

private enum class Filter(val label: String) { ALL("All"), WORDS("Words"), SENTENCES("Sentences"), GRAMMAR("Grammar"), TOUGH("Tough"), NEW("New") }

@Composable
fun BrowseScreen(container: AppContainer, nav: NavController) {
    val vm = appViewModel(container) { BrowseViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    val deckNames = remember(data.decks) { data.decks.associate { it.id to it.name } }
    val shown = remember(data.lines, query, filter) {
        val q = query.trim().lowercase()
        // Searching "ㅅㄱ" finds 사과: match on initial consonants too.
        val qJamo = Hangul.decompose(q)
        data.lines.filter { line ->
            val c = line.card
            val typeOk = when (filter) {
                Filter.ALL -> true
                Filter.WORDS -> c.cardType == CardType.WORD
                Filter.SENTENCES -> c.cardType == CardType.SENTENCE
                Filter.GRAMMAR -> c.cardType == CardType.GRAMMAR
                Filter.TOUGH -> line.status == CardStatus.TOUGH
                Filter.NEW -> line.status == CardStatus.NEW
            }
            typeOk && (
                q.isEmpty() ||
                    Cloze.plain(c.korean).lowercase().contains(q) ||
                    c.english.lowercase().contains(q) ||
                    c.romanization.lowercase().contains(q) ||
                    c.tags.lowercase().contains(q) ||
                    c.notes.lowercase().contains(q) ||
                    (qJamo.length >= 2 && Hangul.decompose(Cloze.plain(c.korean)).contains(qJamo))
                )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (data.decks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { nav.navigate(Routes.editor()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Outlined.Add, "Add card") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cards", style = MaterialTheme.typography.headlineLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, "Clear") }
                    },
                    placeholder = { Text("Search Korean, English, tags…") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Filter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            if (data.lines.isNotEmpty() && shown.isEmpty()) {
                EmptyState("?", "Nothing matches", "Try another word, or clear the filter.")
            } else if (data.lines.isEmpty()) {
                EmptyState("빈", "No cards yet", "Cards you add or import show up here.")
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = padding.calculateBottomPadding() + 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if (shown.isNotEmpty()) {
                        Text(
                            if (shown.size == 1) "1 card" else "${shown.size} cards",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(shown, key = { it.card.id }) { line ->
                    CardRow(line, onClick = { nav.navigate(Routes.editor(cardId = line.card.id)) }, deckLabel = deckNames[line.card.deckId])
                }
            }
        }
    }
}
