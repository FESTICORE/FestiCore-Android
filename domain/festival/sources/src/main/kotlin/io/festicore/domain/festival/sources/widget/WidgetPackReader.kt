package io.festicore.domain.festival.sources.widget

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.sources.model.dto.FestivalPackDocumentDto
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import java.time.Instant
import kotlinx.serialization.json.Json

private val widgetPackJson = Json { ignoreUnknownKeys = true }
internal fun readWidgetPack(pack: ServicePack): CachedFestivalPack = CachedFestivalPack(
    festivalId = pack.festivalId,
    packVersion = pack.packVersion,
    etag = pack.etag,
    checkedAt = pack.checkedAtMillis?.let(Instant::ofEpochMilli),
    sizeBytes = pack.pack.size,
    document = try {
        widgetPackJson.decodeFromString(FestivalPackDocumentDto.serializer(), pack.pack.decodeToString())
            .takeIf { it.id == pack.festivalId }?.toDomain()
    } catch (_: IllegalArgumentException) {
        null
    },
)
