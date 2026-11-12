package com.nylio.ssokssok.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.AnswerMode
import com.nylio.ssokssok.data.DirectionMode
import com.nylio.ssokssok.data.SessionPreview
import com.nylio.ssokssok.data.StudyConfig
import com.nylio.ssokssok.ui.components.SectionHeader

/**
 * Asks the two questions that matter before a session: which way round, and flip or type.
 * Remembers the answers for next time.
 */
@Composable
fun StudySetupSheet(
    container: AppContainer,
    title: String,
    deckIds: List<Long>?,
    onDismiss: () -> Unit,
    onStart: (StudyConfig) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val saved = container.settings.value
    var direction by remember { mutableStateOf(saved.direction) }
    var mode by remember { mutableStateOf(saved.answerMode) }
    var size by remember { mutableStateOf(saved.sessionSize) }
    var preview by remember { mutableStateOf<SessionPreview?>(null) }

    LaunchedEffect(direction, deckIds) {
        preview = container.repository.preview(deckIds, direction)
    }

    fun start(practice: Boolean = false, tough: Boolean = false) {
        container.settings.update { it.copy(direction = direction, answerMode = mode, sessionSize = size) }
        onStart(StudyConfig(deckIds, direction, mode, size, practice = practice, toughOnly = tough))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                val p = preview
                Text(
                    when {
                        p == null -> " "
                        p.due + p.new == 0 -> "Nothing due in this direction"
                        else -> "${p.due} to review · ${p.new} new"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader("Direction")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DirectionMode.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = direction == d,
                        onClick = { direction = d },
                        shape = SegmentedButtonDefaults.itemShape(i, DirectionMode.entries.size),
                        icon = {},
                    ) { Text(d.short, maxLines = 1) }
                }
            }

            SectionHeader("Answer by")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AnswerMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, AnswerMode.entries.size),
                        icon = {
                            Icon(
                                if (m == AnswerMode.FLIP) Icons.Outlined.TouchApp else Icons.Outlined.Keyboard,
                                null,
                                Modifier.size(18.dp),
                            )
                        },
                    ) { Text(if (m == AnswerMode.FLIP) "Flipping" else "Typing") }
                }
            }

            SectionHeader("Cards per session")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 50, 0).forEach { n ->
                    FilterChip(
                        selected = size == n,
                        onClick = { size = n },
                        label = { Text(if (n == 0) "All" else "$n") },
                    )
                }
            }

            val p = preview
            val hasWork = p != null && p.due + p.new > 0
            if (hasWork || p == null) {
                Button(
                    onClick = { start() },
                    enabled = hasWork,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Start", style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 8.dp))
                }
            }
            if (p != null && p.total > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { start(practice = true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Replay, null, Modifier.size(18.dp))
                        Text("Practice", Modifier.padding(start = 6.dp))
                    }
                    if (p.tough > 0) {
                        OutlinedButton(
                            onClick = { start(tough = true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Bolt, null, Modifier.size(18.dp))
                            Text("Tough cards · ${p.tough}", Modifier.padding(start = 6.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
