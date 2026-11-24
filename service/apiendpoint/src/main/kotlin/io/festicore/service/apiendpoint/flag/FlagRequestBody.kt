package io.festicore.service.apiendpoint.flag

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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** 신고 본문. [reason]은 `fake` · `inappropriate` · `other`. iOS `FlagReportBody`(note는 보내지 않는다). */
@Serializable
data class FlagReportBody(val reason: String)

/** 대표 깃발 본문. 해제할 때도 `{"flagId":null}`로 null을 명시한다(`FestiCoreJson.explicitNulls = false`를 이 본문만 우회). iOS `PinnedFlagBody`. */
@Serializable(with = PinnedFlagBodySerializer::class)
data class PinnedFlagBody(val flagId: String?)

internal object PinnedFlagBodySerializer : KSerializer<PinnedFlagBody> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: PinnedFlagBody) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("JSON으로만 보낸다")
        json.encodeJsonElement(buildJsonObject { put(FLAG_ID, value.flagId) })
    }

    override fun deserialize(decoder: Decoder): PinnedFlagBody {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON으로만 읽는다")
        return PinnedFlagBody(json.decodeJsonElement().jsonObject.nullableString(FLAG_ID))
    }

    private const val FLAG_ID = "flagId"
}

/**
 * 기수 상태 변경 본문(`POST flags/{id}/status`). [clientAt]만 필수이고 나머지는 null이면 키를 보내지 않는다
 * (`FestiCoreJson.explicitNulls = false`). iOS `FlagStatusBody`.
 */
@Serializable
data class FlagStatusBody(
    val clientAt: String,
    val zoneId: String? = null,
    val activity: String? = null,
    val status: String? = null,
    val restart: Boolean? = null,
)

/**
 * 깃발 프로필 수정 본문(`PATCH flags/{id}`). [bio] · [homeZoneId]는 비우면 null을 명시하고, [plan]은 null이면 키를 보내지 않아 서버 값을 둔다.
 * 키마다 null 규칙이 달라 직렬화기를 직접 쓴다([PinnedFlagBody]와 같은 방식). iOS `FlagProfileBody`.
 */
@Serializable(with = FlagProfileBodySerializer::class)
data class FlagProfileBody(
    val name: String,
    val bio: String?,
    val tags: List<String>,
    val planVisible: Boolean,
    val homeZoneId: String?,
    val plan: List<String>? = null,
)

internal object FlagProfileBodySerializer : KSerializer<FlagProfileBody> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: FlagProfileBody) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("JSON으로만 보낸다")
        json.encodeJsonElement(
            buildJsonObject {
                put(NAME, value.name)
                put(BIO, value.bio)
                putJsonArray(TAGS) { value.tags.forEach { add(it) } }
                put(PLAN_VISIBLE, value.planVisible)
                put(HOME_ZONE_ID, value.homeZoneId)
                value.plan?.let { plan -> putJsonArray(PLAN) { plan.forEach { add(it) } } }
            },
        )
    }

    override fun deserialize(decoder: Decoder): FlagProfileBody = throw SerializationException("보내기 전용 본문이다")

    private const val NAME = "name"
    private const val BIO = "bio"
    private const val TAGS = "tags"
    private const val PLAN_VISIBLE = "planVisible"
    private const val HOME_ZONE_ID = "homeZoneId"
    private const val PLAN = "plan"
}

/** 기수 코드 인증 본문(`POST bearer/claim`). iOS `BearerClaimBody`. */
@Serializable
data class BearerClaimBody(
    val festivalId: String,
    val code: String,
)

/**
 * 코드 없는 기수 등록 본문(`POST bearer/register`). [bio] · [homeZoneId] · [plan]은 null이면 키를 보내지 않는다
 * (`FestiCoreJson.explicitNulls = false`). iOS `BearerRegisterBody`.
 */
@Serializable
data class BearerRegisterBody(
    val festivalId: String,
    val name: String,
    val bio: String? = null,
    val tags: List<String>,
    val planVisible: Boolean,
    val homeZoneId: String? = null,
    val plan: List<String>? = null,
)

private fun JsonObject.nullableString(key: String): String? = when (val value = get(key)) {
    null, JsonNull -> null
    is JsonPrimitive -> if (value.isString) value.content else throw SerializationException("$key 형식이 다릅니다")
    else -> throw SerializationException("$key 형식이 다릅니다")
}
