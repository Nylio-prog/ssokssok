package com.nylio.ssokssok.data

import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.data.db.ProgressEntity
import com.nylio.ssokssok.data.db.ReviewLogEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A full copy of the app: decks, cards, both schedules per card, review history and settings.
 * Restoring one replaces everything on the device.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long,
    val appVersion: String = "",
    val settings: AppSettings? = null,
    val decks: List<DeckEntity> = emptyList(),
    val cards: List<CardEntity> = emptyList(),
    val progress: List<ProgressEntity> = emptyList(),
    val reviews: List<ReviewLogEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = "ssokssok-backup"
    }
}

object Backup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    /** Reads a backup file. Throws [IllegalArgumentException] with a readable message otherwise. */
    fun decode(text: String): BackupFile {
        val format = runCatching { json.parseToJsonElement(text).jsonObject["format"]?.jsonPrimitive?.content }.getOrNull()
        require(format == BackupFile.FORMAT) { "This isn't a ssokssok backup. Deck files go through Import." }
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("This backup is damaged and can't be read.") }
        require(backup.version <= 1) { "This backup comes from a newer version of ssokssok. Update the app first." }
        return backup
    }

    /** Drops rows that point at missing decks or cards, so a hand-edited file can't break the database. */
    fun consistent(b: BackupFile): BackupFile {
        val deckIds = b.decks.map { it.id }.toSet()
        val cards = b.cards.filter { it.deckId in deckIds }
        val cardIds = cards.map { it.id }.toSet()
        return b.copy(cards = cards, progress = b.progress.filter { it.cardId in cardIds })
    }
}
