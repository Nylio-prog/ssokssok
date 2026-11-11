package com.nylio.ssokssok.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.ReviewOutcome
import com.nylio.ssokssok.data.StudyConfig
import com.nylio.ssokssok.data.StudyItem
import com.nylio.ssokssok.data.db.CardType
import com.nylio.ssokssok.data.db.Direction
import com.nylio.ssokssok.korean.AltStyle
import com.nylio.ssokssok.korean.AnswerChecker
import com.nylio.ssokssok.korean.CheckResult
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.srs.Rating
import com.nylio.ssokssok.srs.formatDelay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUi(
    val loading: Boolean = true,
    val current: StudyItem? = null,
    val revealed: Boolean = false,
    val typed: String = "",
    val check: CheckResult? = null,
    val suggested: Rating? = null,
    val previews: Map<Rating, String> = emptyMap(),
    val done: Int = 0,
    val remaining: Int = 0,
    val finished: Boolean = false,
    val canUndo: Boolean = false,
    val counts: Map<Rating, Int> = emptyMap(),
    val sessionMs: Long = 0,
    /** Number of the card shown, so the UI can animate between cards. */
    val turn: Int = 0,
)

/** What the learner is asked to produce for an item. */
data class Expectation(val text: String, val korean: Boolean, val style: AltStyle)

fun StudyItem.expectation(): Expectation {
    val style = when (card.cardType) {
        CardType.WORD -> AltStyle.WORD
        CardType.SENTENCE -> AltStyle.SENTENCE
        CardType.GRAMMAR -> AltStyle.GRAMMAR
    }
    return when {
        cloze -> Expectation(Cloze.answers(card.korean).joinToString(" "), true, AltStyle.GRAMMAR)
        direction == Direction.RECOGNIZE -> Expectation(card.english, false, style)
        else -> Expectation(card.korean, true, style)
    }
}

class ReviewViewModel(private val container: AppContainer, val config: StudyConfig) : ViewModel() {
    private val repo = container.repository
    private val noSchedule = config.practice || config.toughOnly

    private val queue = ArrayDeque<StudyItem>()
    private val undoStack = ArrayDeque<UndoEntry>()
    private var shownAt = 0L
    private var sessionStart = 0L
    private var busy = false

    private data class UndoEntry(
        val item: StudyItem,
        val outcome: ReviewOutcome?,
        val requeued: StudyItem?,
        val rating: Rating,
        val activeMs: Long,
    )

    private val _ui = MutableStateFlow(ReviewUi())
    val ui: StateFlow<ReviewUi> = _ui.asStateFlow()

    val isPractice get() = noSchedule

    init {
        container.speaker.warmUp()
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val items = repo.buildSession(config)
            queue.clear()
            queue.addAll(items)
            sessionStart = System.currentTimeMillis()
            _ui.value = ReviewUi(loading = false)
            next()
        }
    }

    /** Starts a fresh session with the same settings (the "keep going" button). */
    fun restart() {
        undoStack.clear()
        _ui.value = ReviewUi(loading = true)
        load()
    }

    private fun next() {
        busy = false
        val item = queue.removeFirstOrNull()
        shownAt = System.currentTimeMillis()
        _ui.update {
            it.copy(
                current = item,
                revealed = false,
                typed = "",
                check = null,
                suggested = null,
                previews = emptyMap(),
                remaining = queue.size + if (item != null) 1 else 0,
                finished = item == null,
                canUndo = undoStack.isNotEmpty(),
                turn = it.turn + 1,
            )
        }
    }

    fun onTyped(text: String) = _ui.update { it.copy(typed = text) }

    fun reveal(giveUp: Boolean = false) {
        val s = _ui.value
        val item = s.current ?: return
        if (s.revealed) return
        val check = if (config.answerMode == com.nylio.ssokssok.data.AnswerMode.TYPE) {
            val e = item.expectation()
            AnswerChecker.check(if (giveUp) "" else s.typed, e.text, e.korean, e.style)
        } else {
            null
        }
        val elapsed = System.currentTimeMillis() - shownAt
        val previews = if (noSchedule) emptyMap() else {
            repo.scheduler().preview(item.progress, System.currentTimeMillis()).mapValues { formatDelay(it.value) }
        }
        _ui.update {
            it.copy(
                revealed = true,
                check = check,
                suggested = check?.suggestedRating(elapsed),
                previews = previews,
            )
        }
    }

    fun rate(rating: Rating) {
        val s = _ui.value
        val item = s.current ?: return
        if (!s.revealed) return
        val now = System.currentTimeMillis()
        val duration = now - shownAt
        // A double tap must not rate twice.
        if (busy) return
        busy = true
        viewModelScope.launch {
            var requeued: StudyItem? = null
            val outcome = if (noSchedule) {
                if (rating == Rating.HARD) requeued = item
                null
            } else {
                repo.recordReview(item, rating, duration, now).also { o ->
                    if (o.requeue) requeued = item.copy(progress = o.after)
                }
            }
            requeued?.let { r ->
                val gap = if (rating == Rating.HARD) 3 else 6
                queue.add(minOf(gap, queue.size), r)
            }
            undoStack.addLast(UndoEntry(item, outcome, requeued, rating, duration.coerceAtMost(120_000)))
            if (undoStack.size > 20) undoStack.removeFirst()
            _ui.update {
                it.copy(
                    done = it.done + 1,
                    counts = it.counts + (rating to (it.counts[rating] ?: 0) + 1),
                    sessionMs = it.sessionMs + duration.coerceAtMost(120_000),
                )
            }
            next()
        }
    }

    fun undo() {
        if (busy) return
        val entry = undoStack.removeLastOrNull() ?: return
        busy = true
        viewModelScope.launch {
            entry.outcome?.let { repo.undo(it) }
            entry.requeued?.let { r -> queue.remove(r) }
            _ui.value.current?.let { queue.addFirst(it) }
            queue.addFirst(entry.item)
            _ui.update {
                it.copy(
                    done = (it.done - 1).coerceAtLeast(0),
                    counts = it.counts + (entry.rating to ((it.counts[entry.rating] ?: 1) - 1).coerceAtLeast(0)),
                    sessionMs = (it.sessionMs - entry.activeMs).coerceAtLeast(0),
                    finished = false,
                )
            }
            next()
        }
    }

    fun speak(text: String) = container.speaker.speak(text)
}
