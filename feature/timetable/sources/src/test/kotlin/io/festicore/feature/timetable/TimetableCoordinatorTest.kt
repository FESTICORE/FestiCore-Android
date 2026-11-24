package io.festicore.feature.timetable

import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `TimetableRoutingTests`의 Coordinator · 탭 바 사례(`searchPushHidesTabBarAndBackRestoresIt` · `routedSchedulePickButtonPushesAndLaterPops` ·
 * `schedulePickIsPushRoute` · `popDismissesSchedulePick` · `ignoresStaleSchedulePickAction`).
 * 보드 입력 사례 2건은 `TimetableViewModelTest`, 매퍼 4건은 `domain/timetable`이 맡는다. `forwardsScheduleSaved`는 저장 이벤트 공유(`notifyMyScheduleSaved`)라
 * `TimetableScheduleSyncTest`가 맡는다.
 */
class TimetableCoordinatorTest {
    private val pick = TimetableRoute.SchedulePick(
        festivalId = "festival",
        selectedSetIds = emptyList(),
        previousClientAtEpochMillis = null,
    )
    private val onPick = TimetableCoordinatorState(routes = listOf(TimetableRoute.Timetable, pick))
    private val search = TimetableRoute.SetList(TimetableSetListMode.Search, festivalId = "festival")

    @Test
    fun searchPushHidesTabBarAndBackRestoresIt() = runTest {
        TimetableCoordinator().test(this) {
            containerHost.onSearch("festival")
            expectState { copy(routes = listOf(TimetableRoute.Timetable, search)) }
            assertTrue(containerHost.container.stateFlow.value.hidesTabBar(isSetDetailPresented = false))
            containerHost.onSetListClosed()
            expectState { copy(routes = listOf(TimetableRoute.Timetable)) }
            assertFalse(containerHost.container.stateFlow.value.hidesTabBar(isSetDetailPresented = false))
        }
    }

    @Test
    fun `시간표 변경 배너는 변경 목록을 쌓고 시스템 뒤로는 pop한다`() = runTest {
        TimetableCoordinator().test(this) {
            containerHost.onShowChanges("festival")
            expectState {
                copy(routes = listOf(TimetableRoute.Timetable, TimetableRoute.SetList(TimetableSetListMode.Changes, "festival")))
            }
            containerHost.goBack()
            expectState { copy(routes = listOf(TimetableRoute.Timetable)) }
            // 타임테이블뿐이면 스택을 비우지 않는다.
            containerHost.goBack()
        }
    }

    @Test
    fun `검색 결과를 고르면 목록을 닫고 그 공연을 대기 값으로 남긴다`() = runTest {
        TimetableCoordinator().test(this, initialState = TimetableCoordinatorState(routes = listOf(TimetableRoute.Timetable, search))) {
            containerHost.onSetListSetSelected("set_a")
            expectState { copy(routes = listOf(TimetableRoute.Timetable), pendingSet = TimetablePendingSet("festival", "set_a")) }
            assertEquals("set_a", containerHost.container.stateFlow.value.pendingSetId("festival"))
            containerHost.onPendingSetConsumed()
            expectState { copy(pendingSet = null) }
        }
    }

    @Test
    fun schedulePickIsPushRoute() = runTest {
        TimetableCoordinator().test(this) {
            containerHost.onSchedulePick(pick)
            expectState { copy(routes = listOf(TimetableRoute.Timetable, pick)) }
            assertTrue(containerHost.container.stateFlow.value.hidesTabBar(isSetDetailPresented = false))
        }
    }

    @Test
    fun routedSchedulePickButtonPushesAndLaterPops() = runTest {
        TimetableCoordinator().test(this, initialState = onPick) {
            containerHost.onSchedulePickFinished(SchedulePickResult.Later)
            expectState { copy(routes = listOf(TimetableRoute.Timetable), pendingPickResult = SchedulePickResult.Later) }
            assertFalse(containerHost.container.stateFlow.value.hidesTabBar(isSetDetailPresented = false))
            containerHost.onPendingPickResultConsumed()
            expectState { copy(pendingPickResult = null) }
        }
    }

    @Test
    fun popDismissesSchedulePick() = runTest {
        TimetableCoordinator().test(this, initialState = onPick) {
            containerHost.goBack()
            expectState { copy(routes = listOf(TimetableRoute.Timetable), pendingPickResult = SchedulePickResult.Skipped) }
        }
    }

    @Test
    fun ignoresStaleSchedulePickAction() = runTest {
        TimetableCoordinator().test(this) {
            containerHost.onSchedulePickFinished(SchedulePickResult.Later)
            // 상태가 바뀌지 않는다.
        }
    }

    @Test
    fun `타임테이블이 고르기를 닫으면 결과 없이 pop한다`() = runTest {
        TimetableCoordinator().test(this, initialState = onPick) {
            containerHost.onCloseSchedulePick()
            expectState { copy(routes = listOf(TimetableRoute.Timetable)) }
        }
    }

    @Test
    fun `고르기가 열려 있으면 검색 · 변경 목록을 열지 않는다`() = runTest {
        TimetableCoordinator().test(this, initialState = onPick) {
            containerHost.onSearch("festival")
            containerHost.onShowChanges("festival")
        }
    }

    @Test
    fun `공연 시트가 떠 있으면 타임테이블 루트에서도 탭 바를 숨긴다`() {
        assertTrue(TimetableCoordinatorState().hidesTabBar(isSetDetailPresented = true))
        assertFalse(TimetableCoordinatorState().hidesTabBar(isSetDetailPresented = false))
    }

    @Test
    fun `다른 축제로 바뀌면 검색 목록을 닫고 처음 받은 축제와 같은 축제는 그대로 둔다`() = runTest {
        TimetableCoordinator().test(this, initialState = TimetableCoordinatorState(routes = listOf(TimetableRoute.Timetable, search))) {
            containerHost.onFestivalChanged("festival")
            containerHost.onFestivalChanged("festival")
            containerHost.onFestivalChanged("other")
            expectState { copy(routes = listOf(TimetableRoute.Timetable)) }
        }
    }

    /** Codex TT-01: 축제가 바뀐 직후 이전 축제 목록에서 고른 공연은 같은 ID가 있어도 지금 축제 시트로 열지 않는다. */
    @Test
    fun `이전 축제 목록에서 고른 공연은 지금 축제에서 열지 않는다`() = runTest {
        TimetableCoordinator().test(this, initialState = TimetableCoordinatorState(routes = listOf(TimetableRoute.Timetable, search))) {
            containerHost.onSetListSetSelected("set_a")
            expectState { copy(routes = listOf(TimetableRoute.Timetable), pendingSet = TimetablePendingSet("festival", "set_a")) }
            val state = containerHost.container.stateFlow.value
            assertNull(state.pendingSetId("other"))
            assertNull(state.pendingSetId(null))
        }
    }

    @Test
    fun `목록이 없으면 고른 공연을 남기지 않는다`() = runTest {
        TimetableCoordinator().test(this) {
            containerHost.onSetListSetSelected("set_a")
        }
    }

    @Test
    fun `push · pop은 다른 탭과 같은 350ms FastOutSlowIn이다`() {
        assertEquals(350, TimetableTransitions.DURATION_MILLIS)
        assertEquals(FastOutSlowInEasing, TimetableTransitions.easing)
    }
}
