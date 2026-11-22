package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalIsoTime
import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.domain.festival.FestivalZoneMap
import java.time.Instant

/**
 * 팩 문서 → 존 지도. iOS `FestivalZoneMapPackDTO.toDomain`.
 * 존은 `order` 오름차순(없으면 맨 뒤, 같으면 원래 순서), `aspect ≤ 0`이면 null, 무대 머리글은 팩 `stages`에 이름이 있는 것만(첫 정의),
 * 지금 공연은 그 무대에서 `startsAt ≤ now < endsAt`인 첫 공연의 아티스트다. `flagsEnabled`가 없으면 참이다.
 */
internal fun FestivalPackDocument.toZoneMap(festivalId: String, now: Instant): FestivalZoneMap {
    val stageNames = mutableMapOf<String, String>()
    stages.orEmpty().forEach { stageNames.putIfAbsent(it.id, it.name) }
    // sortedBy는 안정 정렬이라 같은 order는 원래 순서를 지킨다.
    val orderedZones = zones.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE }
    return FestivalZoneMap(
        festivalId = festivalId,
        version = mapVersion,
        imageUrl = mapImageUrl,
        aspect = mapAspect?.takeIf { it > 0 },
        flagsEnabled = flagsEnabled ?: true,
        zones =
            orderedZones.map { zone ->
                FestivalZoneMap.Zone(
                    id = zone.id,
                    name = zone.name,
                    kind = zone.kind,
                    area = zone.area?.let { FestivalZoneMap.Area(it.x, it.y, it.width, it.height) },
                    landmark = zone.landmark,
                )
            },
        stageHeaders =
            mapStages.orEmpty().mapNotNull { header ->
                val name = stageNames[header.stageId] ?: return@mapNotNull null
                FestivalZoneMap.StageHeader(
                    stageId = header.stageId,
                    name = name,
                    x = header.x,
                    y = header.y,
                    width = header.width,
                    nowPlaying = nowPlaying(header.stageId, now),
                )
            },
    )
}

private fun FestivalPackDocument.nowPlaying(stageId: String, now: Instant): String? = sets.orEmpty().firstOrNull { set ->
    if (set.stageId != stageId) return@firstOrNull false
    val starts = FestivalIsoTime.parse(set.startsAt) ?: return@firstOrNull false
    val ends = FestivalIsoTime.parse(set.endsAt) ?: return@firstOrNull false
    starts <= now && now < ends
}?.artist
