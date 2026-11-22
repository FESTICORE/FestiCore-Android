package io.festicore.feature.timetable.setdetail

import io.festicore.domain.timetable.TimetableInterval
import io.festicore.domain.timetable.TimetableSetDetail
import java.time.Instant

/**
 * 공연 상세 시트(S03) 상태. iOS `TimetableSetDetailFeature.State`.
 * 내용은 타임테이블 ViewModel이 계산해 넣고(설계 T7), 시트는 받은 내용을 보여 준다.
 */
data class TimetableSetDetailState(
    val setId: String,
    val content: TimetableSetDetailContent,
    /** 내 일정에 넣은 직후 시트 위에 보이는 "내 일정에 추가됐어요" 토스트. */
    val isScheduleToastVisible: Boolean = false,
) {
    /** 열린 동안 공연 시작 · 종료 시각이 지나면 상태가 바뀌므로 화면이 현재 시각을 넘긴다. */
    fun detail(now: Instant): TimetableSetDetail = content.detail.copy(status = TimetableSetDetail.Status.of(content.interval, now))
}

/** 타임테이블이 계산해 넘기는 시트 내용. iOS `TimetableSetDetailFeature.Content`. */
data class TimetableSetDetailContent(
    val detail: TimetableSetDetail,
    /** 공연의 실제 시작 · 종료 시각. */
    val interval: TimetableInterval,
    val source: String,
    /** 서버 일정을 읽는 중이다. 이 동안에는 내 일정을 바꾸지 않는다. */
    val isScheduleLoading: Boolean,
    /** 60개를 넘겨 추가하지 못했다. */
    val isScheduleLimitReached: Boolean,
)
