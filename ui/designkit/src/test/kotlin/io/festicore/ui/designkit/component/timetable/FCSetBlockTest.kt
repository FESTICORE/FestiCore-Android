package io.festicore.ui.designkit.component.timetable

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * iOS 테스트에는 Timetable 검사가 없다. iOS `FCSetBlock.height(minutes:)` 문서(1분 2pt, 0 이하는 0)와
 * `accessibilityValue` 구현을 기준으로 시간 → 높이 계산과 접근성 값을 확인한다.
 */
class FCSetBlockTest {
    @Test
    fun `공연 길이 1분은 2dp다`() {
        assertEquals(120.dp, fcSetBlockHeight(minutes = 60))
        assertEquals(40.dp, fcSetBlockHeight(minutes = 20))
        assertEquals(2.dp, fcSetBlockHeight(minutes = 1))
    }

    @Test
    fun `0 이하 길이는 0이다`() {
        assertEquals(0.dp, fcSetBlockHeight(minutes = 0))
        assertEquals(0.dp, fcSetBlockHeight(minutes = -10))
    }

    @Test
    fun `상태별 접근성 값`() {
        assertEquals("18:00", fcSetBlockAccessibilityValue("18:00", FCSetBlockState.Default))
        assertEquals("18:00, 내 일정", fcSetBlockAccessibilityValue("18:00", FCSetBlockState.Mine))
        assertEquals(
            "18:30, 변경 전 18:00, 30분 지연",
            fcSetBlockAccessibilityValue("18:30", FCSetBlockState.Changed(oldTime = "18:00", change = "30분 지연")),
        )
        assertEquals("18:00, 겹침", fcSetBlockAccessibilityValue("18:00", FCSetBlockState.Overlap()))
    }
}
