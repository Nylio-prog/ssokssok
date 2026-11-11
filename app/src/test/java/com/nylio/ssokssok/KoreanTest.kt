package com.nylio.ssokssok

import com.nylio.ssokssok.korean.AltStyle
import com.nylio.ssokssok.korean.AnswerChecker
import com.nylio.ssokssok.korean.Cloze
import com.nylio.ssokssok.korean.DiffKind
import com.nylio.ssokssok.korean.Hangul
import com.nylio.ssokssok.korean.Verdict
import com.nylio.ssokssok.srs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanTest {
    @Test
    fun decomposesSyllables() {
        assertEquals("ㅎㅏㄴㄱㅡㄹ", Hangul.decompose("한글"))
        assertEquals("ㄱㅏㅆㅇㅓㅇㅛ", Hangul.decompose("갔어요"))
        assertEquals("abc", Hangul.decompose("abc"))
    }

    @Test
    fun exactKoreanIsCorrect() {
        val r = AnswerChecker.check("사과", "사과", korean = true)
        assertEquals(Verdict.CORRECT, r.verdict)
    }

    @Test
    fun spacingOnlyMistakeIsCorrectWithNote() {
        val r = AnswerChecker.check("만나서반가워요", "만나서 반가워요.", korean = true, style = AltStyle.SENTENCE)
        assertEquals(Verdict.CORRECT, r.verdict)
        assertTrue(r.note!!.contains("spacing"))
    }

    @Test
    fun oneJamoSlipIsClose() {
        // ㅆ typed as ㅅ: 갓어요 instead of 갔어요
        val r = AnswerChecker.check("갓어요", "갔어요", korean = true)
        assertEquals(Verdict.CLOSE, r.verdict)
        assertEquals(Rating.MEDIUM, r.suggestedRating(1_000))
        assertTrue(r.yours.any { it.kind == DiffKind.WRONG && it.text == "갓" })
        assertTrue(r.target.any { it.kind == DiffKind.MISSING && it.text == "갔" })
    }

    @Test
    fun differentWordIsWrong() {
        val r = AnswerChecker.check("물", "사과", korean = true)
        assertEquals(Verdict.WRONG, r.verdict)
        assertEquals(Rating.HARD, r.suggestedRating(1_000))
    }

    @Test
    fun englishAlternativesArticlesAndParentheses() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("Eat", "to eat", korean = false).verdict)
        assertEquals(Verdict.CORRECT, AnswerChecker.check("meal", "rice; meal", korean = false).verdict)
        assertEquals(Verdict.CORRECT, AnswerChecker.check("home", "house, home", korean = false).verdict)
        assertEquals(Verdict.CORRECT, AnswerChecker.check("watch", "to see; to watch", korean = false).verdict)
        assertEquals(Verdict.CORRECT, AnswerChecker.check("thanks for the meal", "Thanks for the meal (before eating)", korean = false, style = AltStyle.SENTENCE).verdict)
        assertEquals(Verdict.CLOSE, AnswerChecker.check("delicous", "to be delicious", korean = false).verdict)
    }

    @Test
    fun sentencesDoNotSplitOnCommas() {
        val r = AnswerChecker.check("hello", "Hello, nice to meet you.", korean = false, style = AltStyle.SENTENCE)
        assertEquals(Verdict.WRONG, r.verdict)
    }

    @Test
    fun quickCorrectAnswerSuggestsEasySlowSuggestsMedium() {
        val r = AnswerChecker.check("사과", "사과", korean = true)
        assertEquals(Rating.EASY, r.suggestedRating(2_000))
        assertEquals(Rating.MEDIUM, r.suggestedRating(30_000))
    }

    @Test
    fun emptyAnswerIsWrong() {
        assertEquals(Verdict.WRONG, AnswerChecker.check("  ", "사과", korean = true).verdict)
    }

    @Test
    fun clozeParsing() {
        val s = "저{{는::topic}} 학생{{이에요}}."
        assertTrue(Cloze.isCloze(s))
        assertEquals(listOf("는", "이에요"), Cloze.answers(s))
        assertEquals("저는 학생이에요.", Cloze.plain(s))
        assertEquals("topic", Cloze.parts(s)[1].hint)
        assertNull(Cloze.parts(s)[3].hint)
    }
}
