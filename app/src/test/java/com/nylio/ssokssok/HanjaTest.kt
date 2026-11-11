package com.nylio.ssokssok

import com.nylio.ssokssok.data.db.DeckPath
import com.nylio.ssokssok.korean.Hanja
import com.nylio.ssokssok.korean.HanjaPart
import com.nylio.ssokssok.ui.home.DeckTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HanjaTest {
    @Test
    fun parsesCharacterMeaningAndReading() {
        assertEquals(
            listOf(HanjaPart("新", "새", "신"), HanjaPart("入", "들", "입"), HanjaPart("生", "날", "생")),
            Hanja.parse("新=새 신; 入=들 입; 生=날 생"),
        )
    }

    @Test
    fun characterAndEnglishAreOptional() {
        assertEquals(listOf(HanjaPart("", "눈", "설")), Hanja.parse("눈 설"))
        assertEquals(listOf(HanjaPart("學", "배울", "학", "study")), Hanja.parse("學 배울 학 (study)"))
        assertEquals(listOf(HanjaPart("", "장려할", "장"), HanjaPart("", "배울", "학")), Hanja.parse("장려할 장 · 배울 학"))
        assertTrue(Hanja.parse("  ").isEmpty())
        assertEquals(
            listOf(HanjaPart("生", "날", "생", "life; student"), HanjaPart("會", "모일", "회", "meet, gathering")),
            Hanja.parse("生=날 생 (life; student); 會=모일 회 (meet, gathering)"),
        )
    }

    @Test
    fun deckPaths() {
        val p = DeckPath("서울대 한국어+::3A::1-1 시작과 만남")
        assertEquals("1-1 시작과 만남", p.leaf)
        assertEquals("서울대 한국어+::3A", p.folder)
        assertEquals("서울대 한국어+::3A", DeckPath.normalizeFolder("서울대 한국어+ › 3A"))
        assertEquals("A::B::Deck", DeckPath.join("A / B", "Deck"))
        assertEquals("Deck", DeckPath.join("", "Deck"))
    }

    @Test
    fun naturalOrder() {
        val sorted = listOf("1-10 x", "1-2 y", "2-1 z", "1-1 w").sortedWith { a, b -> DeckTree.naturalCompare(a, b) }
        assertEquals(listOf("1-1 w", "1-2 y", "1-10 x", "2-1 z"), sorted)
    }
}
