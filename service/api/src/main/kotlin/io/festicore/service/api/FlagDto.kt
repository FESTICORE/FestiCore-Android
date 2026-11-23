package io.festicore.service.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * `GET api/v1/flags/{id}` 응답 data. 익명 조회면 [isFollowing] · [isBearer] · [isPinned]가 null이다.
 * [tags] · [plan]의 null 원소는 매핑에서 버린다. iOS `FestivalFlagDetailDataDTO`.
 */
@Serializable
data class FlagDetailDto(
    val id: String,
    val name: String,
    val thumbUrl: String? = null,
    val photoUrl: String? = null,
    val tags: List<String?>,
    val bio: String? = null,
    val followerCount: Int,
    val planVisible: Boolean,
    val plan: List<String?>,
    val homeZoneId: String? = null,
    val status: FlagStatusDto,
    val isFollowing: Boolean? = null,
    val isBearer: Boolean? = null,
    val isPinned: Boolean? = null,
    val hidden: Boolean,
) {
    @Serializable
    data class FlagStatusDto(
        val flagId: String,
        val status: String,
        val zoneId: String? = null,
        val activity: String? = null,
        val updatedAt: String,
    )
}

/** 팔로우 · 언팔로우 응답 data. iOS `FestivalFlagFollowDataDTO`. */
@Serializable
data class FlagFollowDto(
    val flagId: String,
    val following: Boolean,
    val followerCount: Int,
)

/** 신고 응답 data. iOS `FestivalFlagReportDataDTO`. */
@Serializable
data class FlagReportDto(val reportId: String)

/** 대표 깃발 응답 data. `pinnedFlagId` 키가 없으면 해석 실패다(null과 구분). iOS `FestivalPinnedFlagDataDTO`. */
@Serializable(with = PinnedFlagDtoSerializer::class)
data class PinnedFlagDto(val pinnedFlagId: String?)

internal object PinnedFlagDtoSerializer : KSerializer<PinnedFlagDto> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: PinnedFlagDto) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("JSON으로만 보낸다")
        json.encodeJsonElement(buildJsonObject { put(PINNED_FLAG_ID, value.pinnedFlagId) })
    }

    override fun deserialize(decoder: Decoder): PinnedFlagDto {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON으로만 읽는다")
        val data = json.decodeJsonElement().jsonObject
        if (PINNED_FLAG_ID !in data) throw SerializationException("pinnedFlagId가 없습니다")
        return PinnedFlagDto(data.nullableString(PINNED_FLAG_ID))
    }

    private const val PINNED_FLAG_ID = "pinnedFlagId"
}

private fun JsonObject.nullableString(key: String): String? = when (val value = get(key)) {
    null, JsonNull -> null
    is JsonPrimitive -> if (value.isString) value.content else throw SerializationException("$key 형식이 다릅니다")
    else -> throw SerializationException("$key 형식이 다릅니다")
}
