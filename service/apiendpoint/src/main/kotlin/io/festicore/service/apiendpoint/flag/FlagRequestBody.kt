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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

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

private fun JsonObject.nullableString(key: String): String? = when (val value = get(key)) {
    null, JsonNull -> null
    is JsonPrimitive -> if (value.isString) value.content else throw SerializationException("$key 형식이 다릅니다")
    else -> throw SerializationException("$key 형식이 다릅니다")
}
