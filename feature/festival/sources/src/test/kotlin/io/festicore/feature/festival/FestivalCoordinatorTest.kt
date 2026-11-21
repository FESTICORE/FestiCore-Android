package io.festicore.feature.festival

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

class FestivalCoordinatorTest {
    @Test
    fun `라이브 탭은 목록에서 시작하고 고른 축제 ID를 받아 둔다`() = runTest {
        FestivalCoordinator().test(this) {
            containerHost.onFestivalSelected("fst_1")
            expectState { copy(selectedFestivalId = "fst_1") }
        }
    }

    @Test
    fun `목록뿐이면 뒤로는 스택을 비우지 않는다`() = runTest {
        FestivalCoordinator().test(this) {
            containerHost.goBack()
        }
    }
}
