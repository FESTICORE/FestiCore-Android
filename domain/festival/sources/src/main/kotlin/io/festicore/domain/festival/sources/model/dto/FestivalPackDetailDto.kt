package io.festicore.domain.festival.sources.model.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * 받은 팩 JSON에서 팩 상세(가는 길 · 준비물 · 반입 금지 · 출처)만 읽는다. 라이브 보드가 쓰는 [FestivalPackDocumentDto]와 같은 JSON을 따로 읽는다.
 * 키마다 형식이 틀리면 그 키만 null로 둔다(iOS `try?`). 준비물 · 링크는 손상 원소만 버린다. iOS `FestivalPackDetailDTO`.
 */
@Serializable
data class FestivalPackDetailDto(
    @Serializable(with = FestivalPackDetailDirectionsOrNullSerializer::class)
    val directions: FestivalPackDetailDirectionsDto? = null,
    @Serializable(with = FestivalPackChecklistOrNullSerializer::class)
    val checklist: List<FestivalPackChecklistItemDto>? = null,
    @Serializable(with = FestivalPackRulesOrNullSerializer::class)
    val rules: List<String?>? = null,
    @Serializable(with = FestivalPackDetailSourceOrNullSerializer::class)
    val source: FestivalPackDetailSourceDto? = null,
)

/** `lastDepartures`는 iOS 상세 DTO도 읽지 않는다. */
@Serializable
data class FestivalPackDetailDirectionsDto(
    @Serializable(with = FestivalPackDetailSectionOrNullSerializer::class)
    val shuttle: FestivalPackDetailSectionDto? = null,
    @Serializable(with = FestivalPackDetailSectionOrNullSerializer::class)
    val transit: FestivalPackDetailSectionDto? = null,
    @Serializable(with = FestivalPackDetailSectionOrNullSerializer::class)
    val parking: FestivalPackDetailSectionDto? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val memo: String? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val lastShuttleAt: String? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val lastTrainAt: String? = null,
    @Serializable(with = FestivalPackMapLinksOrNullSerializer::class)
    val mapLinks: FestivalPackMapLinksDto? = null,
    /** 출처를 확인한 축제 장소 좌표(`label` · `source`는 iOS도 읽지 않는다). */
    @Serializable(with = FestivalPackCoordinateSerializer::class)
    val location: FestivalPackCoordinateDto? = null,
    @Serializable(with = FestivalPackDetailSourceOrNullSerializer::class)
    val source: FestivalPackDetailSourceDto? = null,
)

@Serializable
data class FestivalPackDetailSectionDto(
    @Serializable(with = StringOrNullSerializer::class)
    val body: String? = null,
    @Serializable(with = FestivalPackLinkListSerializer::class)
    val links: List<FestivalPackLinkDto>? = null,
    /** 대중교통의 가까운 역 */
    @Serializable(with = StringOrNullSerializer::class)
    val stationName: String? = null,
    /** 셔틀 정류장 */
    @Serializable(with = StringOrNullSerializer::class)
    val stopName: String? = null,
    @Serializable(with = IntOrNullSerializer::class)
    val walkMinutes: Int? = null,
    /** 역 · 정류장 좌표 */
    @Serializable(with = FestivalPackCoordinateSerializer::class)
    val location: FestivalPackCoordinateDto? = null,
)

/** 위도 · 경도를 값마다 따로 읽는다. 객체가 아니거나 숫자가 아니면 그 값만 null(iOS `CoordinateDTO`). */
data class FestivalPackCoordinateDto(
    val latitude: Double?,
    val longitude: Double?,
)

@Serializable
data class FestivalPackLinkDto(
    val label: String? = null,
    val url: String? = null,
)

@Serializable
data class FestivalPackMapLinksDto(
    @Serializable(with = StringOrNullSerializer::class)
    val naver: String? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val kakao: String? = null,
)

@Serializable
data class FestivalPackChecklistItemDto(
    val id: String,
    val label: String,
    val required: Boolean? = null,
    val note: String? = null,
)

@Serializable
data class FestivalPackDetailSourceDto(
    val name: String? = null,
    val verifiedAt: String? = null,
)

object FestivalPackDetailDirectionsOrNullSerializer :
    OrNullSerializer<FestivalPackDetailDirectionsDto>(FestivalPackDetailDirectionsDto.serializer())

object FestivalPackDetailSectionOrNullSerializer : OrNullSerializer<FestivalPackDetailSectionDto>(FestivalPackDetailSectionDto.serializer())

object FestivalPackMapLinksOrNullSerializer : OrNullSerializer<FestivalPackMapLinksDto>(FestivalPackMapLinksDto.serializer())

object FestivalPackDetailSourceOrNullSerializer : OrNullSerializer<FestivalPackDetailSourceDto>(FestivalPackDetailSourceDto.serializer())

object FestivalPackLinkListSerializer : LossyListSerializer<FestivalPackLinkDto>(FestivalPackLinkDto.serializer())

/** 배열이면 손상 원소만 버리고, 배열이 아니면 null(iOS `LossyChecklistItemDTO`). */
object FestivalPackChecklistOrNullSerializer : OrNullSerializer<List<FestivalPackChecklistItemDto>>(FestivalPackChecklistListSerializer)

private object FestivalPackChecklistListSerializer :
    LossyListSerializer<FestivalPackChecklistItemDto>(FestivalPackChecklistItemDto.serializer())

/** null 원소는 남기고(매퍼가 버림) 문자열이 아닌 원소가 있으면 전체 null(iOS `[String?]`). */
object FestivalPackRulesOrNullSerializer : OrNullSerializer<List<String?>>(ListSerializer(String.serializer().nullable))

object FestivalPackCoordinateSerializer : KSerializer<FestivalPackCoordinateDto?> {
    // 객체 · 다른 형식을 모두 JSON 요소로 받는다. 원시 문자열 서술자로 두면 트리 디코딩에서 상태가 어긋나 상위 키까지 깨진다.
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: FestivalPackCoordinateDto?) = throw SerializationException("읽기 전용")

    override fun deserialize(decoder: Decoder): FestivalPackCoordinateDto? {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON에서만 읽는다")
        val value = json.decodeJsonElement() as? JsonObject ?: return FestivalPackCoordinateDto(null, null)
        return FestivalPackCoordinateDto(value.number("latitude"), value.number("longitude"))
    }

    private fun JsonObject.number(key: String): Double? = (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
}
