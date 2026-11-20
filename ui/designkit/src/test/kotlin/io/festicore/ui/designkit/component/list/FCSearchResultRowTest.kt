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
}
