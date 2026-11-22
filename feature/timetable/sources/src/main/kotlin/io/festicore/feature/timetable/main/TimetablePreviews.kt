package io.festicore.feature.timetable.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.timetable.TimetableDay
import io.festicore.domain.timetable.TimetableLineupArtist
import io.festicore.domain.timetable.TimetableLineupDay
import io.festicore.domain.timetable.TimetableOverlap
import io.festicore.domain.timetable.TimetablePickOverlap
import io.festicore.domain.timetable.TimetableSet
import io.festicore.domain.timetable.TimetableStage
import io.festicore.feature.timetable.TimetableSetListMode
import io.festicore.feature.timetable.schedulepick.SchedulePickContent
import io.festicore.feature.timetable.schedulepick.SchedulePickState
import io.festicore.feature.timetable.setlist.TimetableSetListContent
import io.festicore.feature.timetable.setlist.TimetableSetListState
import java.time.Instant

// 프리뷰 가짜 값(Figma S02와 같은 구성). 실제 서버 · 팩을 읽지 않는다.
private val PreviewStages = listOf(TimetableStage("rivet", "리벳 스테이지"), TimetableStage("wonder", "원더 스테이지"))
private val PreviewSets =
    listOf(
        TimetableSet("noise", "노이즈캔디", "rivet", 780, 820),
        TimetableSet("smoking", "스모킹구스", "rivet", 880, 928),
        TimetableSet("noise-returns", "노이즈캔디 리턴즈", "rivet", 940, 990),
        TimetableSet("lime", "라임플래그스", "rivet", 1020, 1070, previousStart = 1000),
        TimetableSet("rookie", "루키 쇼케이스", "wonder", 720, 760),
        TimetableSet("cryingbird", "크라잉버드", "wonder", 955, 1005),
        TimetableSet("encore", "앵콜밴드", "wonder", 1090, 1140),
    )
private val PreviewDays =
    listOf("0807" to "8/7(금)", "0808" to "8/8(토)", "0809" to "8/9(일)").mapIndexed { index, (id, title) ->
        TimetableDay(
            id = id,
            title = title,
            date = Instant.parse("2026-08-06T15:00:00Z").plusSeconds(index * 86_400L),
            stages = PreviewStages,
            sets = if (index == 0) PreviewSets else emptyList(),
        )
    }
private val PreviewState =
    TimetableState(
        days = PreviewDays,
        mySetIds = setOf("noise", "noise-returns", "cryingbird"),
        nowMinute = 928,
        source = "출처: 주최측 공지 · 확인 10/16 14:00",
        festivalId = "preview",
        shouldShowOverlapWarning = true,
        overlaps = listOf(TimetableOverlap(955, 990)),
        overlappingSetIds = setOf("noise-returns", "cryingbird"),
    )

@Composable
private fun Preview(state: TimetableState) {
    TimetableContent(
        state = state,
        onSearchClicked = {},
        onDaySelected = {},
        onRetryClicked = {},
        onNotifyClicked = {},
        onMineOnlyToggled = {},
        onSchedulePickClicked = {},
        onChangeBannerClicked = {},
        onSetClicked = {},
        onZoomChanged = {},
        onScheduleToggled = {},
        onSetDetailDismissed = {},
    )
}

@Preview(name = "격자", heightDp = 900)
@Composable
private fun PublishedPreview() = Preview(PreviewState)

@Preview(name = "오프라인 · 내 일정만", heightDp = 900)
@Composable
private fun OfflinePreview() = Preview(PreviewState.copy(offlineMessage = "오프라인 · 3분 전 정보", showsMineOnly = true))

@Preview(name = "날짜 칩 줄(4일 이상)", heightDp = 900)
@Composable
private fun ManyDaysPreview() =
    Preview(PreviewState.copy(days = PreviewDays + PreviewDays.map { it.copy(id = it.id + "b", title = it.title + "b") }))

@Preview(name = "공개 전", heightDp = 900)
@Composable
private fun UnpublishedPreview() = Preview(
    TimetableState(
        contentStatus = TimetableContentStatus.Unpublished,
        days = PreviewDays.map { it.copy(sets = emptyList()) },
        lineup = listOf(TimetableLineupDay("preview", "공개된 라인업", listOf("노이즈캔디", "스모킹구스", "크라잉버드").map { TimetableLineupArtist(it) })),
    ),
)

@Preview(name = "불러오는 중")
@Composable
private fun LoadingPreview() = Preview(TimetableState(contentStatus = TimetableContentStatus.Loading))

@Preview(name = "실패")
@Composable
private fun FailedPreview() = Preview(TimetableState(contentStatus = TimetableContentStatus.Failed))

@Preview(name = "축제 없음")
@Composable
private fun NoFestivalPreview() = Preview(TimetableState(contentStatus = TimetableContentStatus.NoFestival))

@Preview(name = "공연 없음")
@Composable
private fun EmptyPreview() = Preview(TimetableState())

@Preview(name = "공연 검색", heightDp = 700)
@Composable
private fun SetListSearchPreview() = TimetableSetListContent(
    state = TimetableSetListState(TimetableSetListMode.Search, PreviewDays, query = "노이즈"),
    onQueryChanged = {},
    onSetClicked = {},
    onBackClicked = {},
)

@Preview(name = "시간표 변경 없음", heightDp = 700)
@Composable
private fun SetListChangesEmptyPreview() = TimetableSetListContent(
    state = TimetableSetListState(TimetableSetListMode.Changes, emptyList()),
    onQueryChanged = {},
    onSetClicked = {},
    onBackClicked = {},
)

@Preview(name = "내 일정 고르기", heightDp = 900)
@Composable
private fun SchedulePickPreview() = SchedulePickContent(
    state =
        SchedulePickState(
            festivalId = "preview",
            days = PreviewDays,
            selectedSetIds = setOf("noise-returns", "cryingbird"),
            overlaps = mapOf("cryingbird" to TimetablePickOverlap("노이즈캔디 리턴즈", 35)),
        ),
    onDayTapped = {},
    onSetTapped = {},
    onNextTapped = {},
    onSkipTapped = {},
    onLaterTapped = {},
)

@Preview(name = "내 일정 고르기 · 빈 날 · 오류", heightDp = 700)
@Composable
private fun SchedulePickEmptyPreview() = SchedulePickContent(
    state =
        SchedulePickState(
            festivalId = "preview",
            days = PreviewDays,
            selectedDayIndex = 1,
            errorMessage = "내 일정을 저장하지 못했어요. 다시 시도해 주세요.",
        ),
    onDayTapped = {},
    onSetTapped = {},
    onNextTapped = {},
    onSkipTapped = {},
    onLaterTapped = {},
)
