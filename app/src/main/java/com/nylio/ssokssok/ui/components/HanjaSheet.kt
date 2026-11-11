package com.nylio.ssokssok.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nylio.ssokssok.data.Repository
import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.korean.Hanja
import com.nylio.ssokssok.korean.HanjaPart
import com.nylio.ssokssok.ui.theme.KoreanDisplay

/** Other words in the learner's decks built on the same Sino-Korean syllable. */
@Composable
fun HanjaFamilySheet(repository: Repository, part: HanjaPart, cardId: Long, onDismiss: () -> Unit) {
    var related by remember(part) { mutableStateOf<List<CardEntity>?>(null) }
    LaunchedEffect(part) { related = repository.cardsSharingHanja(Hanja.searchKey(part), cardId) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(part.eum.ifEmpty { part.char }, fontFamily = KoreanDisplay, fontSize = 44.sp, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(part.name.ifEmpty { part.char }, style = MaterialTheme.typography.titleLarge)
                        if (part.char.isNotEmpty() && part.name.isNotEmpty()) {
                            Text(part.char, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (part.english.isNotBlank()) {
                        Text(part.english, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            val list = related
            when {
                list == null -> Text(" ")
                list.isEmpty() -> Text(
                    "No other word in your decks uses this one yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    SectionHeader("Also in your decks")
                    LazyColumn {
                        items(list, key = { it.id }) { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Cloze.plain(c.korean), fontFamily = KoreanDisplay, fontSize = 20.sp, modifier = Modifier.weight(0.45f))
                                Text(
                                    c.english,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.55f),
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
