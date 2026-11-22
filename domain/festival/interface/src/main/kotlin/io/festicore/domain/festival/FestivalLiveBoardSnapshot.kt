package io.festicore.domain.festival

import java.time.Instant

/** 축제 라이브 상태(공개 깃발 · 내 팔로우 · 크루 · 서버 시각). iOS `FestivalLiveBoardSnapshot`. */
data class FestivalLiveBoardSnapshot(
    val festivalId: String,
    val activeFlags: List<Flag>,
    val followedFlags: List<Flag>,
    val crew: Crew?,
    val serverAt: Instant? = null,
    val pinnedFlagId: String? = null,
    /** 축제의 공개 깃발 전체. 공개 목록 순서에 라이브 상태를 합친다. */
    val directoryFlags: List<Flag> = emptyList(),
    /** 이 기기가 라이브 상태를 마지막으로 확인한 시각. 304로 이전 응답을 다시 써도 새로 기록한다. */
    val receivedAt: Instant? = null,
    /** 이 축제에서 내가 기수인 깃발 ID(`me.bearerOf`). */
    val bearerFlagIds: List<String> = emptyList(),
) {
    data class Flag(
        val id: String,
        val name: String,
        /** `active` · `resting` · `ended`, 라이브 목록에 없으면 `scheduled`(목록이 상한 미만) 또는 `unknown`. */
        val status: String,
        val zoneId: String?,
        val activity: String?,
        /** https로 결합한 썸네일 주소. 없으면 null. */
        val thumbnailUrl: String?,
        val tags: List<String> = emptyList(),
        val updatedAt: Instant? = null,
        val followerCount: Int? = null,
        val homeZoneId: String? = null,
        /** 공개한 관람 계획(공연 ID). 개장 전 "도착 예정 순" 정렬에 쓴다. */
        val plannedSetIds: List<String> = emptyList(),
    )

    data class Crew(
        val id: String,
        val name: String,
        val members: List<Member>,
        val ownerId: String? = null,
        val gatherRequest: GatherRequest? = null,
        val gatherAvailableAt: Instant? = null,
    ) {
        data class Member(
            val id: String,
            val nickname: String,
            val state: String?,
            val zoneId: String?,
            val updatedAt: Instant? = null,
        )

        data class GatherRequest(
            /** 응답 API에 쓰는 모여 ID. 이전 서버 응답에는 없다. */
            val id: String? = null,
            val zoneId: String?,
            val byUserId: String,
            val at: Instant,
            /** 응답을 받을 수 있는 마지막 시각. 이전 서버 응답에는 없다. */
            val expiresAt: Instant? = null,
            /** 응답한 순서대로. */
            val responses: List<Response> = emptyList(),
            private val serverSummary: Summary? = null,
        ) {
            /** 서버 요약. 없던 이전 응답이면 [responses]로 센다. */
            val summary: Summary
                get() = serverSummary ?: Summary(
                    going = responses.count { it.response == FestivalCrewGatherResponse.GOING },
                    ack = responses.count { it.response == FestivalCrewGatherResponse.ACK },
                )

            data class Response(
                val userId: String,
                val nickname: String,
                val response: FestivalCrewGatherResponse,
                val respondedAt: Instant,
            )

            data class Summary(
                val going: Int,
                val ack: Int,
            )
        }
    }
}

/** 모여 응답. iOS `FestivalCrewGatherResponse`. */
enum class FestivalCrewGatherResponse(val rawValue: String) {
    /** 모이는 존으로 가는 중 */
    GOING("going"),

    /** 봤다는 확인 */
    ACK("ack"),
    ;

    companion object {
        fun of(rawValue: String): FestivalCrewGatherResponse? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
