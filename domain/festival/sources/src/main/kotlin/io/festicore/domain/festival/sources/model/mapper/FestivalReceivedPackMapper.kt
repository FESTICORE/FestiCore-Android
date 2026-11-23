package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.pack.FestivalPackRetention
import java.time.Duration
import java.time.Instant

/** 축제 목록에 같은 축제가 있으면 이름 · 썸네일 · 최신 버전을 목록 값으로 쓴다. iOS `CachedFestivalPack+ReceivedPack.swift`. */
internal fun CachedFestivalPack.toReceivedPack(catalogItem: FestivalCatalogItem?, imageUrl: (String?) -> String?) = FestivalReceivedPack(
    id = festivalId,
    name = catalogItem?.name ?: document?.name ?: festivalId,
    thumbnailUrl = catalogItem?.thumbnailUrl ?: imageUrl(document?.thumbUrl),
    packVersion = packVersion,
    latestPackVersion = catalogItem?.packVersion,
    checkedAt = checkedAt,
    sizeBytes = sizeBytes,
)

/** 최근에 확인한 팩부터, 같으면 축제 ID 순. */
internal fun List<CachedFestivalPack>.toReceivedPacks(
    catalog: List<FestivalCatalogItem>,
    imageUrl: (String?) -> String?,
): List<FestivalReceivedPack> {
    // 같은 ID가 여러 번 있으면 앞의 것을 쓴다(iOS `uniquingKeysWith: first`).
    val items = catalog.reversed().associateBy { it.id }
    return map { it.toReceivedPack(items[it.festivalId], imageUrl) }
        .sortedWith(compareByDescending<FestivalReceivedPack> { it.checkedAt ?: Instant.MIN }.thenBy { it.id })
}

/** 마지막 날 종료 후 보관 기간이 끝나는 시각. 일자가 없거나 한 날이라도 종료 시각을 읽을 수 없으면 null. iOS `CachedFestivalPack+Expiry.swift`. */
internal val CachedFestivalPack.expiresAt: Instant?
    get() {
        val days = document?.days?.takeIf { it.isNotEmpty() } ?: return null
        val ends = days.mapNotNull { FestivalIsoTime.parse(it.endsAt) }
        if (ends.size != days.size) return null
        return ends.max().plus(Duration.ofDays(FestivalPackRetention.DAYS.toLong()))
    }
