package io.festicore.domain.festival

import java.time.Instant

/** 크루원 한 명의 모여 응답. 다시 응답하면 서버가 덮어쓴다. 응답 값은 [FestivalCrewGatherResponse]. iOS `FestivalCrewGatherReply`. */
data class FestivalCrewGatherReply(
    val gatherId: String,
    val userId: String,
    val nickname: String,
    val response: FestivalCrewGatherResponse,
    val respondedAt: Instant,
)
