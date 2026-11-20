package io.festicore.ui.designkit.component.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestiCoreDesignKitTests`의 페이지네이션 검사(`pagination*`)를 같은 기대값으로 옮겼다. 기본 개수는 4다. */
class FCPaginationTest {
    @Test
    fun `현재 페이지를 1부터 센다`() {
        assertEquals(listOf(true, false, false, false), fcPaginationDotStates(count = 4, current = 1))
        assertEquals(listOf(false, true, false, false), fcPaginationDotStates(count = 4, current = 2))
        assertEquals(listOf(false, false, true, false), fcPaginationDotStates(count = 4, current = 3))
        assertEquals(listOf(false, false, false, true), fcPaginationDotStates(count = 4, current = 4))
        assertEquals("페이지 2/4", fcPaginationAccessibilityText(count = 4, current = 2))
    }

    @Test
    fun `범위 밖 페이지를 보정한다`() {
        assertEquals(listOf(true, false, false, false), fcPaginationDotStates(count = 4, current = 0))
        assertEquals("페이지 1/4", fcPaginationAccessibilityText(count = 4, current = 0))
        assertEquals(listOf(false, false, false, true), fcPaginationDotStates(count = 4, current = 5))
        assertEquals("페이지 4/4", fcPaginationAccessibilityText(count = 4, current = 5))
    }

    @Test
    fun `페이지가 없으면 아무것도 그리지 않는다`() {
        assertTrue(fcPaginationDotStates(count = 0, current = 1).isEmpty())
        assertNull(fcPaginationAccessibilityText(count = 0, current = 1))
        assertTrue(fcPaginationDotStates(count = -1, current = 1).isEmpty())
        assertNull(fcPaginationAccessibilityText(count = -1, current = 1))
    }
}
