package io.festicore.domain.bearer.entity

import java.time.Instant

/** 기수가 누른 상태 변경. iOS `BearerStatusChange`. */
sealed interface BearerStatusChange {
    /** 예정 → 활동 중 */
    data class Start(
        val zoneId: String,
        val activity: BearerActivity,
    ) : BearerStatusChange

    /** 활동 중 · 쉬는 중의 존 이동 */
    data class Move(val zoneId: String) : BearerStatusChange

    /** 활동 중의 활동 변경, 쉬는 중에서 다시 시작 */
    data class Activity(val activity: BearerActivity) : BearerStatusChange

    data object Rest : BearerStatusChange

    data object End : BearerStatusChange

    /** 오늘 종료 후 다시 시작. 서울 하루 한 번이다. */
    data class Restart(
        val zoneId: String,
        val activity: BearerActivity,
    ) : BearerStatusChange
}

/** 상태 전송 한 건. 다시 보낼 때도 같은 멱등키 · 누른 시각을 쓴다. iOS `BearerStatusRequest`. */
data class BearerStatusRequest(
    val change: BearerStatusChange,
    val idempotencyKey: String,
    val clientAt: Instant,
) {
    val id: String get() = idempotencyKey
}
