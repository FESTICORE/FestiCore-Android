package io.festicore.service.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * 축제 팩 문서. `GET api/v1/festivals/{id}` 응답 `data`이자 팩 본문. iOS `FestivalPackDocument`가 읽는 키만 받는다
 * (`timezone` · `packVersion` · `rules` 등은 `ignoreUnknownKeys`로 버린다). `directions`는 귀가 카드가 쓰는 키만 받는다(iOS `FestivalPackDetailDTO`).
 * `eventPeriod` · `source` · `note` · `directions`는 형식이 틀리면 null, `zones` · `ticketLinks`는 손상 원소만 버린다. 나머지 배열은 원소 하나가 틀리면 문서 전체가 실패한다(iOS와 같음).
 * 존 지도 값(`mapImageUrl` · `mapAspect` · `mapVersion` · 존 `kind` · `order` · `area` · `landmark`)은 형식이 틀리면 null,
 * `mapStages`는 손상 원소만 버린다(iOS `FestivalZoneMapPackDTO`, 설계 E4).
 */
@Serializable
data class FestivalPackDocumentDto(
    val id: String,
    val name: String? = null,
    val venue: String? = null,
    val thumbUrl: String? = null,
    @Serializable(with = FestivalEventPeriodOrNullSerializer::class)
    val eventPeriod: FestivalEventPeriodDto? = null,
    val days: List<FestivalPackDayDto>? = null,
    val artists: List<FestivalPackArtistDto>? = null,
    val timetable: List<FestivalPackPerformanceDto>? = null,
    val sets: List<FestivalPackPerformanceDto>? = null,
    val stages: List<FestivalPackStageDto>? = null,
    val flagsEnabled: Boolean? = null,
    @Serializable(with = FestivalPackZoneListSerializer::class)
    val zones: List<FestivalPackZoneDto>? = null,
    @Serializable(with = FestivalPackSourceOrNullSerializer::class)
    val source: FestivalPackSourceDto? = null,
    @Serializable(with = FestivalTicketLinkListSerializer::class)
    val ticketLinks: List<FestivalTicketLinkDto>? = null,
    @Serializable(with = FestivalPackDirectionsOrNullSerializer::class)
    val directions: FestivalPackDirectionsDto? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val mapImageUrl: String? = null,
    @Serializable(with = DoubleOrNullSerializer::class)
    val mapAspect: Double? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val mapVersion: String? = null,
    @Serializable(with = FestivalPackMapStageListSerializer::class)
    val mapStages: List<FestivalPackMapStageDto>? = null,
)

/** `GET api/v1/festivals/{id}/pack/version` 응답 봉투. `message`가 없을 수 있다. */
@Serializable
internal data class FestivalPackVersionResponseDto(
    val code: String,
    val message: String? = null,
    val data: FestivalPackVersionDto? = null,
)

/** `GET api/v1/festivals/{id}/pack/version` 응답 `data`. iOS `FestivalPackVersionDataDTO`(`packUpdatedAt` · `etag`는 읽지 않는다). */
@Serializable
data class FestivalPackVersionDto(
    val packVersion: Int,
    val mapVersion: String? = null,
)

/** 지도 위 무대 머리글 자리(0~1 비율). */
@Serializable
data class FestivalPackMapStageDto(
    val stageId: String,
    val x: Double,
    val y: Double,
    val w: Double,
)

/** 존 영역(지도 왼쪽 위 기준 0~1 비율). */
@Serializable
data class FestivalPackZoneAreaDto(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
)

/** 가는 길 중 귀가 카드가 쓰는 값. 마지막 셔틀 · 막차 시각은 서버 문자열 그대로(ISO 또는 `HH:mm`). */
@Serializable
data class FestivalPackDirectionsDto(
    val lastShuttleAt: String? = null,
    val lastTrainAt: String? = null,
    val shuttle: FestivalPackDirectionsSectionDto? = null,
    val transit: FestivalPackDirectionsSectionDto? = null,
)

@Serializable
data class FestivalPackDirectionsSectionDto(
    /** 대중교통의 가까운 역 */
    val stationName: String? = null,
    /** 셔틀 정류장 */
    val stopName: String? = null,
    val walkMinutes: Int? = null,
)

@Serializable
data class FestivalEventPeriodDto(
    val firstDate: String,
    val lastDate: String,
)

@Serializable
data class FestivalPackDayDto(
    val date: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
)

@Serializable
data class FestivalPackArtistDto(
    val name: String,
    val imageUrl: String? = null,
)

@Serializable
data class FestivalPackStageDto(
    val id: String,
    val name: String,
)

/** 지도 값(`kind` · `order` · `area` · `landmark`)은 형식이 틀리면 null이라 보드의 존 이름 찾기에 영향을 주지 않는다. */
@Serializable
data class FestivalPackZoneDto(
    val id: String,
    val name: String,
    @Serializable(with = StringOrNullSerializer::class)
    val kind: String? = null,
    @Serializable(with = IntOrNullSerializer::class)
    val order: Int? = null,
    @Serializable(with = FestivalPackZoneAreaOrNullSerializer::class)
    val area: FestivalPackZoneAreaDto? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val landmark: String? = null,
)

/** 각 필드는 형식이 틀리면 null. */
@Serializable
data class FestivalPackSourceDto(
    @Serializable(with = StringOrNullSerializer::class)
    val name: String? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val url: String? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val verifiedAt: String? = null,
)

/** 공연 한 건. `note`만 형식이 틀려도 null로 읽는다. */
@Serializable
data class FestivalPackPerformanceDto(
    val id: String,
    val artist: String? = null,
    val stageId: String? = null,
    val stageName: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val imageUrl: String? = null,
    @Serializable(with = StringOrNullSerializer::class)
    val note: String? = null,
)

object StringOrNullSerializer : OrNullSerializer<String>(String.serializer())

object DoubleOrNullSerializer : OrNullSerializer<Double>(Double.serializer())

object IntOrNullSerializer : OrNullSerializer<Int>(Int.serializer())

object FestivalPackZoneAreaOrNullSerializer : OrNullSerializer<FestivalPackZoneAreaDto>(FestivalPackZoneAreaDto.serializer())

object FestivalPackMapStageListSerializer : LossyListSerializer<FestivalPackMapStageDto>(FestivalPackMapStageDto.serializer())

object FestivalEventPeriodOrNullSerializer : OrNullSerializer<FestivalEventPeriodDto>(FestivalEventPeriodDto.serializer())

object FestivalPackSourceOrNullSerializer : OrNullSerializer<FestivalPackSourceDto>(FestivalPackSourceDto.serializer())

object FestivalPackDirectionsOrNullSerializer : OrNullSerializer<FestivalPackDirectionsDto>(FestivalPackDirectionsDto.serializer())

object FestivalPackZoneListSerializer : LossyListSerializer<FestivalPackZoneDto>(FestivalPackZoneDto.serializer())
