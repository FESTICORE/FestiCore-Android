package io.festicore.ui.designkit.component.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS에는 대응 검사가 없다. 검색어 강조 구간 계산(iOS `highlightedTitle`)의 일치 규칙을 확인한다. */
class FCSearchResultRowTest {
    @Test
    fun `빈 검색어는 강조하지 않는다`() {
        assertTrue(fcSearchHighlightRanges("실리카겔", "").isEmpty())
    }

    @Test
    fun `겹치지 않는 일치 구간을 모두 찾는다`() {
        assertEquals(listOf(0..1, 4..5), fcSearchHighlightRanges("락락 페락락", "락락"))
    }

    @Test
    fun `대소문자 · 발음 기호 · 전각을 무시한다`() {
        assertEquals(listOf(0..3), fcSearchHighlightRanges("Café Bar", "CAFE"))
        assertEquals(listOf(4..6), fcSearchHighlightRanges("Day ＡＢＣ", "abc"))
    }

    @Test
    fun `합성형과 분해형 제목 · 질의를 서로 찾는다`() {
        val composed = "Caf\u00E9"
        val decomposed = "Cafe\u0301"
        assertEquals(listOf(0..3), fcSearchHighlightRanges(composed, "cafe"))
        assertEquals(listOf(0..3), fcSearchHighlightRanges(composed, decomposed))
        assertEquals(listOf(0..4), fcSearchHighlightRanges(decomposed, "caf\u00E9"))
        assertEquals(listOf(0..4), fcSearchHighlightRanges(decomposed, decomposed))
        assertEquals(listOf(0..4), fcSearchHighlightRanges(decomposed, "CAFE"))
    }

    @Test
    fun `분해형 원문 범위는 결합 문자까지 포함한다`() {
        assertEquals(listOf(4..8), fcSearchHighlightRanges("Bar Cafe\u0301 Bar", "cafe"))
        assertEquals(listOf(0..1, 2..2, 4..5), fcSearchHighlightRanges("e\u0301e e\u0301", "e"))
    }

    @Test
    fun `서로게이트 쌍을 깨지 않는다`() {
        val title = "\uD83C\uDFB5 Café"
        assertEquals(listOf(0..1), fcSearchHighlightRanges(title, "\uD83C\uDFB5"))
        assertEquals(listOf(3..6), fcSearchHighlightRanges(title, "cafe"))
    }

    @Test
    fun `한글은 그대로 비교한다`() {
        assertEquals(listOf(0..1), fcSearchHighlightRanges("실리카겔", "실리"))
        assertTrue(fcSearchHighlightRanges("실리카겔", "ㅅ").isEmpty())
    }
}
