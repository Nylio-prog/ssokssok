package com.nylio.ssokssok.korean

object Hangul {
    private const val BASE = 0xAC00
    private const val LAST = 0xD7A3
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private val JONG = listOf(
        "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ",
        "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ",
    )

    fun isSyllable(c: Char) = c.code in BASE..LAST
    fun isJamo(c: Char) = c.code in 0x3131..0x318E || c.code in 0x1100..0x11FF

    fun containsHangul(s: String) = s.any { isSyllable(it) || isJamo(it) }

    /** Splits syllables into compatibility jamo: "한글" -> "ㅎㅏㄴㄱㅡㄹ". */
    fun decompose(s: String): String = buildString {
        for (c in s) {
            if (isSyllable(c)) {
                val idx = c.code - BASE
                append(CHO[idx / 588])
                append(JUNG[(idx % 588) / 28])
                append(JONG[idx % 28])
            } else {
                append(c)
            }
        }
    }
}

/** Cloze markup: "저{{는}} 학생이에요" or with a hint "저{{는::topic}} 학생이에요". */
object Cloze {
    private val REGEX = Regex("\\{\\{(.+?)\\}\\}")

    data class Part(val text: String, val isBlank: Boolean, val hint: String? = null)

    fun isCloze(s: String) = REGEX.containsMatchIn(s)

    fun parts(s: String): List<Part> {
        val out = mutableListOf<Part>()
        var last = 0
        for (m in REGEX.findAll(s)) {
            if (m.range.first > last) out += Part(s.substring(last, m.range.first), false)
            val (answer, hint) = splitHint(m.groupValues[1])
            out += Part(answer, true, hint)
            last = m.range.last + 1
        }
        if (last < s.length) out += Part(s.substring(last), false)
        return out
    }

    fun answers(s: String): List<String> = parts(s).filter { it.isBlank }.map { it.text }

    /** The sentence with markup removed. */
    fun plain(s: String): String = REGEX.replace(s) { splitHint(it.groupValues[1]).first }

    private fun splitHint(raw: String): Pair<String, String?> {
        val i = raw.indexOf("::")
        return if (i < 0) raw to null else raw.substring(0, i) to raw.substring(i + 2).trim().ifEmpty { null }
    }
}
