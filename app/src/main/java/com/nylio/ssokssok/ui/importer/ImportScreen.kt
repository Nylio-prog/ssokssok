package com.nylio.ssokssok.ui.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.ImportSummary
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.importer.DeckFormat
import com.nylio.ssokssok.importer.DeckFormatException
import com.nylio.ssokssok.importer.ParseResult
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.ui.Routes
import com.nylio.ssokssok.ui.appViewModel
import com.nylio.ssokssok.ui.components.DeckTile
import com.nylio.ssokssok.ui.components.SectionHeader
import com.nylio.ssokssok.ui.theme.KoreanDisplay
import com.nylio.ssokssok.ui.theme.Ssok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportViewModel(private val container: AppContainer, targetDeckId: Long?) : ViewModel() {
    val decks: StateFlow<List<DeckEntity>> =
        container.repository.observeDecks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var pasted by mutableStateOf("")
    var fileName by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<ParseResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var target by mutableStateOf(targetDeckId)
    var importing by mutableStateOf(false)
        private set
    var summary by mutableStateOf<ImportSummary?>(null)
        private set

    init {
        container.incomingImport.value?.let { incoming ->
            container.incomingImport.value = null
            pasted = if (incoming.fileName == null) incoming.text else ""
            parse(incoming.text, incoming.fileName)
        }
    }

    fun parse(text: String, name: String? = null) {
        fileName = name
        error = null
        result = null
        summary = null
        try {
            result = DeckFormat.parse(text, name)
        } catch (e: DeckFormatException) {
            error = e.message
        } catch (e: Exception) {
            error = "Could not read this: ${e.message}"
        }
    }

    fun loadUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val (text, name) = withContext(Dispatchers.IO) {
                runCatching {
                    val t = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                    var n: String? = null
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) n = c.getString(0)
                    }
                    t to n
                }.getOrElse { "" to null }
            }
            if (text.isBlank()) error = "That file is empty or could not be opened." else parse(text, name)
        }
    }

    fun reset() {
        result = null
        error = null
        summary = null
        fileName = null
    }

    fun import() {
        val r = result ?: return
        importing = true
        viewModelScope.launch {
            summary = container.repository.import(r, target)
            importing = false
        }
    }
}

@Composable
fun ImportScreen(container: AppContainer, nav: NavController, targetDeckId: Long?) {
    val vm = appViewModel(container, key = "import-$targetDeckId") { ImportViewModel(it, targetDeckId) }
    val decks by vm.decks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadUri(context, uri)
    }

    fun copy(text: String, what: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar("$what copied") }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Import cards") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val summary = vm.summary
            val result = vm.result
            when {
                summary != null -> Done(summary, onOpen = {
                    val id = summary.deckIds.firstOrNull()
                    nav.popBackStack()
                    if (id != null) nav.navigate(Routes.deck(id))
                }, onMore = vm::reset)
                result != null -> Preview(
                    result = result,
                    fileName = vm.fileName,
                    decks = decks,
                    target = vm.target,
                    onTarget = { vm.target = it },
                    importing = vm.importing,
                    onImport = vm::import,
                    onCancel = vm::reset,
                )
                else -> {
                    Sources(
                        pasted = vm.pasted,
                        onPasted = { vm.pasted = it },
                        onPick = { picker.launch(arrayOf("*/*")) },
                        onPasteClipboard = { clipboard.getText()?.text?.let { vm.pasted = it } },
                        onRead = { vm.parse(vm.pasted, null) },
                        error = vm.error,
                    )
                    FormatGuide(onCopy = ::copy)
                }
            }
        }
    }
}

