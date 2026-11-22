package io.festicore.feature.timetable.main

import io.festicore.domain.timetable.TimetableDay
import io.festicore.domain.timetable.TimetableLineupDay
import io.festicore.domain.timetable.TimetableOverlap
import io.festicore.feature.timetable.TimetableRoute
import io.festicore.feature.timetable.setdetail.TimetableSetDetailState
import java.time.Instant

/** 서버 일정 동기화 상태. iOS `TimetableFeature.ScheduleSync`. */
enum class TimetableScheduleSync {
    /** 축제가 없어 이 기기에만 표시한다. */
    Idle,

    /** 서버 일정을 읽는 중이다. 이 동안에는 일정을 바꾸지 않는다. */
    Loading,

    /** 서버와 맞춰져 바꿀 때마다 저장한다. */
    Ready,

    /** 로그인 전이거나 연결이 없어 이 기기에만 표시한다. 다음 진입 때 다시 읽는다. */
    LocalOnly,
}

/** 보낸 내 일정 저장 요청. iOS `TimetableFeature.ScheduleSave`. [generation]은 보낼 때의 계정 세대다. */
data class TimetableScheduleSave(
    val setIds: List<String>,
    val clientAt: Instant,
    val generation: Int,
)

/** iOS `TimetableFeature.ContentStatus`. */
enum class TimetableContentStatus {
    Loading,
    Failed,
    NoFestival,
    Unpublished,
    Published,
}

/** 공개 전 "알림 받기" 상태. iOS `TimetableFeature.NotificationStatus`. */
enum class TimetableNotificationStatus {
    Idle,
    Saving,
    Enabled,
    PermissionRequired,
    Failed,
}

/**
 * S02 타임테이블 화면 상태. iOS `TimetableFeature.State`.
 * [overlaps]는 iOS 계산 속성이지만 계산기가 주입 대상이라 ViewModel이 바뀔 때마다 다시 채운다. 시트 내용([setDetail])도 같다(설계 T7).
 */
data class TimetableState(
    val notificationStatus: TimetableNotificationStatus = TimetableNotificationStatus.Idle,
    val contentStatus: TimetableContentStatus = TimetableContentStatus.Published,
    val days: List<TimetableDay> = emptyList(),
    /** 공개 전 상태에서 표시하는 라인업. */
    val lineup: List<TimetableLineupDay> = emptyList(),
    val selectedDayIndex: Int = 0,
    val mySetIds: Set<String> = emptySet(),
    val showsMineOnly: Boolean = false,
    /** 저장된 일정은 그대로 두고, 사용자가 일정을 바꿔 충돌을 만든 경우에만 격자 경고를 띄운다. */
    val shouldShowOverlapWarning: Boolean = false,
    /** 두 손가락으로 확정한 시간축 배율. 날짜를 바꿔도 유지한다. */
    val zoom: Double = 1.0,
    /** 선택한 날 0시부터의 현재 시각(분). 자정을 넘는 공연까지 끝나면 null. */
    val nowMinute: Int? = null,
    /** 마지막으로 반영한 현재 시각(분 단위로 자름). 1초마다 같은 분이면 상태가 바뀌지 않는다. */
    val clockAt: Instant? = null,
    /** 오프라인일 때 보여 줄 안내. 온라인이면 null. */
    val offlineMessage: String? = null,
    val source: String = "",
    val festivalId: String? = null,
    /** 상세 시트로 연 공연. */
    val setDetail: TimetableSetDetailState? = null,
    val scheduleSync: TimetableScheduleSync = TimetableScheduleSync.Idle,
    /** 60개를 넘겨 추가하지 못했다. */
    val isScheduleLimitReached: Boolean = false,
    /** 고른 날 공연이 낀 내 일정 겹침(iOS `overlaps`). */
    val overlaps: List<TimetableOverlap> = emptyList(),
    /** 겹침 경고 중 고른 날에서 다른 내 일정과 겹치는 내 공연(iOS `isOverlapping`). 격자 블록이 `Overlap`으로 그린다. */
    val overlappingSetIds: Set<String> = emptySet(),
    /** 내 일정 고르기가 위에 떠 있다(iOS `destination != nil`). 경로는 Coordinator가 가진다(설계 T8). */
    val isPickOpen: Boolean = false,
    val loadRequestId: Int = 0,
    val accountGeneration: Int = 0,
    val isSaving: Boolean = false,
    /** 저장 중에 목록이 또 바뀌었다. 끝나면 마지막 목록으로 한 번 더 저장한다. */
    val needsSave: Boolean = false,
    val lastClientAt: Instant? = null,
    /** 결과를 알 수 없는 저장. 목록이 그대로면 같은 요청으로 다시 보낸다. */
    val uncertainSave: TimetableScheduleSave? = null,
) {
    val isPublished: Boolean get() = contentStatus == TimetableContentStatus.Published

    val selectedDay: TimetableDay? get() = days.getOrNull(selectedDayIndex)

    /** 서버 일정과 맞춰져 있고 저장 중이 아닐 때만 내 일정 고르기를 연다. 로그인 전 · 읽기 실패면 다른 기기 일정을 덮어쓰지 않도록 열지 않는다. */
    val canPickSchedule: Boolean
        get() = festivalId != null && isPublished && scheduleSync == TimetableScheduleSync.Ready && !isSaving

    /** 고른 날의 내 공연 수. */
    val mySetCount: Int get() = selectedDay?.sets?.count { it.id in mySetIds } ?: 0

    val visibleOverlaps: List<TimetableOverlap> get() = if (shouldShowOverlapWarning) overlaps else emptyList()

    internal val allSetIds: Set<String> get() = days.flatMapTo(mutableSetOf()) { day -> day.sets.map { it.id } }

    companion object {
        /** 서버 일정은 최대 60개까지 저장된다. */
        const val SCHEDULE_LIMIT = 60
    }
}

/** 상위 흐름(Coordinator)이 처리할 일. iOS `TimetableDelegate`. */
sealed interface TimetableSideEffect {
    data object Search : TimetableSideEffect

    data object ShowChanges : TimetableSideEffect

    /** 공개 전 "알림 받기"(iOS `requestNotification`). 알림 설정 PR에서 처리한다. */
    data object RequestNotification : TimetableSideEffect

    /** "내 일정 N개"(iOS `schedulePickTapped`). Coordinator가 이 경로를 쌓는다. */
    data class SchedulePick(val route: TimetableRoute.SchedulePick) : TimetableSideEffect

    /** 축제 · 계정 · 팩이 바뀌어 열린 내 일정 고르기를 닫는다(iOS `destination = nil`). 결과는 돌려주지 않는다. */
    data object CloseSchedulePick : TimetableSideEffect
}
