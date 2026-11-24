package io.festicore.domain.bearer.entity

import java.time.Instant

/** 받아 둔 팩에서 읽은 축제 이름 · 존 · 공연. 팩이 없으면 이름 null · 빈 목록이다. iOS `BearerFestival`. */
data class BearerFestival(
    val id: String,
    val name: String?,
    val zones: List<BearerZone>,
    /** 오늘 계획으로 고를 수 있는 공연(시작 순) */
    val sets: List<BearerSet> = emptyList(),
) {
    fun zoneName(zoneId: String?): String? = zoneId?.let { id -> zones.firstOrNull { it.id == id }?.name }
}

data class BearerZone(
    val id: String,
    val name: String,
)

data class BearerSet(
    val id: String,
    val artist: String,
    val stageName: String?,
    val startsAt: Instant,
    val endsAt: Instant,
)
