package com.nylio.ssokssok.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.data.db.CardType
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.korean.Hanja
import com.nylio.ssokssok.ui.review.HanjaRow
import com.nylio.ssokssok.data.db.DeckPath
import com.nylio.ssokssok.ui.home.DeckTree
import com.nylio.ssokssok.ui.appViewModel
import com.nylio.ssokssok.ui.components.DeckTile
import com.nylio.ssokssok.ui.home.NewDeckDialog
import com.nylio.ssokssok.ui.theme.KoreanDisplay
import com.nylio.ssokssok.ui.theme.Ssok
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditorViewModel(
    private val container: AppContainer,
    private val cardId: Long?,
    initialDeckId: Long?,
) : ViewModel() {
    private val repo = container.repository

    val decks: StateFlow<List<DeckEntity>> =
        repo.observeDecks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var original: CardEntity? by mutableStateOf(null)
        private set
    var deckId: Long? by mutableStateOf(initialDeckId)
    var type by mutableStateOf(CardType.WORD)
    var korean by mutableStateOf(TextFieldValue(""))
    var english by mutableStateOf("")
    var hanja by mutableStateOf("")
    var example by mutableStateOf("")
    var exampleTranslation by mutableStateOf("")
    var notes by mutableStateOf("")
    var tags by mutableStateOf("")
    var showDetails by mutableStateOf(false)
    var addedThisSession by mutableIntStateOf(0)
        private set

    private val selectedDeck = MutableStateFlow(initialDeckId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val deckCards: StateFlow<List<CardEntity>> = selectedDeck.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeCards(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (cardId != null) {
            viewModelScope.launch {
                repo.card(cardId)?.let { c ->
                    original = c
                    deckId = c.deckId
                    selectedDeck.value = c.deckId
                    type = c.cardType
                    korean = TextFieldValue(c.korean, TextRange(c.korean.length))
                    english = c.english
                    hanja = c.hanja
                    example = c.example
                    exampleTranslation = c.exampleTranslation
                    notes = c.notes
                    tags = c.tags
                    showDetails = listOf(c.hanja, c.example, c.notes, c.tags).any { it.isNotBlank() }
                }
            }
        } else if (initialDeckId == null) {
            viewModelScope.launch {
                // Default to the most recent deck for quick capture.
                decks.collect { list ->
                    if (deckId == null && list.isNotEmpty()) selectDeck(list.last().id)
                }
            }
        }
    }

    fun selectDeck(id: Long) {
        deckId = id
        selectedDeck.value = id
    }

    val isEdit get() = cardId != null
    val canSave get() = deckId != null && korean.text.isNotBlank() && english.isNotBlank()

    /** Wraps the selected part of the Korean text in {{ }} so it becomes a blank to fill. */
    fun makeBlank() {
        val v = korean
        val sel = v.selection
        if (sel.collapsed) return
        val start = minOf(sel.start, sel.end)
        val end = maxOf(sel.start, sel.end)
        val text = v.text.substring(0, start) + "{{" + v.text.substring(start, end) + "}}" + v.text.substring(end)
        korean = TextFieldValue(text, TextRange(end + 4))
    }

    fun clearBlanks() {
        val plain = Cloze.plain(korean.text)
        korean = TextFieldValue(plain, TextRange(plain.length))
    }

    fun save(onSaved: (CardEntity) -> Unit) {
        val deck = deckId ?: return
        if (!canSave) return
        val base = original ?: CardEntity(deckId = deck, korean = "", english = "")
        val card = base.copy(
            deckId = deck,
            korean = korean.text.trim(),
            english = english.trim(),
            hanja = hanja.trim(),
            example = example.trim(),
            exampleTranslation = exampleTranslation.trim(),
            notes = notes.trim(),
            tags = tags.trim().split(Regex("[,\\s]+")).filter { it.isNotBlank() }.joinToString(" "),
            type = type.key,
        )
        viewModelScope.launch {
            repo.saveCard(card)
            if (!isEdit) addedThisSession++
            onSaved(card)
        }
    }

    /** Clears the fields for the next card but keeps deck and type. */
    fun clearForNext() {
        korean = TextFieldValue("")
        english = ""
        hanja = ""
        example = ""
        exampleTranslation = ""
        notes = ""
    }

    fun delete(onDone: () -> Unit) {
        val id = cardId ?: return
        viewModelScope.launch { repo.deleteCard(id); onDone() }
    }

    fun resetProgress(onDone: () -> Unit) {
        val id = cardId ?: return
        viewModelScope.launch { repo.resetCard(id); onDone() }
    }

    fun createDeck(name: String) {
        viewModelScope.launch { selectDeck(repo.createDeck(name)) }
    }
}

@Composable
fun CardEditorScreen(container: AppContainer, nav: NavController, cardId: Long?, deckId: Long?) {
    val vm = appViewModel(container, key = "editor-$cardId-$deckId") { EditorViewModel(it, cardId, deckId) }
    val decks by vm.decks.collectAsStateWithLifecycle()
    val deckCards by vm.deckCards.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val koreanFocus = remember { FocusRequester() }
    var menu by remember { mutableStateOf(false) }
    var newDeck by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!vm.isEdit) runCatching { koreanFocus.requestFocus() }
    }

    val duplicate = remember(deckCards, vm.korean.text, vm.original) {
        val k = vm.korean.text.trim()
        if (k.isEmpty()) null else deckCards.firstOrNull { it.korean == k && it.id != vm.original?.id }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (vm.isEdit) "Edit card" else "New card", style = MaterialTheme.typography.titleLarge)
                        if (vm.addedThisSession > 0) {
                            Text(
                                "${vm.addedThisSession} added",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.Close, "Close") } },
                actions = {
                    if (vm.isEdit) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Reset progress") },
                                leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                                onClick = {
                                    menu = false
                                    vm.resetProgress { scope.launch { snackbar.showSnackbar("Progress reset. The card is new again.") } }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete card", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!vm.isEdit) {
                        OutlinedButton(
                            onClick = {
                                vm.save { card ->
                                    vm.clearForNext()
                                    runCatching { koreanFocus.requestFocus() }
                                    val deckName = decks.firstOrNull { it.id == card.deckId }?.path?.leaf ?: "deck"
                                    scope.launch { snackbar.showSnackbar("Saved “${Cloze.plain(card.korean)}” to $deckName") }
                                }
                            },
                            enabled = vm.canSave,
                            modifier = Modifier.weight(1f).height(54.dp),
                        ) { Text("Save & next") }
                    }
                    Button(
                        onClick = { vm.save { nav.popBackStack() } },
                        enabled = vm.canSave,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeckPicker(decks, vm.deckId, onSelect = vm::selectDeck, onNew = { newDeck = true })

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CardType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = vm.type == t,
                        onClick = { vm.type = t },
                        shape = SegmentedButtonDefaults.itemShape(i, CardType.entries.size),
                    ) { Text(t.label) }
                }
            }

            OutlinedTextField(
                value = vm.korean,
                onValueChange = { vm.korean = it },
                label = { Text("Korean") },
                placeholder = {
                    // Same font and size as the typed text, so the hint doesn't jump when typing starts.
                    Text(
                        when (vm.type) {
                            CardType.WORD -> "사과"
                            CardType.SENTENCE -> "사과를 먹어요."
                            CardType.GRAMMAR -> "-고 싶다"
                        },
                        style = koreanFieldStyle,
                    )
                },
                supportingText = if (vm.type == CardType.SENTENCE) {
                    { Text("Select a part, then tap “Make blank”") }
                } else {
                    null
                },
                textStyle = koreanFieldStyle,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(koreanFocus),
            )

            val hasSelection = !vm.korean.selection.collapsed
            val isCloze = Cloze.isCloze(vm.korean.text)
            if (vm.type != CardType.WORD || hasSelection || isCloze) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = vm::makeBlank,
                        enabled = hasSelection,
                        label = { Text("Make blank") },
                        leadingIcon = { Text("{ }", style = MaterialTheme.typography.labelMedium) },
                    )
                    if (isCloze) {
                        AssistChip(onClick = vm::clearBlanks, label = { Text("Remove blanks") })
                    }
                }
            }
            AnimatedVisibility(isCloze) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val masked = Cloze.parts(vm.korean.text).joinToString("") { if (it.isBlank) "＿＿" else it.text }
                        Text(masked, fontFamily = KoreanDisplay, fontSize = 20.sp)
                        Text(
                            "Answer: ${Cloze.answers(vm.korean.text).joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (duplicate != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = Ssok.colors.medium, modifier = Modifier.size(18.dp))
                    Text(
                        "Already in this deck as “${duplicate.english}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ssok.colors.medium,
                    )
                }
            }

            OutlinedTextField(
                value = vm.english,
                onValueChange = { vm.english = it },
                label = { Text("English") },
                placeholder = { Text(if (vm.type == CardType.WORD) "apple" else "I eat an apple.", style = MaterialTheme.typography.titleMedium) },
                supportingText = { Text("Separate other accepted answers with ;") },
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = { vm.showDetails = !vm.showDetails }) {
                Icon(if (vm.showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                Text(
                    if (vm.showDetails) "Fewer details" else "한자, example, notes, tags",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            AnimatedVisibility(vm.showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        vm.hanja, { vm.hanja = it },
                        label = { Text("한자") },
                        placeholder = { Text("新=새 신; 入=들 입; 生=날 생") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val hanjaParts = remember(vm.hanja) { Hanja.parse(vm.hanja) }
                    if (hanjaParts.isNotEmpty()) HanjaRow(hanjaParts, onClick = null)
                    OutlinedTextField(
                        vm.example, { vm.example = it },
                        label = { Text("Example sentence") },
                        placeholder = { Text("사과를 먹어요.", style = exampleFieldStyle) },
                        textStyle = exampleFieldStyle,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        vm.exampleTranslation, { vm.exampleTranslation = it },
                        label = { Text("Example translation") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        vm.notes, { vm.notes = it },
                        label = { Text("Notes") },
                        placeholder = { Text("Irregular verb, nuance, a mnemonic…") },
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        vm.tags, { vm.tags = it },
                        label = { Text("Tags") },
                        placeholder = { Text("food verbs topik1") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (newDeck) {
        NewDeckDialog(
            folders = decks.map { it.path.folder }.filter { it.isNotEmpty() }.distinct().sorted(),
            initialFolder = decks.firstOrNull { it.id == vm.deckId }?.path?.folder.orEmpty(),
            onDismiss = { newDeck = false },
            onCreate = { newDeck = false; vm.createDeck(it) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("Its review history goes with it.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete { nav.popBackStack() } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val koreanFieldStyle = TextStyle(fontFamily = KoreanDisplay, fontSize = 24.sp, lineHeight = 32.sp)
private val exampleFieldStyle = TextStyle(fontFamily = KoreanDisplay, fontSize = 19.sp, lineHeight = 26.sp)

@Composable
private fun DeckPicker(decks: List<DeckEntity>, selected: Long?, onSelect: (Long) -> Unit, onNew: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = decks.firstOrNull { it.id == selected }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current?.let { DeckPath.display(it.name) } ?: "Choose a deck",
            onValueChange = {},
            readOnly = true,
            label = { Text("Deck") },
            leadingIcon = current?.let { { DeckTile(it, size = 28.dp) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            decks.sortedWith { a, b -> DeckTree.naturalCompare(a.name, b.name) }.forEach { d ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(d.path.leaf)
                            if (d.path.folder.isNotEmpty()) {
                                Text(
                                    DeckPath.display(d.path.folder),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = { DeckTile(d, size = 28.dp) },
                    onClick = { onSelect(d.id); open = false },
                )
            }
            if (decks.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New deck…") },
                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                onClick = { open = false; onNew() },
            )
        }
    }
}
