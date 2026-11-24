package io.festicore.feature.timetable

import io.festicore.domain.timetable.TimetableInterval
import io.festicore.domain.timetable.TimetableSetDetail
import io.festicore.domain.timetable.TimetableSetOverlap
import io.festicore.feature.timetable.setdetail.TimetableSetDetailContent
import io.festicore.feature.timetable.setdetail.scheduleHelper
import io.festicore.feature.timetable.setdetail.sheetState
import io.festicore.feature.timetable.setlist.TimetableSetListSideEffect
import io.festicore.feature.timetable.setlist.TimetableSetListState
import io.festicore.feature.timetable.setlist.TimetableSetListViewModel
import io.festicore.ui.sharedui.sheet.FCSetSheetState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** 공연 검색 · 변경 목록(iOS `setListSearchAndChangesUseActualPerformances` · `TimetableSetListFeature`)과 시트 표시 규칙(iOS `TimetableSetDetailView`). */
class TimetableSetListAndSheetTest {
    @Test
    fun setListSearchAndChangesUseActualPerformances() {
        val days = sample(SampleVariant.Changed).days
        assertEquals(
            listOf("lime"),
            TimetableSetListState(TimetableSetListMode.Search, days, query = "라임").results.flatMap {
                it.sets
            }.map { it.id },
        )
        assertEquals(2, TimetableSetListState(TimetableSetListMode.Changes, days).results.flatMap { it.sets }.size)
        // 무대 이름 · 대소문자 무시 · 앞뒤 공백 제거
        assertEquals(5, TimetableSetListState(TimetableSetListMode.Search, days, query = " 원더 ").results.flatMap { it.sets }.size)
        assertEquals(
            listOf("headliner"),
            TimetableSetListState(TimetableSetListMode.Search, days, query = "x").results.flatMap {
                it.sets
            }.map { it.id },
        )
        // 공연 없는 날은 뺀다.
        assertEquals(listOf("0807"), TimetableSetListState(TimetableSetListMode.Search, days).results.map { it.id })
    }

    @Test
    fun tappingResultSelectsSetAndBackCloses() = runTest {
        TimetableSetListViewModel(TimetableSetListMode.Changes, sample(SampleVariant.Changed).days).test(this) {
            containerHost.onSetClicked("noise")
            containerHost.onSetClicked("lime")
            expectSideEffect(TimetableSetListSideEffect.SetSelected("lime"))
            containerHost.onQueryChanged("앵콜")
            expectState { copy(query = "앵콜") }
            containerHost.onBackClicked()
            expectSideEffect(TimetableSetListSideEffect.Close)
        }
    }

    /** iOS `syncTimetable`: 같은 축제 팩이 바뀌면 목록은 검색어를 두고 날짜만 새로 받는다. */
    @Test
    fun `팩이 바뀌면 검색어를 두고 날짜만 바꾼다`() = runTest {
        val days = sample(SampleVariant.Changed).days
        TimetableSetListViewModel(TimetableSetListMode.Search, emptyList()).test(this) {
            containerHost.onQueryChanged("라임")
            expectState { copy(query = "라임") }
            containerHost.onDaysChanged(days)
            expectState { copy(days = days) }
            assertEquals(listOf("lime"), containerHost.container.stateFlow.value.results.flatMap { it.sets }.map { it.id })
        }
    }

    private fun content(
        status: TimetableSetDetail.Status = TimetableSetDetail.Status.Upcoming(30),
        overlap: TimetableSetOverlap? = null,
        previousTime: String? = null,
        change: String? = null,
        isScheduleLoading: Boolean = false,
        isScheduleLimitReached: Boolean = false,
    ) = TimetableSetDetailContent(
        detail = TimetableSetDetail("라임", "리벳 스테이지", "17:00–17:50 · 50분", status, false, overlap, previousTime, change),
        interval = TimetableInterval(FIRST_DAY, FIRST_DAY.plusSeconds(3000)),
        source = "",
        isScheduleLoading = isScheduleLoading,
        isScheduleLimitReached = isScheduleLimitReached,
    )

    @Test
    fun sheetStatePrefersEndedThenOverlapThenChange() {
        val overlap = TimetableSetOverlap("크라잉버드", "원더 스테이지", "15:55–16:45", 35)
        assertEquals(FCSetSheetState.Ended, sheetState(content(TimetableSetDetail.Status.Ended, overlap, "16:40", "20분 미뤄짐").detail))
        assertEquals(
            FCSetSheetState.Overlap(overlap.warning),
            sheetState(content(overlap = overlap, previousTime = "16:40", change = "20분 미뤄짐").detail),
        )
        assertEquals(FCSetSheetState.Changed("16:40", "20분 미뤄짐"), sheetState(content(previousTime = "16:40", change = "20분 미뤄짐").detail))
        assertEquals(FCSetSheetState.Default, sheetState(content().detail))
    }

    @Test
    fun scheduleHelperExplainsLoadingAndLimit() {
        assertEquals("내 일정을 불러오는 중이에요", scheduleHelper(content(isScheduleLoading = true, isScheduleLimitReached = true)))
        assertEquals("내 일정은 최대 60개까지 저장할 수 있어요", scheduleHelper(content(isScheduleLimitReached = true)))
        assertEquals("공연 10분 전에 알려 드려요", scheduleHelper(content()))
    }
}
