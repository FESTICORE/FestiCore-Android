package io.festicore.ui.designkit.component.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Selection 그룹의 iOS 로직(`FCTextField` 글자 수 제한 · 카운터, `FCFlowLayout.arrange`)을 같은 규칙으로 검사한다.
 * iOS `FestiCoreDesignKitTests`에는 이 그룹 대상 검사가 없어, 기대값은 iOS 소스의 동작에서 정했다.
 */
class FCSelectionLogicTest {
    @Test
    fun `최대 글자 수를 넘는 입력은 앞에서부터 자른다`() {
        assertEquals("페스티코어", limitTextLength("페스티코어입니다", maxLength = 5))
        assertEquals("페스티", limitTextLength("페스티", maxLength = 5))
        assertEquals("페스티코어입니다", limitTextLength("페스티코어입니다", maxLength = null))
    }

    @Test
    fun `글자 수는 이모지를 한 글자로 센다`() {
        val family = "👨‍👩‍👧"
        assertEquals(3, characterCount("a${family}b"))
        assertEquals("a$family", limitTextLength("a${family}b", maxLength = 2))
    }

    @Test
    fun `카운터는 직접 준 문구가 최대 글자 수 표시보다 우선한다`() {
        assertEquals("3 / 10", textFieldCounter("페스티", maxLength = 10, counter = null))
        assertEquals("직접", textFieldCounter("페스티", maxLength = 10, counter = "직접"))
        assertNull(textFieldCounter("페스티", maxLength = null, counter = null))
    }

    @Test
    fun `폭이 모자라면 다음 줄로 넘기고 줄의 첫 항목은 넘쳐도 그 줄에 둔다`() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), arrangeFlowRows(listOf(40, 50, 30), availableWidth = 100, spacing = 8))
        assertEquals(listOf(listOf(0, 1, 2)), arrangeFlowRows(listOf(30, 30, 24), availableWidth = 100, spacing = 8))
        assertEquals(listOf(listOf(0), listOf(1)), arrangeFlowRows(listOf(150, 20), availableWidth = 100, spacing = 8))
        assertEquals(emptyList<List<Int>>(), arrangeFlowRows(emptyList(), availableWidth = 100, spacing = 8))
    }
}
