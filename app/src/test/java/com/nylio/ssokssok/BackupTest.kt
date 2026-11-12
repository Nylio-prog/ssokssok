package com.nylio.ssokssok

import com.nylio.ssokssok.data.AppSettings
import com.nylio.ssokssok.data.Backup
import com.nylio.ssokssok.data.BackupFile
import com.nylio.ssokssok.data.DirectionMode
import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.data.db.ProgressEntity
import com.nylio.ssokssok.data.db.ReviewLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val sample = BackupFile(
        createdAt = 1_700_000_000_000,
        appVersion = "0.3.0",
        settings = AppSettings(newPerDay = 20, direction = DirectionMode.MIXED, expandedFolders = setOf("A")),
        decks = listOf(DeckEntity(id = 3, name = "서울대 한국어+::3A::1-1", createdAt = 1)),
        cards = listOf(CardEntity(id = 7, deckId = 3, korean = "설레다", english = "to flutter", hanja = "", createdAt = 1, updatedAt = 2)),
        progress = listOf(ProgressEntity(cardId = 7, direction = 0, state = 2, stability = 4.5, lastReview = 9)),
        reviews = listOf(ReviewLogEntity(id = 1, cardId = 7, deckId = 3, direction = 0, rating = 3, stateBefore = 0, reviewedAt = 9, durationMs = 2000, intervalDays = 3.0)),
    )

    @Test
    fun roundTripsEverything() {
        assertEquals(sample, Backup.decode(Backup.encode(sample)))
    }

    @Test
    fun rejectsDeckFilesAndJunk() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"decks":[{"name":"x","cards":[]}]}""") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("사과\tapple") }
    }

    @Test
    fun dropsOrphanRows() {
        val b = sample.copy(
            cards = sample.cards + CardEntity(id = 8, deckId = 99, korean = "x", english = "y"),
            progress = sample.progress + ProgressEntity(cardId = 8, direction = 0),
        )
        val c = Backup.consistent(b)
        assertEquals(listOf(7L), c.cards.map { it.id })
        assertEquals(listOf(7L), c.progress.map { it.cardId })
    }
}