@Composable
private fun Sources(
    pasted: String,
    onPasted: (String) -> Unit,
    onPick: () -> Unit,
    onPasteClipboard: () -> Unit,
    onRead: () -> Unit,
    error: String?,
) {
    Surface(
        onClick = onPick,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(32.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text("Choose a file", style = MaterialTheme.typography.titleMedium)
                Text(".json, .tsv, .csv or .txt, Anki plain-text exports too", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Or paste text", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onPasteClipboard) {
                    Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                    Text("Paste", Modifier.padding(start = 6.dp))
                }
            }
            OutlinedTextField(
                value = pasted,
                onValueChange = onPasted,
                placeholder = { Text("사과\tapple\n물 | water\n…") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                minLines = 4,
                maxLines = 10,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            Button(onClick = onRead, enabled = pasted.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Read cards") }
        }
    }

    if (error != null) {
        Surface(color = Ssok.colors.hardContainer, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Ssok.colors.hard)
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun FormatGuide(onCopy: (String, String) -> Unit) {
    var topic by rememberSaveable { mutableStateOf("") }
    SectionHeader("Make a deck with AI")
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                Text("Ask any chat assistant for a deck", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Type a topic, copy the prompt, paste it into the assistant, then share or paste its answer here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                placeholder = { Text("e.g. ordering at a café, TOPIK I verbs, -아/어서") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = { onCopy(DeckFormat.aiPrompt(topic.ifBlank { "everyday Korean" }), "Prompt") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Text("Copy prompt", Modifier.padding(start = 8.dp))
            }
        }
    }

    SectionHeader("File format")
    Text(
        "One card per line: Korean, then English, separated by a tab or |. Optional columns follow in this order: " +
            "romanization, example, example translation, notes, tags, type (word, sentence, grammar). " +
            "A first line with column names lets you pick any order. “# deck: Name” starts a new deck.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Wrap part of a sentence in double braces, 저{{는}} 학생이에요, to turn it into a fill-the-blank card.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CodeBlock("Text (tab-separated)", DeckFormat.TEXT_TEMPLATE.replace("\t", "  →  ")) { onCopy(DeckFormat.TEXT_TEMPLATE, "Template") }
    CodeBlock("JSON", DeckFormat.JSON_TEMPLATE) { onCopy(DeckFormat.JSON_TEMPLATE, "Template") }
}

@Composable
private fun CodeBlock(title: String, code: String, onCopy: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "Copy $title", Modifier.size(18.dp)) }
            }
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Preview(
    result: ParseResult,
    fileName: String?,
    decks: List<DeckEntity>,
    target: Long?,
    onTarget: (Long?) -> Unit,
    importing: Boolean,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    var showWarnings by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${result.cardCount} cards found", style = MaterialTheme.typography.headlineMedium)
        Text(
            listOfNotNull(fileName, result.format, if (result.decks.size > 1) "${result.decks.size} decks" else null).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    result.decks.forEach { deck ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        deck.name ?: "Unnamed deck",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${deck.cards.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                deck.cards.take(4).forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Cloze.plain(c.korean), fontFamily = KoreanDisplay, fontSize = 18.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            c.english, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (deck.cards.size > 4) {
                    Text("and ${deck.cards.size - 4} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (result.warnings.isNotEmpty()) {
        Surface(color = Ssok.colors.mediumContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { showWarnings = !showWarnings }) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (result.warnings.size == 1) "1 line skipped" else "${result.warnings.size} lines skipped",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ssok.colors.medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(if (showWarnings) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Ssok.colors.medium)
                }
                AnimatedVisibility(showWarnings) {
                    Column {
                        result.warnings.take(30).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    SectionHeader("Add to")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DestinationOption(
            selected = target == null,
            title = if (result.decks.size > 1) "Decks named in the file" else "“${result.decks.first().name ?: "Imported deck"}”",
            subtitle = "Creates the deck, or adds to an existing deck with the same name",
            onClick = { onTarget(null) },
        )
        DestinationOption(
            selected = target != null,
            title = "An existing deck",
            subtitle = null,
            onClick = { if (target == null) decks.firstOrNull()?.let { onTarget(it.id) } },
            enabled = decks.isNotEmpty(),
        )
        if (target != null) TargetPicker(decks, target, onTarget)
    }
    Text(
        "Cards already in the deck (same Korean and English) are skipped.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.height(54.dp)) { Text("Start over") }
        Button(onClick = onImport, enabled = !importing, modifier = Modifier.weight(1f).height(54.dp)) {
            Text(if (importing) "Importing…" else "Import ${result.cardCount} cards", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DestinationOption(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TargetPicker(decks: List<DeckEntity>, target: Long, onTarget: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = decks.firstOrNull { it.id == target }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            leadingIcon = current?.let { { DeckTile(it, size = 28.dp) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            decks.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.name) },
                    leadingIcon = { DeckTile(d, size = 28.dp) },
                    onClick = { onTarget(d.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun Done(summary: ImportSummary, onOpen: () -> Unit, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = Ssok.colors.easy, modifier = Modifier.size(64.dp))
        Text("${summary.cardsAdded} cards added", style = MaterialTheme.typography.headlineMedium)
        val parts = buildList {
            if (summary.decksCreated > 0) add(if (summary.decksCreated == 1) "1 new deck" else "${summary.decksCreated} new decks")
            if (summary.duplicates > 0) add("${summary.duplicates} duplicates skipped")
        }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 8.dp)) { Text("Open deck") }
        TextButton(onClick = onMore) { Text("Import more") }
    }
}
