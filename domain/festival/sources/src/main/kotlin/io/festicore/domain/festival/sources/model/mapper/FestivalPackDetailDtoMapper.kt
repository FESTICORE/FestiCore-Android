package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.sources.model.dto.FestivalPackChecklistItemDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackCoordinateDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDetailDirectionsDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDetailDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDetailSectionDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDetailSourceDto
import java.net.URI
import java.net.URISyntaxException

/** iOS `FestivalPackDetailDTO+.swift`. 공백 문자열은 null, 주소는 https · 호스트가 있는 것만 둔다. */
internal fun FestivalPackDetailDto.toDomain(pack: FestivalReceivedPack) = FestivalPackDetail(
    pack = pack,
    directions = directions?.toDomain() ?: FestivalPackDetail.Directions(),
    checklist = checklist.orEmpty().mapNotNull { it.toDomain() },
    rules = rules.orEmpty().mapNotNull { it?.nonBlank() },
    source = source?.toDomain(),
)

private fun FestivalPackDetailDirectionsDto.toDomain() = FestivalPackDetail.Directions(
    lastShuttleAt = lastShuttleAt?.nonBlank(),
    lastTrainAt = lastTrainAt?.nonBlank(),
    shuttle = shuttle?.toDomain(),
    transit = transit?.toDomain(),
    parking = parking?.toDomain(),
    memo = memo?.nonBlank(),
    naverMapUrl = mapLinks?.naver?.let(::httpsUrl),
    kakaoMapUrl = mapLinks?.kakao?.let(::httpsUrl),
    venueLocation = location?.toDomain(),
    source = source?.toDomain(),
)

/** 본문 · 링크 · 역 · 정류장이 모두 없으면 섹션을 숨긴다. */
private fun FestivalPackDetailSectionDto.toDomain(): FestivalPackDetail.Section? {
    val links = links.orEmpty().mapNotNull { link ->
        val label = link.label?.nonBlank() ?: return@mapNotNull null
        val url = link.url?.let(::httpsUrl) ?: return@mapNotNull null
        FestivalPackDetail.Link(label, url)
    }
    val body = body?.nonBlank()
    val station = stationName?.nonBlank()
    val stop = stopName?.nonBlank()
    if (body == null && links.isEmpty() && station == null && stop == null) return null
    return FestivalPackDetail.Section(
        body = body,
        links = links,
        stationName = station,
        stopName = stop,
        walkMinutes = walkMinutes,
        location = location?.toDomain(),
    )
}

/** 위도 · 경도가 모두 있고 유한하며 범위 안일 때만 좌표로 쓴다. */
internal fun FestivalPackCoordinateDto.toDomain(): FestivalPackDetail.Coordinate? {
    val latitude = latitude ?: return null
    val longitude = longitude ?: return null
    if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return FestivalPackDetail.Coordinate(latitude, longitude)
}

private fun FestivalPackChecklistItemDto.toDomain(): FestivalPackDetail.ChecklistItem? {
    val label = label.nonBlank() ?: return null
    if (id.isEmpty()) return null
    return FestivalPackDetail.ChecklistItem(id = id, label = label, required = required ?: false, note = note?.nonBlank())
}

private fun FestivalPackDetailSourceDto.toDomain(): FestivalPackDetail.Source? {
    val name = name?.nonBlank() ?: return null
    return FestivalPackDetail.Source(name = name, verifiedAt = FestivalIsoTime.parse(verifiedAt))
}

private fun String.nonBlank(): String? = trim().ifEmpty { null }

private fun httpsUrl(value: String): String? {
    val uri = try {
        URI(value)
    } catch (_: URISyntaxException) {
        return null
    }
    return value.takeIf { uri.scheme.equals("https", ignoreCase = true) && uri.host != null }
}
