package io.festicore.domain.festival.sources.model.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * `GET api/v1/festivals/{id}/live` 응답 `data`. iOS `FestivalLiveBoardDataDTO`.
 * 서버의 `setOverrides`는 iOS에 없어 받지 않는다(`ignoreUnknownKeys`). 비로그인이면 [me]가 null.
 */
@Serializable
data class FestivalLiveBoardDto(
    val festivalId: String,
    val activeFlags: List<FestivalLiveFlagDto>,
    val me: FestivalLiveMeDto? = null,
    val serverAt: String? = null,
)

@Serializable
data class FestivalLiveFlagDto(
    val flagId: String,
    val status: String,
    val zoneId: String? = null,
    val activity: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class FestivalLiveMeDto(
    val following: List<String>,
    val crew: FestivalLiveCrewDto? = null,
    val pinnedFlagId: String? = null,
    /** 이 축제에서 내가 기수인 깃발 ID. */
    val bearerOf: List<String>? = null,
)

@Serializable
data class FestivalLiveCrewDto(
    val id: String,
    val name: String,
    val members: List<FestivalLiveCrewMemberDto>,
    val ownerId: String? = null,
    val gatherRequest: FestivalLiveGatherRequestDto? = null,
    val gatherAvailableAt: String? = null,
)

@Serializable
data class FestivalLiveCrewMemberDto(
    val userId: String,
    val nickname: String,
    val state: String? = null,
    val zoneId: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class FestivalLiveGatherRequestDto(
    val id: String? = null,
    val zoneId: String? = null,
    val byUserId: String,
    val at: String,
    val expiresAt: String? = null,
    val responses: List<Response>? = null,
    val responseSummary: Summary? = null,
) {
    @Serializable
    data class Response(
        val userId: String,
        val nickname: String,
        val response: String,
        val respondedAt: String,
    )

    @Serializable
    data class Summary(
        val going: Int,
        val ack: Int,
    )
}

/** `GET api/v1/festivals/{id}/flags` 응답 `data`. iOS `FestivalFlagsDataDTO`. */
@Serializable
data class FestivalFlagsDto(val items: List<FestivalFlagProfileDto>)

/**
 * 공개 깃발 프로필. iOS가 읽지 않는 `photoUrl` · `bio` · `planVisible`은 받지 않는다.
 * [tags]는 문자열 아닌 원소를 버리고, [plan]은 형식이 틀리면 null.
 */
@Serializable
data class FestivalFlagProfileDto(
    val id: String,
    val name: String,
    val thumbUrl: String? = null,
    @Serializable(with = LossyStringListSerializer::class)
    val tags: List<String>? = null,
    val followerCount: Int? = null,
    val homeZoneId: String? = null,
    /** 공개한 관람 계획(공연 ID). 계획을 숨긴 깃발은 빈 배열. */
    @Serializable(with = StringListOrNullSerializer::class)
    val plan: List<String>? = null,
)

object LossyStringListSerializer : LossyListSerializer<String>(String.serializer())

object StringListOrNullSerializer : OrNullSerializer<List<String>>(ListSerializer(String.serializer()))
