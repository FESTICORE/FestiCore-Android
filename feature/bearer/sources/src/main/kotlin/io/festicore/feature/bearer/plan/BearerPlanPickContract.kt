package io.festicore.feature.bearer.plan

import io.festicore.domain.bearer.entity.BearerSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * iOS `BearerPlanPickFeature.State`. 받아 둔 팩의 공연을 서울 날짜별로 묶어 보여 준다.
 *
 * @param selectedSetIds 고른 공연 ID. 순서는 [orderedPlan]이 정한다.
 */
data class BearerPlanPickState(
    val days: List<BearerPlanDay>,
    val selectedDayIndex: Int = 0,
    val selectedSetIds: Set<String> = emptySet(),
    val limitMessage: String? = null,
) {
    val selectedDay: BearerPlanDay? get() = days.getOrNull(selectedDayIndex)

    /** 고른 공연을 시작 순으로 돌려준다. 팩에 없는 예전 셋은 그대로 뒤에 둔다. */
    val orderedPlan: List<String>
        get() {
            val known = days.flatMap { it.sets }.map { it.id }.filter { it in selectedSetIds }
            val unknown = (selectedSetIds - known.toSet()).sorted()
            return known + unknown
        }

    companion object {
        /** 서버 plan 최대 개수 */
        const val PLAN_LIMIT = 30

        /** - [plan]: 이미 고른 오늘 계획. 첫 화면은 그 공연이 있는 날로 연다. iOS `State.init(sets:plan:)`. */
        fun initial(sets: List<BearerSet>, plan: List<String>): BearerPlanPickState {
            val days = days(sets)
            val selected = plan.toSet()
            val plannedDay = days.indexOfFirst { day -> day.sets.any { it.id in selected } }
            return BearerPlanPickState(days = days, selectedDayIndex = maxOf(0, plannedDay), selectedSetIds = selected)
        }

        /** 공연을 서울 기준 시작 날짜로 묶는다. */
        internal fun days(sets: List<BearerSet>): List<BearerPlanDay> = sets
            .groupBy { it.startsAt.atZone(BearerPlanFormat.zone).toLocalDate() }
            .toSortedMap()
            .map { (date, daySets) -> BearerPlanDay(date = date, sets = daySets.sortedBy { it.startsAt }) }
    }
}

/** iOS `BearerPlanPickFeature.Day`. */
data class BearerPlanDay(
    val date: LocalDate,
    val sets: List<BearerSet>,
) {
    val title: String get() = BearerPlanFormat.festivalDay(date)
}

/** iOS `BearerPlanPickFeature.Delegate`. */
sealed interface BearerPlanPickSideEffect {
    /** 고른 공연(시작 순). 부모가 프로필에 넣는다(`BearerProfileViewModel.onPlanChanged`). */
    data class Picked(val setIds: List<String>) : BearerPlanPickSideEffect

    data object CloseRequested : BearerPlanPickSideEffect
}

/** iOS `AppDateFormat.festivalDay`(`M/d(E)`, ko_KR) · `.time`(`HH:mm`, en_US_POSIX), 서울 시간대. feature는 core를 의존하지 않아 같은 패턴을 둔다. */
internal object BearerPlanFormat {
    val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val festivalDayFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone)

    fun festivalDay(date: LocalDate): String = festivalDayFormatter.format(date)

    fun time(instant: Instant): String = timeFormatter.format(instant)
}
