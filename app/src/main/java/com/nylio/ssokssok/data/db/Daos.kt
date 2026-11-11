package com.nylio.ssokssok.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val STUDY_COLUMNS = """
    c.*, p.cardId AS p_cardId, p.direction AS p_direction, p.state AS p_state, p.due AS p_due,
    p.stability AS p_stability, p.difficulty AS p_difficulty, p.reps AS p_reps, p.lapses AS p_lapses,
    p.lastReview AS p_lastReview, p.step AS p_step, p.introducedAt AS p_introducedAt,
    p.scheduledDays AS p_scheduledDays
"""

@Dao
interface DeckDao {
    @Query(
        """
        SELECT d.*,
          (SELECT COUNT(*) FROM cards c WHERE c.deckId = d.id) AS total,
          (SELECT COUNT(DISTINCT p.cardId) FROM progress p JOIN cards c ON c.id = p.cardId
             WHERE c.deckId = d.id AND p.state != 0 AND p.due <= :cutoff) AS due,
          (SELECT COUNT(*) FROM progress p JOIN cards c ON c.id = p.cardId
             WHERE c.deckId = d.id AND p.direction = 0 AND p.state = 0) AS newCount,
          (SELECT COUNT(*) FROM progress p JOIN cards c ON c.id = p.cardId
             WHERE c.deckId = d.id AND p.introducedAt >= :dayStart) AS introducedToday,
          (SELECT COUNT(DISTINCT p.cardId) FROM progress p JOIN cards c ON c.id = p.cardId
             WHERE c.deckId = d.id AND p.state = 2 AND p.scheduledDays >= 21) AS mature,
          (SELECT COUNT(DISTINCT p.cardId) FROM progress p JOIN cards c ON c.id = p.cardId
             WHERE c.deckId = d.id AND p.state != 0) AS learned
        FROM decks d ORDER BY d.createdAt ASC, d.id ASC
        """,
    )
    fun observeSummaries(cutoff: Long, dayStart: Long): Flow<List<DeckSummary>>

    @Query("SELECT * FROM decks ORDER BY createdAt ASC, id ASC")
    fun observeDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks ORDER BY createdAt ASC, id ASC")
    suspend fun allDecks(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeDeck(id: Long): Flow<DeckEntity?>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun deck(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun deckByName(name: String): DeckEntity?

    @Insert
    suspend fun insert(deck: DeckEntity): Long

    @Update
    suspend fun update(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun count(): Int
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt ASC, id ASC")
    fun observeByDeck(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt ASC, id ASC")
    suspend fun byDeck(deckId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun card(id: Long): CardEntity?

    @Insert
    suspend fun insert(card: CardEntity): Long

    @Insert
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM cards WHERE hanja LIKE '%' || :key || '%' AND id != :excludeId ORDER BY korean LIMIT 60")
    suspend fun withHanja(key: String, excludeId: Long): List<CardEntity>

    @Query("SELECT korean || char(31) || english FROM cards WHERE deckId = :deckId")
    suspend fun keysForDeck(deckId: Long): List<String>
}

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<ProgressEntity>)

    @Upsert
    suspend fun upsert(row: ProgressEntity)

    @Query("SELECT * FROM progress WHERE cardId = :cardId")
    suspend fun forCard(cardId: Long): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE cardId = :cardId")
    fun observeForCard(cardId: Long): Flow<List<ProgressEntity>>

    @Query("SELECT $STUDY_COLUMNS FROM cards c JOIN progress p ON p.cardId = c.id WHERE c.deckId IN (:deckIds)")
    suspend fun studyRows(deckIds: List<Long>): List<StudyRow>

    @Query("SELECT $STUDY_COLUMNS FROM cards c JOIN progress p ON p.cardId = c.id")
    suspend fun allStudyRows(): List<StudyRow>

    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE cardId IN (SELECT id FROM cards WHERE deckId = :deckId)")
    fun observeForDeck(deckId: Long): Flow<List<ProgressEntity>>

    @Query("UPDATE progress SET state = 0, due = 0, stability = 0, difficulty = 0, reps = 0, lapses = 0, lastReview = NULL, step = 0, introducedAt = NULL, scheduledDays = 0 WHERE cardId = :cardId")
    suspend fun resetCard(cardId: Long)
}

@Dao
interface ReviewDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity): Long

    @Query("DELETE FROM reviews WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM reviews WHERE reviewedAt >= :since ORDER BY reviewedAt ASC")
    fun observeSince(since: Long): Flow<List<ReviewLogEntity>>

    @Query("SELECT reviewedAt FROM reviews ORDER BY reviewedAt DESC LIMIT :limit")
    fun observeRecentTimes(limit: Int): Flow<List<Long>>
}

@Dao
interface ImportDao {
    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Insert
    suspend fun insertCards(cards: List<CardEntity>): List<Long>

    /** Plain UPDATE (never REPLACE, which would delete the row and cascade to its progress). */
    @Update
    suspend fun updateCards(cards: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(rows: List<ProgressEntity>)

    @Transaction
    suspend fun insertCardsWithProgress(cards: List<CardEntity>): Int {
        if (cards.isEmpty()) return 0
        val ids = insertCards(cards)
        insertProgress(ids.flatMap { id -> Direction.entries.map { ProgressEntity(cardId = id, direction = it.id) } })
        return ids.size
    }
}
