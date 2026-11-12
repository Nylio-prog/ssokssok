package com.nylio.ssokssok.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.BuildConfig
import com.nylio.ssokssok.data.Backup
import com.nylio.ssokssok.data.BackupFile
import com.nylio.ssokssok.data.ThemeMode
import com.nylio.ssokssok.ui.Routes
import com.nylio.ssokssok.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(container: AppContainer, nav: NavController) {
    val s by container.settings.flow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update = container.settings::update

    var pendingRestore by remember { mutableStateOf<BackupFile?>(null) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = container.repository.backup(BuildConfig.VERSION_NAME)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                            ?: error("Couldn't open the file")
                    }
                }
                    .onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: error("Couldn't open the file")
                    }
                    withContext(Dispatchers.Default) { Backup.decode(text) }
                }
                    .onSuccess { pendingRestore = it }
                    .onFailure { Toast.makeText(context, it.message ?: "Couldn't read the backup", Toast.LENGTH_LONG).show() }
            }
        }
    }

    pendingRestore?.let { b ->
        val date = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
            .format(Instant.ofEpochMilli(b.createdAt).atZone(ZoneId.systemDefault()))
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "From $date: ${b.decks.size} decks, ${b.cards.size} cards, ${b.reviews.size} reviews.\n\n" +
                        "Everything currently in the app is replaced.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        runCatching { container.repository.restore(b) }
                            .onSuccess { Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Learning")
            Group {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("New cards per day", style = MaterialTheme.typography.titleSmall)
                        Text("Per deck", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { update { it.copy(newPerDay = (it.newPerDay - 5).coerceAtLeast(0)) } }) {
                        Icon(Icons.Outlined.Remove, "Fewer")
                    }
                    Text("${s.newPerDay}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 10.dp))
                    FilledTonalIconButton(onClick = { update { it.copy(newPerDay = (it.newPerDay + 5).coerceAtMost(200)) } }) {
                        Icon(Icons.Outlined.Add, "More")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Target recall", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${(s.retention * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Slider(
                        value = s.retention.toFloat(),
                        onValueChange = { v -> update { it.copy(retention = (v * 100).roundToInt() / 100.0) } },
                        valueRange = 0.80f..0.97f,
                        steps = 16,
                    )
                    Text(
                        "Higher means more reviews. 90% suits most people.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Cards")
            Group {
                Toggle(
                    "Play Korean audio automatically",
                    "Tap Listen twice to hear it slower",
                    s.autoplayAudio,
                ) { v -> update { it.copy(autoplayAudio = v) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Toggle("Vibrate on answers", null, s.haptics) { v -> update { it.copy(haptics = v) } }
            }

            SectionHeader("Appearance")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = s.theme == t,
                        onClick = { update { it.copy(theme = t) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(t.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                }
            }

            SectionHeader("Your data")
            Group {
                Link(Icons.Outlined.FileDownload, "Import cards", "From a file, pasted text or an AI chat") { nav.navigate(Routes.import()) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Link(Icons.Outlined.Backup, "Back up everything", "Cards, progress and history in one file") {
                    backup.launch("ssokssok-backup-${LocalDate.now()}.json")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Link(Icons.Outlined.Restore, "Restore a backup", "Replaces everything in the app") {
                    restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }

            SectionHeader("About")
            Text(
                "ssokssok ${BuildConfig.VERSION_NAME} · FSRS scheduling",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(22.dp)) {
        Column { content() }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Link(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
