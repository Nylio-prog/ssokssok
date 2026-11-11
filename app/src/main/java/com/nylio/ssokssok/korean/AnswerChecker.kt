package com.nylio.ssokssok.korean

import com.nylio.ssokssok.srs.Rating

enum class Verdict { CORRECT, CLOSE, WRONG }

enum class AltStyle { WORD, SENTENCE, GRAMMAR }

enum class DiffKind { SAME, WRONG, MISSING, EXTRA }

data class DiffSegment(val text: String, val kind: DiffKind)

data class CheckResult(
    val verdict: Verdict,
    /** The accepted answer the input was compared with. */
    val expected: String,
    /** Input with errors marked (SAME / WRONG / EXTRA). */
    val yours: List<DiffSegment>,
    /** Expected answer with what was missed marked (SAME / MISSING). */
    val target: List<DiffSegment>,
    val note: String? = null,
) {
    /** Rating to pre-select, given how long the answer took. */
    fun suggestedRating(durationMs: Long): Rating = when (verdict) {
        Verdict.WRONG -> Rating.HARD
        Verdict.CLOSE -> Rating.MEDIUM
        Verdict.CORRECT -> if (durationMs <= 8_000 + expected.length * 400L) Rating.EASY else Rating.MEDIUM
    }
}

object AnswerChecker {
    private val SPLIT_WORD = Regex("\\s*[;/,]\\s*")
    private val SPLIT_SENTENCE = Regex("\\s*[;/]\\s*")
    private val SPLIT_GRAMMAR = Regex("\\s*;\\s*")
    private val PARENS = Regex("\\([^)]*\\)")
    private val EN_PUNCT = Regex("[^\\p{L}\\p{N}' ]")
    private val KO_PUNCT = Regex("[.,!?~…·\"'“”‘’()\\[\\]{}:;-]")
    private val SPACES = Regex("\\s+")
    private val EN_PREFIX = Regex("^(to|be|a|an|the)\\s+")

    /**
     * Accepted answers for a field: "eat; have (a meal)" -> ["eat", "have (a meal)", "have", "have a meal"].
     * Words also split on commas; sentences only on ";" and "/"; grammar patterns only on ";".
     */
    fun alternatives(field: String, style: AltStyle = AltStyle.WORD): List<String> {
        val splitter = when (style) {
            AltStyle.WORD -> SPLIT_WORD
            AltStyle.SENTENCE -> SPLIT_SENTENCE
            AltStyle.GRAMMAR -> SPLIT_GRAMMAR
        }
        val parts = field.split(splitter).map { it.trim() }.filter { it.isNotEmpty() }
        val out = LinkedHashSet<String>()
        for (p in parts) {
            out += p
            val stripped = PARENS.replace(p, " ").replace(SPACES, " ").trim()
            if (stripped.isNotEmpty()) out += stripped
            val unwrapped = p.replace("(", "").replace(")", "").replace(SPACES, " ").trim()
            if (unwrapped.isNotEmpty()) out += unwrapped
        }
        if (out.isEmpty() && field.isNotBlank()) out += field.trim()
        return out.toList()
    }

    fun normalize(s: String, korean: Boolean): String =
        if (korean) {
            KO_PUNCT.replace(s, " ").replace(SPACES, " ").trim()
        } else {
            var t = EN_PUNCT.replace(s.lowercase(), " ").replace(SPACES, " ").trim()
            repeat(2) { t = EN_PREFIX.replace(t, "") }
            t
        }

    fun check(input: String, expectedField: String, korean: Boolean, style: AltStyle = AltStyle.WORD): CheckResult {
        val alts = alternatives(expectedField, style).ifEmpty { listOf(expectedField) }
        val typed = input.trim()
        if (typed.isEmpty()) {
            val first = alts.first()
            return CheckResult(Verdict.WRONG, first, emptyList(), listOf(DiffSegment(first, DiffKind.MISSING)), "No answer")
        }
        val n = normalize(typed, korean)

        var best: Pair<String, Int>? = null
        for (alt in alts) {
            val a = normalize(alt, korean)
            if (a == n) return correct(typed, alt)
            if (korean && a.replace(" ", "") == n.replace(" ", "")) {
                return correct(typed, alt).copy(note = "Mind the spacing: $alt")
            }
            val dist = if (korean) {
                levenshtein(Hangul.decompose(a.replace(" ", "")), Hangul.decompose(n.replace(" ", "")))
            } else {
                levenshtein(a, n)
            }
            if (best == null || dist < best.second) best = alt to dist
        }
        val (alt, dist) = best!!
        val size = if (korean) Hangul.decompose(normalize(alt, true).replace(" ", "")).length else normalize(alt, false).length
        val tolerance = when {
            size >= 9 -> 2
            size >= 4 -> 1
            else -> 0
        }
        val verdict = if (dist <= tolerance) Verdict.CLOSE else Verdict.WRONG
        val (yours, target) = diff(if (korean) typed else typed.lowercase(), if (korean) alt else alt.lowercase())
        val note = when {
            verdict == Verdict.CLOSE && korean -> "One small jamo slip. Counted as a near miss."
            verdict == Verdict.CLOSE -> "A small spelling slip. Counted as a near miss."
            else -> null
        }
        return CheckResult(verdict, alt, yours, target, note)
    }

    private fun correct(typed: String, alt: String) = CheckResult(
        Verdict.CORRECT, alt,
        listOf(DiffSegment(typed, DiffKind.SAME)),
        listOf(DiffSegment(alt, DiffKind.SAME)),
    )

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Character alignment between what was typed and the expected answer. */
    fun diff(typed: String, expected: String): Pair<List<DiffSegment>, List<DiffSegment>> {
        val a = typed
        val b = expected
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        val yours = ArrayList<Pair<Char, DiffKind>>()
        val target = ArrayList<Pair<Char, DiffKind>>()
        var i = a.length
        var j = b.length
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && a[i - 1] == b[j - 1] && dp[i][j] == dp[i - 1][j - 1] -> {
                    yours += a[i - 1] to DiffKind.SAME; target += b[j - 1] to DiffKind.SAME; i--; j--
                }
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1 -> {
                    yours += a[i - 1] to DiffKind.WRONG; target += b[j - 1] to DiffKind.MISSING; i--; j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    yours += a[i - 1] to DiffKind.EXTRA; i--
                }
                else -> {
                    target += b[j - 1] to DiffKind.MISSING; j--
                }
            }
        }
        return merge(yours.asReversed()) to merge(target.asReversed())
    }

    private fun merge(chars: List<Pair<Char, DiffKind>>): List<DiffSegment> {
        val out = mutableListOf<DiffSegment>()
        val sb = StringBuilder()
        var kind: DiffKind? = null
        for ((c, k) in chars) {
            if (k != kind && kind != null) {
                out += DiffSegment(sb.toString(), kind); sb.clear()
            }
            kind = k; sb.append(c)
        }
        if (kind != null && sb.isNotEmpty()) out += DiffSegment(sb.toString(), kind)
        return out
    }
}
