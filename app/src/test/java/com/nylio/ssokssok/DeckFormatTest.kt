package com.nylio.ssokssok

import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.data.db.CardType
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.importer.DeckFormat
import com.nylio.ssokssok.importer.DeckFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckFormatTest {
    @Test
    fun parsesTabSeparatedWithDirectivesAndHeader() {
        val r = DeckFormat.parse(DeckFormat.TEXT_TEMPLATE)
        assertEquals(1, r.decks.size)
        val d = r.decks[0]
        assertEquals("Food & Drinks", d.name)
        assertEquals("밥", d.symbol)
        assertEquals(4, d.cards.size)
        assertEquals("사과", d.cards[0].korean)
        assertEquals("apple", d.cards[0].english)
        assertEquals("", d.cards[0].hanja)
        assertEquals("飮=마실 음; 食=먹을 식", d.cards[1].hanja)
        assertEquals(listOf("food", "fruit"), d.cards[0].tags)
        assertEquals("저{{는}} 학생이에요.", d.cards[3].korean)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun parsesJsonTemplate() {
        val r = DeckFormat.parse(DeckFormat.JSON_TEMPLATE)
        assertEquals(3, r.cardCount)
        assertEquals(CardType.GRAMMAR, r.decks[0].cards[1].type)
        assertEquals("I like Korean food.", r.decks[0].cards[0].exampleTranslation)
    }

    @Test
    fun pipeSeparatedMultipleDecksAndSwappedColumns() {
        val text = """
            # deck: Colors
            빨간색 | red
            blue | 파란색
            # deck: Numbers
            하나 | one
            둘 | two
            broken line
        """.trimIndent()
        val r = DeckFormat.parse(text)
        assertEquals(listOf("Colors", "Numbers"), r.decks.map { it.name })
        assertEquals("파란색", r.decks[0].cards[1].korean)
        assertEquals("blue", r.decks[0].cards[1].english)
        assertEquals(1, r.warnings.size)
    }

    @Test
    fun csvWithQuotesAndCustomHeaderOrder() {
        val text = "english,korean,notes\n\"to be good, to like\",좋다,\"a, b\"\n"
        val r = DeckFormat.parse(text, "verbs.csv")
        val c = r.decks[0].cards[0]
        assertEquals("verbs", r.decks[0].name)
        assertEquals("좋다", c.korean)
        assertEquals("to be good, to like", c.english)
        assertEquals("a, b", c.notes)
    }

    @Test
    fun ankiPlainTextExport() {
        val text = "#separator:tab\n#html:true\n사과<br>[sound:a.mp3]\tapple&nbsp;\n"
        val r = DeckFormat.parse(text, "Korean.txt")
        assertEquals("사과", r.decks[0].cards[0].korean)
        assertEquals("apple", r.decks[0].cards[0].english)
    }

    @Test
    fun guessesTypes() {
        val r = DeckFormat.parse("사과\tapple\n사과를 먹어요.\tI eat an apple.\n-고 싶다\tto want to\n")
        assertEquals(listOf(CardType.WORD, CardType.SENTENCE, CardType.GRAMMAR), r.decks[0].cards.map { it.type })
    }

    @Test(expected = DeckFormatException::class)
    fun emptyFails() {
        DeckFormat.parse("   \n")
    }

    @Test
    fun exportRoundTrips() {
        val deck = DeckEntity(id = 1, name = "Test", symbol = "테")
        val cards = listOf(
            CardEntity(deckId = 1, korean = "물", english = "water", hanja = "水=물 수", tags = "drink food", type = "word"),
        )
        val back = DeckFormat.parse(DeckFormat.toJson(listOf(deck to cards)))
        assertEquals("Test", back.decks[0].name)
        assertEquals("테", back.decks[0].symbol)
        assertEquals("water", back.decks[0].cards[0].english)
        assertEquals(listOf("drink", "food"), back.decks[0].cards[0].tags)
        assertEquals("水=물 수", back.decks[0].cards[0].hanja)
    }

    @Test
    fun deckFoldersAreKept() {
        val text = """
            # deck: 서울대 한국어+::3A::1-1 시작과 만남
            # symbol: 1-1
            신입생	freshman	新=새 신; 入=들 입; 生=날 생
        """.trimIndent()
        val d = DeckFormat.parse(text).decks.single()
        assertEquals("서울대 한국어+::3A::1-1 시작과 만남", d.name)
        assertEquals("1-1", d.symbol)
        assertEquals("新=새 신; 入=들 입; 生=날 생", d.cards[0].hanja)
    }

    @Test
    fun legacyRomanizationColumnIsSkipped() {
        val r = DeckFormat.parse("사과\tapple\tsagwa\t사과를 먹어요.\tI eat an apple.")
        val c = r.decks[0].cards[0]
        assertEquals("", c.hanja)
        assertEquals("사과를 먹어요.", c.example)
        assertEquals("I eat an apple.", c.exampleTranslation)
    }

    @Test
    fun aiPromptMentionsTopic() {
        assertTrue(DeckFormat.aiPrompt("café").contains("café"))
    }
}
