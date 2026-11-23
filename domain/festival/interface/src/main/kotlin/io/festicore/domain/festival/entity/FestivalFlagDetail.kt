package io.festicore.domain.festival.entity

import java.time.Instant

/**
 * 깃발 상세. 익명 조회면 [isFollowing] · [isBearer] · [isPinned]가 null이다. iOS `FestivalFlagDetail`.
 * [thumbnailUrl] · [photoUrl]은 https로 결합한 주소, [planSetIds]는 [isPlanVisible]일 때만 채운다.
 */
data class FestivalFlagDetail(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val photoUrl: String?,
    val tags: List<String>,
    val bio: String?,
    val followerCount: Int,
    val isPlanVisible: Boolean,
    val planSetIds: List<String>,
    val homeZoneId: String?,
    val status: Status,
    val zoneId: String?,
    val activity: Activity?,
    val statusUpdatedAt: Instant,
    val isFollowing: Boolean?,
    val isBearer: Boolean?,
    val isPinned: Boolean? = null,
    val isHidden: Boolean,
) {
    enum class Status(val value: String) {
        Scheduled("scheduled"),
        Active("active"),
        Resting("resting"),
        Ended("ended"),
    }

    enum class Activity(val value: String) {
        Slam("slam"),
        Sing("sing"),
        Train("train"),
        Calm("calm"),
        Resting("resting"),
    }
}

/** 깃발 상세 오류. iOS `FestivalFlagDetailError`. */
sealed class FestivalFlagDetailError : Exception() {
    /** 404 또는 경로에 넣을 수 없는 ID(요청하지 않음). */
    class NotFound : FestivalFlagDetailError()

    /** 연결 오류 · 5xx · 429처럼 결과를 알 수 없다. */
    class Unavailable : FestivalFlagDetailError()

    /** 응답이 형식에 맞지 않거나 요청과 다르다. */
    class InvalidResponse : FestivalFlagDetailError()
}
