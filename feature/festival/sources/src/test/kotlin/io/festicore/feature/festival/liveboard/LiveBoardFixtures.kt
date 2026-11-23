package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOverview
import io.festicore.domain.festival.entity.FestivalPackDocument
import java.time.Instant
import java.time.OffsetDateTime

/** iOS 라이브 보드 테스트의 `packJSON` · `festival` · `date(_:)`를 옮긴 가짜 값. */
internal object LiveBoardFixtures {
    fun date(value: String): Instant = OffsetDateTime.parse(value).toInstant()

    /** iOS `FestivalLiveBoardPresentationTests.packJSON`. */
    val pack =
        FestivalPackDocument(
            id = "fst",
            flagsEnabled = true,
            days = listOf(FestivalPackDocument.Day("2026-10-20", "2026-10-20T13:00:00+09:00", "2026-10-20T23:00:00+09:00")),
            zones = listOf(FestivalPackDocument.Zone("zon_main", "메인 앞"), FestivalPackDocument.Zone("zon_food", "푸드존")),
            source = FestivalPackDocument.Source(name = "주최측 공지", url = null, verifiedAt = "2026-10-16T05:00:00Z"),
            stages = listOf(FestivalPackDocument.Stage("stg_main", "메인"), FestivalPackDocument.Stage("stg_side", "사이드")),
            sets =
                listOf(
                    performance("set_now", "stg_main", "스모킹구스", "2026-10-20T15:00:00+09:00", "2026-10-20T15:35:00+09:00"),
                    performance("set_next", "stg_main", "노이즈캔디", "2026-10-20T15:40:00+09:00", "2026-10-20T16:30:00+09:00"),
                    performance("set_side", "stg_side", "블루퓨즈", "2026-10-20T16:00:00+09:00", "2026-10-20T16:40:00+09:00"),
                ),
        )

    fun performance(
        id: String,
        stageId: String,
        artist: String,
        startsAt: String,
        endsAt: String?,
    ) = FestivalPackDocument.Performance(id = id, artist = artist, stageId = stageId, startsAt = startsAt, endsAt = endsAt)

    fun festival(
        id: String = "fst",
        name: String = "원더리벳 2026",
        firstDate: String? = "2026-10-20",
        lastDate: String? = "2026-10-20",
        venue: String? = null,
        isCached: Boolean = true,
    ) = FestivalOverview(
        id = id,
        name = name,
        venue = venue,
        firstDate = firstDate,
        lastDate = lastDate,
        thumbnailUrl = null,
        packVersion = 1,
        isCached = isCached,
        cachedPackVersion = if (isCached) 1 else null,
        catalogStatus = null,
        ticketUrl = null,
        artists = null,
    )

    fun flag(
        id: String,
        status: String = "active",
        updatedAt: Instant? = null,
        zoneId: String? = null,
        activity: String? = null,
        tags: List<String> = emptyList(),
        plan: List<String> = emptyList(),
    ) = FestivalLiveBoardSnapshot.Flag(
        id = id,
        name = id,
        status = status,
        zoneId = zoneId,
        activity = activity,
        thumbnailUrl = null,
        tags = tags,
        updatedAt = updatedAt,
        plannedSetIds = plan,
    )
}
