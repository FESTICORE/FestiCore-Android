package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.FestivalCatalogArtist
import io.festicore.domain.festival.FestivalCatalogItem
import io.festicore.domain.festival.FestivalCatalogPerformance
import io.festicore.domain.festival.FestivalTicketLink
import io.festicore.domain.festival.sources.model.dto.FestivalCatalogArtistDto
import io.festicore.domain.festival.sources.model.dto.FestivalItemDto
import io.festicore.domain.festival.sources.model.dto.FestivalTicketLinkDto
import java.net.URI
import java.net.URISyntaxException

/** iOS `FestivalItemDTO+.swift`. 썸네일은 [imageUrl]이 바꾼 주소를 담는다. */
internal fun FestivalItemDto.toDomain(imageUrl: (String?) -> String?): FestivalCatalogItem = FestivalCatalogItem(
    id = id,
    name = name,
    venue = venue,
    firstDate = firstDate,
    lastDate = lastDate,
    thumbnailUrl = imageUrl(thumbUrl),
    packVersion = packVersion,
    status = status,
    ticketLinks = ticketLinks.orEmpty().mapNotNull { it.toDomain() },
    artists = artists?.map { it.toDomain() },
)

private fun FestivalCatalogArtistDto.toDomain() = FestivalCatalogArtist(
    name = name,
    performances = performances.map { FestivalCatalogPerformance(it.startsAt, it.date, it.stageName) },
)

/** 이름이 비었거나 https · 호스트가 없는 링크는 버린다. */
internal fun FestivalTicketLinkDto.toDomain(): FestivalTicketLink? {
    val label = label.trim()
    if (label.isEmpty()) return null
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return null
    }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host == null) return null
    return FestivalTicketLink(label = label, url = url)
}
