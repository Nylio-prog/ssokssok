package com.nylio.ssokssok.data

import androidx.room.withTransaction
import com.nylio.ssokssok.data.db.AppDatabase
import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.data.db.CardState
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.data.db.DeckPath
import com.nylio.ssokssok.data.db.DeckSummary
import com.nylio.ssokssok.data.db.Direction
import com.nylio.ssokssok.data.db.ProgressEntity
import com.nylio.ssokssok.data.db.ReviewLogEntity
import com.nylio.ssokssok.data.db.StudyRow
import com.nylio.ssokssok.importer.DeckFormat
import com.nylio.ssokssok.importer.ParseResult
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.korean.Hangul
import com.nylio.ssokssok.srs.DAY_MS
import com.nylio.ssokssok.srs.Fsrs
import com.nylio.ssokssok.srs.Rating
import com.nylio.ssokssok.srs.Scheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Days roll over at 4 am, so a late-night session still counts for "today". */
object DayClock {
    private const val ROLLOVER_HOURS = 4L

    fun date(t: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(t).atZone(zone).minusHours(ROLLOVER_HOURS).toLocalDate()

    fun dayStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        date(now, zone).atStartOfDay(zone).plusHours(ROLLOVER_HOURS).toInstant().toEpochMilli()

    fun nextDayStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        date(now, zone).plusDays(1).atStartOfDay(zone).plusHours(ROLLOVER_HOURS).toInstant().toEpochMilli()

    /** Emits the current day start, and again whenever the day changes. */
    fun ticks(): Flow<Long> = flow {
        while (true) {
            emit(dayStart(System.currentTimeMillis()))
            delay(60_000)
        }
    }.distinctUntilChanged()
}

data class StudyItem(
    val card: CardEntity,
    val progress: ProgressEntity,
    val direction: Direction,
) {
    val cloze: Boolean get() = Cloze.isCloze(card.korean)
    val isNew: Boolean get() = progress.state == CardState.NEW
    val key: String get() = "${card.id}:${direction.id}"
}

data class StudyConfig(
    /** null = every deck. */
    val deckIds: List<Long>?,
    val direction: DirectionMode,
    val answerMode: AnswerMode,
    /** 0 = no limit. */
    val sessionSize: Int,
    /** Practice runs through cards without touching their schedule. */
    val practice: Boolean = false,
    /** Practice only the cards that keep slipping (3+ lapses or high difficulty). */
    val toughOnly: Boolean = false,
)

data class SessionPreview(val due: Int, val new: Int, val tough: Int, val total: Int)

data class ReviewOutcome(
    val before: ProgressEntity,
    val after: ProgressEntity,
    val logId: Long,
    val requeue: Boolean,
)

data class ImportSummary(val decksCreated: Int, val cardsAdded: Int, val duplicates: Int, val deckIds: List<Long>)

data class StatsSnapshot(
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val studiedToday: Boolean = false,
    val reviewsToday: Int = 0,
    val minutesToday: Int = 0,
    val retention30: Double? = null,
    val totalReviews: Int = 0,
    /** Reviews per day, oldest first, ending today. */
    val heat: List<Int> = emptyList(),
    val heatEnd: LocalDate = LocalDate.now(),
    /** Cards due per day starting today (today includes overdue). */
    val forecast: List<Int> = emptyList(),
    val newCards: Int = 0,
    val learning: Int = 0,
    val young: Int = 0,
    val mature: Int = 0,
    val tough: Int = 0,
)

val DECK_SYMBOL_FALLBACK = listOf("가", "나", "다", "라", "마", "바", "사", "아")

class Repository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    private val decks = db.deckDao()
    private val cards = db.cardDao()
    private val progress = db.progressDao()
    private val reviews = db.reviewDao()
    private val importer = db.importDao()

    // ------------------------------------------------------------ Decks

    @OptIn(ExperimentalCoroutinesApi::class)
    fun deckSummaries(): Flow<List<DeckSummary>> = DayClock.ticks().flatMapLatest { start ->
        decks.observeSummaries(cutoff = start + DAY_MS, dayStart = start)
    }

    fun observeDecks() = decks.observeDecks()
    fun observeDeck(id: Long) = decks.observeDeck(id)

    suspend fun createDeck(name: String, description: String = "", symbol: String = ""): Long {
        val count = decks.count()
        return decks.insert(
            DeckEntity(
                name = name.trim(),
                description = description.trim(),
                symbol = symbol.trim().take(3).ifEmpty { defaultSymbol(name, count) },
                color = count % 8,
            ),
        )
    }

    suspend fun updateDeck(deck: DeckEntity) = decks.update(deck)
    suspend fun deleteDeck(id: Long) = decks.delete(id)

