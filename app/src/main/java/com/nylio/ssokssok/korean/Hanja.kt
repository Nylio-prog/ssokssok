package com.nylio.ssokssok.korean

/**
 * One Sino-Korean syllable: its character, Korean meaning word (훈) and reading (음),
 * as in 雪 = 눈 설. An English gloss is optional.
 */
data class HanjaPart(val char: String, val hun: String, val eum: String, val english: String = "") {
    /** "눈 설", the way the character is named in Korean. */
    val name: String get() = listOf(hun, eum).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Parses the card's hanja field. Parts are separated by ";" (or "·" / ","), each written
 * "雪=눈 설", "雪 눈 설", "눈 설" or "雪=눈 설 (snow)".
 */
object Hanja {
    private val ENGLISH = Regex("\\(([^)]*)\\)\\s*$")

    fun isHan(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    fun parse(field: String): List<HanjaPart> = splitParts(field).mapNotNull { parsePart(it) }

    /** Splits on ; · , but not inside brackets, so glosses like "(life; student)" stay whole. */
    private fun splitParts(field: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (c in field) {
            when {
                c == '(' -> { depth++; sb.append(c) }
                c == ')' -> { depth = (depth - 1).coerceAtLeast(0); sb.append(c) }
                depth == 0 && (c == ';' || c == '·' || c == ',') -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
        }
        out += sb.toString()
        return out
    }

    private fun parsePart(raw: String): HanjaPart? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        var english = ""
        ENGLISH.find(s)?.let { m ->
            english = m.groupValues[1].trim()
            s = s.substring(0, m.range.first).trim()
        }
        val char = s.takeWhile { isHan(it) }
        s = s.drop(char.length).trim().trimStart('=', ':').trim()
        if (s.isEmpty()) return if (char.isNotEmpty()) HanjaPart(char, "", "", english) else null
        val words = s.split(Regex("\\s+"))
        val last = words.last()
        // The reading is the final single syllable ("배울 학"); otherwise treat it all as the meaning.
        return if (words.size > 1 && last.length == 1 && Hangul.isSyllable(last[0])) {
            HanjaPart(char, words.dropLast(1).joinToString(" "), last, english)
        } else if (words.size == 1 && last.length == 1 && Hangul.isSyllable(last[0])) {
            HanjaPart(char, "", last, english)
        } else {
            HanjaPart(char, s, "", english)
        }
    }

    /** Text to search other cards with: the character if known, else "훈 음". */
    fun searchKey(part: HanjaPart): String = part.char.ifEmpty { part.name }
}
