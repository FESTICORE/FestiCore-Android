package io.festicore.domain.festival.entity

/** 받은 팩과 현재 발행 팩의 차이. iOS `FestivalPackChanges`(`lastDepartures`는 iOS도 읽지 않는다). */
data class FestivalPackChanges(
    val fromVersion: Int,
    val toVersion: Int,
    /** 받을 팩 크기(바이트). */
    val sizeBytes: Int,
    /** 바뀐 공연 수. 서버에 이전 버전 기록이 없으면 null. */
    val changedSets: Int?,
    val lastShuttleAt: ValueChange? = null,
    val lastTrainAt: ValueChange? = null,
) {
    data class ValueChange(
        val before: String?,
        val after: String?,
    )
}