    private fun defaultSymbol(name: String, index: Int): String {
        val first = DeckPath(name).leaf.firstOrNull { it.isLetterOrDigit() } ?: return DECK_SYMBOL_FALLBACK[index % 8]
        return if (Hangul.isSyllable(first)) first.toString() else first.uppercase()
    }

    // ------------------------------------------------------------ Cards

    fun observeCards(deckId: Long) = cards.observeByDeck(deckId)
    fun observeAllCards() = cards.observeAll()
    fun observeProgress(deckId: Long) = progress.observeForDeck(deckId)
    fun observeAllProgress() = progress.observeAll()
    suspend fun card(id: Long) = cards.card(id)
    suspend fun progressFor(cardId: Long) = progress.forCard(cardId)

    suspend fun saveCard(card: CardEntity): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        if (card.id == 0L) {
            val id = cards.insert(card.copy(createdAt = now, updatedAt = now))
            progress.insertAll(Direction.entries.map { ProgressEntity(cardId = id, direction = it.id) })
            id
        } else {
            cards.update(card.copy(updatedAt = now))
            card.id
        }
    }

    suspend fun deleteCard(id: Long) = cards.delete(id)
    suspend fun resetCard(id: Long) = progress.resetCard(id)

    // ------------------------------------------------------------ Study

    private suspend fun rows(deckIds: List<Long>?): List<StudyRow> =
        if (deckIds == null) progress.allStudyRows() else progress.studyRows(deckIds)

    private fun allowedDirections(item: CardEntity, mode: DirectionMode): List<Int> = when {
        Cloze.isCloze(item.korean) -> listOf(Direction.RECOGNIZE.id)
        mode == DirectionMode.RECOGNIZE -> listOf(Direction.RECOGNIZE.id)
        mode == DirectionMode.PRODUCE -> listOf(Direction.PRODUCE.id)
        else -> listOf(Direction.RECOGNIZE.id, Direction.PRODUCE.id)
    }

    private fun isTough(p: ProgressEntity) = p.state != CardState.NEW && (p.lapses >= 3 || p.difficulty >= 8.5)

    suspend fun preview(deckIds: List<Long>?, mode: DirectionMode): SessionPreview {
        val cfg = StudyConfig(deckIds, mode, AnswerMode.FLIP, sessionSize = 0)
        val all = rows(deckIds)
        val (due, fresh) = select(all, cfg, System.currentTimeMillis())
        val tough = all.filter { isTough(it.progress) }.distinctBy { it.card.id }.size
        return SessionPreview(due.size, fresh.size, tough, all.distinctBy { it.card.id }.size)
    }

    suspend fun buildSession(cfg: StudyConfig, now: Long = System.currentTimeMillis()): List<StudyItem> {
        val all = rows(cfg.deckIds)
        val limit = if (cfg.sessionSize <= 0) Int.MAX_VALUE else cfg.sessionSize

        if (cfg.practice || cfg.toughOnly) {
            val byCard = all.groupBy { it.card.id }
            val pool = byCard.values.mapNotNull { list ->
                val allowed = allowedDirections(list.first().card, cfg.direction)
                val candidates = list.filter { it.progress.direction in allowed }
                if (cfg.toughOnly) {
                    candidates.filter { isTough(it.progress) }.maxByOrNull { it.progress.lapses }
                } else {
                    candidates.filter { it.progress.state != CardState.NEW }.randomOrNull()
                        ?: candidates.randomOrNull()
                }
            }
            val ordered = if (cfg.toughOnly) pool.sortedByDescending { it.progress.lapses } else pool.shuffled()
            return ordered.take(if (cfg.sessionSize <= 0) 50 else limit)
                .map { StudyItem(it.card, it.progress, Direction.of(it.progress.direction)) }
        }

        val (due, fresh) = select(all, cfg, now)
        val reviewsFirst = due.take(limit)
        val newSlots = (limit - reviewsFirst.size).coerceAtLeast(0)
        val newOnes = fresh.take(newSlots)

        // Spread new cards through the reviews so a session never ends on a wall of unknowns.
        val out = ArrayList<StudyRow>(reviewsFirst.size + newOnes.size)
        val gap = if (newOnes.isEmpty()) Int.MAX_VALUE else (reviewsFirst.size / (newOnes.size + 1)).coerceAtLeast(1)
        val newQueue = ArrayDeque(newOnes)
        reviewsFirst.forEachIndexed { i, r ->
            out += r
            if ((i + 1) % gap == 0 && newQueue.isNotEmpty()) out += newQueue.removeFirst()
        }
        out.addAll(newQueue)
        return out.map { StudyItem(it.card, it.progress, Direction.of(it.progress.direction)) }
    }

    /** Returns (due reviews sorted by urgency, new cards available today). */
    private fun select(all: List<StudyRow>, cfg: StudyConfig, now: Long): Pair<List<StudyRow>, List<StudyRow>> {
        val dayStart = DayClock.dayStart(now)
        val cutoff = dayStart + DAY_MS
        val byCard = all.groupBy { it.card.id }
        val introducedToday = all.filter { (it.progress.introducedAt ?: 0) >= dayStart }
            .groupingBy { it.card.deckId }.eachCount()
        val newLimit = settings.value.newPerDay

        val due = mutableListOf<StudyRow>()
        val fresh = mutableListOf<StudyRow>()
        for ((_, list) in byCard) {
            val card = list.first().card
            val allowed = allowedDirections(card, cfg.direction)
            val dueRow = list.filter { it.progress.direction in allowed && it.progress.state != CardState.NEW && it.progress.due <= cutoff }
                .minByOrNull { it.progress.due }
            if (dueRow != null) {
                due += dueRow
                continue
            }
            val recognize = list.firstOrNull { it.progress.direction == Direction.RECOGNIZE.id }
            val produce = list.firstOrNull { it.progress.direction == Direction.PRODUCE.id }
            val newRow = when {
                Cloze.isCloze(card.korean) -> recognize?.takeIf { it.progress.state == CardState.NEW }
                cfg.direction == DirectionMode.RECOGNIZE -> recognize?.takeIf { it.progress.state == CardState.NEW }
                cfg.direction == DirectionMode.PRODUCE -> produce?.takeIf { it.progress.state == CardState.NEW }
                // Mixed: production unlocks once the word is recognised reliably.
                else -> recognize?.takeIf { it.progress.state == CardState.NEW }
                    ?: produce?.takeIf { it.progress.state == CardState.NEW && recognize?.progress?.state == CardState.REVIEW }
            }
            if (newRow != null) fresh += newRow
        }
        due.sortWith(compareBy<StudyRow> { if (it.progress.state == CardState.REVIEW) 1 else 0 }.thenBy {
            val p = it.progress
            if (p.state == CardState.REVIEW) {
                Fsrs.retrievability((now - (p.lastReview ?: now)).toDouble() / DAY_MS, p.stability)
            } else {
                p.due.toDouble()
            }
        })
        val freshLimited = fresh.sortedWith(compareBy({ it.card.deckId }, { it.card.createdAt }, { it.card.id }))
            .groupBy { it.card.deckId }
            .flatMap { (deckId, list) -> list.take((newLimit - (introducedToday[deckId] ?: 0)).coerceAtLeast(0)) }
        return due to interleaveDecks(freshLimited)
    }

    private fun interleaveDecks(rows: List<StudyRow>): List<StudyRow> {
        val groups = rows.groupBy { it.card.deckId }.values.map { ArrayDeque(it) }
        val out = mutableListOf<StudyRow>()
        while (groups.any { it.isNotEmpty() }) groups.forEach { g -> g.removeFirstOrNull()?.let { out += it } }
        return out
    }

    fun scheduler() = Scheduler(retention = settings.value.retention)

    suspend fun recordReview(item: StudyItem, rating: Rating, durationMs: Long, now: Long = System.currentTimeMillis()): ReviewOutcome {
        val result = scheduler().schedule(item.progress, rating, now)
        val logId = db.withTransaction {
            progress.upsert(result.progress)
            reviews.insert(
                ReviewLogEntity(
                    cardId = item.card.id,
                    deckId = item.card.deckId,
                    direction = item.direction.id,
                    rating = rating.grade,
                    stateBefore = item.progress.state,
                    reviewedAt = now,
                    durationMs = durationMs.coerceAtMost(120_000),
                    intervalDays = result.progress.scheduledDays,
                ),
            )
        }
        return ReviewOutcome(item.progress, result.progress, logId, result.requeue)
    }

    suspend fun undo(outcome: ReviewOutcome) = db.withTransaction {
        progress.upsert(outcome.before)
        reviews.delete(outcome.logId)
    }

    // ------------------------------------------------------------ Import / export

    suspend fun import(parsed: ParseResult, targetDeckId: Long?): ImportSummary = db.withTransaction {
        var created = 0
        var added = 0
        var dupes = 0
        val touched = mutableListOf<Long>()
        for (deck in parsed.decks) {
            val deckId = targetDeckId ?: run {
                val name = deck.name?.trim()?.ifEmpty { null } ?: "Imported deck"
                decks.deckByName(name)?.id ?: run {
                    created++
                    val count = decks.count()
                    importer.insertDeck(
                        DeckEntity(
                            name = name,
                            description = deck.description,
                            symbol = deck.symbol.ifBlank { defaultSymbol(name, count) },
                            color = count % 8,
                        ),
                    )
                }
            }
            val existing = cards.keysForDeck(deckId).toHashSet()
            val now = System.currentTimeMillis()
            val toInsert = mutableListOf<CardEntity>()
            deck.cards.forEachIndexed { i, c ->
                val key = c.korean + '\u001F' + c.english
                if (!existing.add(key)) {
                    dupes++
                } else {
                    toInsert += CardEntity(
                        deckId = deckId,
                        korean = c.korean,
                        english = c.english,
                        hanja = c.hanja,
                        example = c.example,
                        exampleTranslation = c.exampleTranslation,
                        notes = c.notes,
                        tags = c.tags.joinToString(" "),
                        type = c.type.key,
                        // Keep file order: createdAt increases along the file.
                        createdAt = now + i,
                        updatedAt = now + i,
                    )
                }
            }
            added += importer.insertCardsWithProgress(toInsert)
            if (deckId !in touched) touched += deckId
        }
        ImportSummary(created, added, dupes, touched)
    }

    suspend fun exportDeck(deckId: Long): String {
        val deck = decks.deck(deckId) ?: return DeckFormat.toJson(emptyList())
        return DeckFormat.toJson(listOf(deck to cards.byDeck(deckId)))
    }

    suspend fun exportAll(): String = DeckFormat.toJson(decks.allDecks().map { it to cards.byDeck(it.id) })

    /** Other cards whose hanja field mentions the same character (or 훈 음 when no character is given). */
    suspend fun cardsSharingHanja(key: String, excludeCardId: Long): List<CardEntity> =
        if (key.isBlank()) emptyList() else cards.withHanja(key.trim(), excludeCardId)

    /** Moves every deck under [fromFolder] to [toFolder] (used when a folder is renamed). */
    suspend fun renameFolder(fromFolder: String, toFolder: String) = db.withTransaction {
        val prefix = fromFolder + DeckPath.SEP
        val target = DeckPath.normalizeFolder(toFolder)
        decks.allDecks().filter { it.name.startsWith(prefix) }.forEach { d ->
            val rest = d.name.removePrefix(prefix)
            decks.update(d.copy(name = if (target.isEmpty()) rest else target + DeckPath.SEP + rest))
        }
    }

    // ------------------------------------------------------------ Stats

    fun stats(): Flow<StatsSnapshot> = combine(
        reviews.observeSince(System.currentTimeMillis() - 400 * DAY_MS),
        progress.observeAll(),
        DayClock.ticks(),
    ) { logs, rows, _ -> computeStats(logs, rows, System.currentTimeMillis()) }

    private fun computeStats(logs: List<ReviewLogEntity>, rows: List<ProgressEntity>, now: Long): StatsSnapshot {
        val zone = ZoneId.systemDefault()
        val today = DayClock.date(now, zone)
        val perDay = logs.groupingBy { DayClock.date(it.reviewedAt, zone) }.eachCount()

        var streak = 0
        var d = if ((perDay[today] ?: 0) > 0) today else today.minusDays(1)
        while ((perDay[d] ?: 0) > 0) { streak++; d = d.minusDays(1) }

        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in perDay.keys.sorted()) {
            run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            prev = day
        }

        val todayLogs = logs.filter { DayClock.date(it.reviewedAt, zone) == today }
        val since30 = now - 30 * DAY_MS
        val matureLogs = logs.filter { it.reviewedAt >= since30 && it.stateBefore == CardState.REVIEW }
        val retention = if (matureLogs.size >= 5) matureLogs.count { it.rating > 1 }.toDouble() / matureLogs.size else null

        val weeks = 18
        val days = weeks * 7
        val heat = (days - 1 downTo 0).map { perDay[today.minusDays(it.toLong())] ?: 0 }

        val dayStart = DayClock.dayStart(now, zone)
        val forecast = IntArray(7)
        rows.filter { it.state != CardState.NEW }.forEach { p ->
            val idx = if (p.due < dayStart + DAY_MS) 0 else ((p.due - dayStart) / DAY_MS).toInt()
            if (idx in 0..6) forecast[idx]++
        }

        // Card breakdown follows the recognition side, which every card has.
        val rec = rows.filter { it.direction == Direction.RECOGNIZE.id }
        return StatsSnapshot(
            streak = streak,
            bestStreak = maxOf(best, streak),
            studiedToday = todayLogs.isNotEmpty(),
            reviewsToday = todayLogs.size,
            minutesToday = (todayLogs.sumOf { it.durationMs } / 60_000L).toInt(),
            retention30 = retention,
            totalReviews = logs.size,
            heat = heat,
            heatEnd = today,
            forecast = forecast.toList(),
            newCards = rec.count { it.state == CardState.NEW },
            learning = rec.count { it.state == CardState.LEARNING || it.state == CardState.RELEARNING },
            young = rec.count { it.state == CardState.REVIEW && it.scheduledDays < 21 },
            mature = rec.count { it.state == CardState.REVIEW && it.scheduledDays >= 21 },
            tough = rows.filter { isTough(it) }.map { it.cardId }.distinct().size,
        )
    }
}
