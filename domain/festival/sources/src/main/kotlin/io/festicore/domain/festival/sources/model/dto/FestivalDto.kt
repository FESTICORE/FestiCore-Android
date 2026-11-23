package io.festicore.domain.festival.sources.model.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull

/**
 * `GET api/v1/festivals` 응답 `data`. 마지막 쪽은 [nextCursor]가 null. iOS `FestivalListDataDTO`.
 * 서버의 `flagsEnabled` · `artistNames`는 iOS에도 없어 받지 않는다(`ignoreUnknownKeys`).
 */
@Serializable
data class FestivalListDto(
    val items: List<FestivalItemDto>,
    val nextCursor: String? = null,
)

/** 축제 한 건. 목록 캐시 파일에도 이 형태로 저장한다. iOS `FestivalItemDTO`. */
@Serializable
data class FestivalItemDto(
    val id: String,
    val name: String,
    val venue: String? = null,
    val firstDate: String? = null,
    val lastDate: String? = null,
    val thumbUrl: String? = null,
    val packVersion: Int,
    val status: String? = null,
    /** 손상된 원소는 건너뛰고, 배열이 아니면 빈 목록. */
    @Serializable(with = FestivalTicketLinkListSerializer::class)
    val ticketLinks: List<FestivalTicketLinkDto>? = null,
    /** 손상된 원소는 건너뛴다. null은 서버 · 캐시에 이 필드가 없었다는 뜻이다. */
    @Serializable(with = FestivalCatalogArtistListSerializer::class)
    val artists: List<FestivalCatalogArtistDto>? = null,
)

@Serializable
data class FestivalTicketLinkDto(
    val label: String,
    val url: String,
)

@Serializable
data class FestivalCatalogArtistDto(
    val name: String,
    /** 손상된 원소는 건너뛰고, 없으면 빈 목록. */
    @Serializable(with = FestivalCatalogPerformanceListSerializer::class)
    val performances: List<FestivalCatalogPerformanceDto> = emptyList(),
)

@Serializable
data class FestivalCatalogPerformanceDto(
    val startsAt: String,
    val date: String,
    val stageName: String? = null,
)

/** 원소 하나가 깨져도 나머지를 살린다. 배열이 아니면 빈 목록. iOS `LossyArray` · `FestivalTicketLinksDTO`. */
abstract class LossyListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON에서만 읽는다")
        val array = json.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            if (item is JsonNull) return@mapNotNull null
            try {
                json.json.decodeFromJsonElement(element, item)
            } catch (_: IllegalArgumentException) { // SerializationException 포함
                null
            }
        }
    }
}

object FestivalTicketLinkListSerializer : LossyListSerializer<FestivalTicketLinkDto>(FestivalTicketLinkDto.serializer())

object FestivalCatalogArtistListSerializer : LossyListSerializer<FestivalCatalogArtistDto>(FestivalCatalogArtistDto.serializer())

object FestivalCatalogPerformanceListSerializer :
    LossyListSerializer<FestivalCatalogPerformanceDto>(FestivalCatalogPerformanceDto.serializer())
