package io.festicore.feature.timetable.schedulepick

import io.festicore.domain.timetable.TimetablePickOverlap
import io.festicore.domain.timetable.entity.TimetableDay
import io.festicore.domain.timetable.entity.TimetableSet
import io.festicore.feature.timetable.SchedulePickResult
import java.time.Instant

/**
 * S15-1 내 일정 고르기 상태. iOS `SchedulePickFeature.State`.
 * [overlaps]는 iOS `overlap(of:)` 계산 속성이지만 계산기가 주입 대상이라 ViewModel이 바뀔 때마다 고른 날 기준으로 다시 채운다.
 */
data class SchedulePickState(
    val festivalId: String,
    val days: List<TimetableDay> = emptyList(),
    val selectedDayIndex: Int = 0,
    /** 고른 공연. 이미 저장된 내 일정으로 시작한다. */
    val selectedSetIds: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    /** 부르는 쪽이 마지막으로 저장한 요청 시각. 이번 저장은 이보다 늦게 보낸다. */
    val previousClientAt: Instant? = null,
    /** 고른 날 고른 공연 ID → 같은 날 먼저(또는 같은 시각에) 시작한 고른 공연 중 가장 많이 겹친 공연. */
    val overlaps: Map<String, TimetablePickOverlap> = emptyMap(),
) {
    val selectedDay: TimetableDay? get() = days.getOrNull(selectedDayIndex)

    val canSubmit: Boolean get() = !isSaving && selectedSetIds.isNotEmpty()

    /** 고른 날 공연을 시작 시각순으로. */
    val sortedSets: List<TimetableSet> get() = selectedDay?.sets?.sortedBy { it.start }.orEmpty()
}

/** 부르는 쪽(타임테이블 · 준비 안내)에 돌려줄 결과. iOS `SchedulePickDelegate`. */
sealed interface SchedulePickSideEffect {
    data class Finished(val result: SchedulePickResult) : SchedulePickSideEffect
}

/** 테스트가 확인하는 표시 문자열 규칙(iOS `SchedulePickView` 확장). */
internal object SchedulePickFormat {
    private const val HANGUL_FIRST = 0xAC00
    private const val HANGUL_LAST = 0xD7A3
    private const val FINAL_CONSONANTS = 28
    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24

    /** 그날 0시부터의 분을 "HH:mm"으로 쓴다. 자정을 넘긴 시각은 다음 날 시각으로 쓴다. */
    fun clock(minute: Int): String = "%02d:%02d".format((minute / MINUTES_PER_HOUR) % HOURS_PER_DAY, minute % MINUTES_PER_HOUR)

    /** 받침이 없는 한글로 끝나면 "와", 그 밖에는(한글이 아니어도) "과"를 붙인다. */
    fun withParticle(word: String): String {
        val last = word.lastOrNull()?.code ?: return "과"
        if (last !in HANGUL_FIRST..HANGUL_LAST) return "과"
        return if ((last - HANGUL_FIRST) % FINAL_CONSONANTS == 0) "와" else "과"
    }

    fun overlapText(overlap: TimetablePickOverlap): String = "${overlap.artist}${withParticle(overlap.artist)} ${overlap.minutes}분 겹쳐요"
}
