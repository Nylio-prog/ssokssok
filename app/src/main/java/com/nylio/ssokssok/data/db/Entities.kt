package com.nylio.ssokssok.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "decks")
@Serializable
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Full path; folders are separated by "::", e.g. "서울대 한국어+::3A::1-1 시작과 만남". */
    val name: String,
    val description: String = "",
    /** One or two characters shown on the deck tile, e.g. "음" for a food deck. */
    val symbol: String = "",
    /** Index into the deck color palette. */
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val path: DeckPath get() = DeckPath(name)
}

/** A deck name split into folders. "A::B::C" -> folders [A, B], leaf C. */
@JvmInline
value class DeckPath(val full: String) {
    val parts: List<String> get() = full.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(full) }
    val leaf: String get() = parts.last()
    val folders: List<String> get() = parts.dropLast(1)
    val folder: String get() = folders.joinToString(SEP)
    val depth: Int get() = parts.size - 1

    companion object {
        const val SEP = "::"

        /** Accepts "::", " › " or " / " between folder levels, as typed in the deck dialog. */
        fun normalizeFolder(input: String): String =
            input.split(Regex("::|›|/")).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(SEP)

        fun join(folder: String, leaf: String): String =
            (normalizeFolder(folder).split(SEP).filter { it.isNotEmpty() } + leaf.trim()).joinToString(SEP)

        fun display(path: String): String = path.split(SEP).joinToString(" › ")
    }
}

enum class CardType(val key: String, val label: String) {
    WORD("word", "Word"),
    SENTENCE("sentence", "Sentence"),
    GRAMMAR("grammar", "Grammar");

    companion object {
        fun fromKey(key: String?): CardType =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: WORD
    }
}

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
@Serializable
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    /** Korean side. May contain {{cloze}} markers, e.g. "저{{는}} 학생이에요". */
    val korean: String,
    /** English side. Alternatives can be separated with ";" or "/". */
    val english: String,
    /** Sino-Korean breakdown, e.g. "新=새 신; 入=들 입; 生=날 생". See [com.nylio.ssokssok.korean.Hanja]. */
    @ColumnInfo(defaultValue = "")
    val hanja: String = "",
    val example: String = "",
    val exampleTranslation: String = "",
    val notes: String = "",
    /** Space separated tags. */
    val tags: String = "",
    val type: String = CardType.WORD.key,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val cardType: CardType get() = CardType.fromKey(type)
}

/** Direction of a study item. Each card has one schedule per direction. */
enum class Direction(val id: Int) {
    /** See Korean, recall the meaning. */
    RECOGNIZE(0),

    /** See English, produce the Korean. */
    PRODUCE(1);

    companion object {
        fun of(id: Int) = if (id == 1) PRODUCE else RECOGNIZE
    }
}

object CardState {
    const val NEW = 0
    const val LEARNING = 1
    const val REVIEW = 2
    const val RELEARNING = 3
}

@Entity(
    tableName = "progress",
    primaryKeys = ["cardId", "direction"],
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId"), Index("due")],
)
@Serializable
data class ProgressEntity(
    val cardId: Long,
    val direction: Int,
    val state: Int = CardState.NEW,
    val due: Long = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReview: Long? = null,
    /** Successful steps taken while (re)learning. */
    val step: Int = 0,
    /** When the card was first studied in this direction. */
    val introducedAt: Long? = null,
    /** Last scheduled interval in days (0 while learning). */
    val scheduledDays: Double = 0.0,
)

@Entity(tableName = "reviews", indices = [Index("reviewedAt"), Index("cardId")])
@Serializable
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val deckId: Long,
    val direction: Int,
    /** 1 = Hard (forgot), 2 = Medium, 3 = Easy. */
    val rating: Int,
    val stateBefore: Int,
    val reviewedAt: Long,
    val durationMs: Long,
    val intervalDays: Double,
)

data class DeckSummary(
    @Embedded val deck: DeckEntity,
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "due") val due: Int,
    @ColumnInfo(name = "newCount") val newCount: Int,
    @ColumnInfo(name = "introducedToday") val introducedToday: Int,
    @ColumnInfo(name = "mature") val mature: Int,
    @ColumnInfo(name = "learned") val learned: Int,
)

data class StudyRow(
    @Embedded val card: CardEntity,
    @Embedded(prefix = "p_") val progress: ProgressEntity,
)
