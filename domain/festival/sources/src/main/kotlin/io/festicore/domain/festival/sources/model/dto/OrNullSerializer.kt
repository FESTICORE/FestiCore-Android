package io.festicore.domain.festival.sources.model.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull

/** 형식이 틀린 값은 오류 대신 null로 읽는다. iOS `try? container.decodeIfPresent(...)`. */
abstract class OrNullSerializer<T : Any>(private val element: KSerializer<T>) : KSerializer<T?> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = element.descriptor.nullable

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: T?) = encoder.encodeNullableSerializableValue(element, value)

    override fun deserialize(decoder: Decoder): T? {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON에서만 읽는다")
        val value = json.decodeJsonElement()
        if (value is JsonNull) return null
        return try {
            json.json.decodeFromJsonElement(element, value)
        } catch (_: IllegalArgumentException) { // SerializationException 포함
            null
        } catch (_: IndexOutOfBoundsException) {
            // 원시 값 자리에 객체 · 배열이 오면 트리 디코더가 이 예외를 낸다(kotlinx.serialization). 형식이 틀린 값과 같이 null로 읽는다.
            null
        }
    }
}
