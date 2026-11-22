package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.service.api.FestivalPackDocumentDto
import io.festicore.service.api.FestivalPackPerformanceDto

/** 팩 문서 DTO → 엔티티. 손상 원소 처리는 DTO 해석 단계(service/api)에서 끝난다. 공연 메모는 앞뒤 공백을 지우고 비면 null. */
internal fun FestivalPackDocumentDto.toDomain() = FestivalPackDocument(
    id = id,
    name = name,
    venue = venue,
    thumbUrl = thumbUrl,
    eventPeriod = eventPeriod?.let { FestivalPackDocument.EventPeriod(it.firstDate, it.lastDate) },
    days = days?.map { FestivalPackDocument.Day(it.date, it.startsAt, it.endsAt) },
    artists = artists?.map { FestivalPackDocument.Artist(it.name, it.imageUrl) },
    timetable = timetable?.map { it.toDomain() },
    sets = sets?.map { it.toDomain() },
    stages = stages?.map { FestivalPackDocument.Stage(it.id, it.name) },
    flagsEnabled = flagsEnabled,
    zones = zones?.map { FestivalPackDocument.Zone(it.id, it.name) },
    source = source?.let { FestivalPackDocument.Source(it.name, it.url, it.verifiedAt) },
    ticketLinks = ticketLinks?.map { FestivalPackDocument.TicketLink(it.label, it.url) },
)

private fun FestivalPackPerformanceDto.toDomain() = FestivalPackDocument.Performance(
    id = id,
    artist = artist,
    stageId = stageId,
    stageName = stageName,
    startsAt = startsAt,
    endsAt = endsAt,
    imageUrl = imageUrl,
    note = note?.trim()?.takeIf { it.isNotEmpty() },
)
