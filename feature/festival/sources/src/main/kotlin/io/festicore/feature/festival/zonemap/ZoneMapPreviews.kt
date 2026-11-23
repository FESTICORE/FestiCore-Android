package io.festicore.feature.festival.zonemap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalZoneMap
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewNow = Instant.parse("2026-10-20T06:00:00Z")

private val PreviewMap =
    FestivalZoneMap(
        festivalId = "preview",
        version = "zm-preview",
        imageUrl = null,
        aspect = 0.655,
        flagsEnabled = true,
        zones =
            listOf(
                FestivalZoneMap.Zone("zon_a", "A 무대 앞", "무대 앞", FestivalZoneMap.Area(0.02, 0.07, 0.46, 0.2), null),
                FestivalZoneMap.Zone("zon_b", "B 무대 앞", "무대 앞", FestivalZoneMap.Area(0.52, 0.07, 0.46, 0.2), null),
                FestivalZoneMap.Zone("zon_c", "텐트 앞", "무대 앞", FestivalZoneMap.Area(0.52, 0.3, 0.46, 0.3), "텐트 무대"),
                FestivalZoneMap.Zone("zon_d", "푸드존", "편의", FestivalZoneMap.Area(0.02, 0.3, 0.46, 0.3), null),
                FestivalZoneMap.Zone("zon_e", "정문", "출입구", FestivalZoneMap.Area(0.02, 0.9, 0.96, 0.08), "GATE"),
            ),
        stageHeaders =
            listOf(
                FestivalZoneMap.StageHeader("stg_a", "A Stage", 0.02, 0.01, 0.46, nowPlaying = "아티스트 1"),
                FestivalZoneMap.StageHeader("stg_b", "B Stage", 0.52, 0.01, 0.46, nowPlaying = null),
            ),
    )

private fun previewFlag(
    id: String,
    name: String,
    zoneId: String,
    minutesAgo: Long,
) = FestivalLiveBoardSnapshot.Flag(
    id = id,
    name = name,
    status = "active",
    zoneId = zoneId,
    activity = "slam",
    thumbnailUrl = null,
    updatedAt = PreviewNow.minusSeconds(minutesAgo * 60),
)

private val PreviewSnapshot =
    FestivalLiveBoardSnapshot(
        festivalId = "preview",
        activeFlags =
            listOf(previewFlag("flg_a", "깃발 1", "zon_a", 4), previewFlag("flg_b", "깃발 2", "zon_a", 1)) +
                (1..5).map { previewFlag("flg_d$it", "깃발 d$it", "zon_d", 2) },
        followedFlags = listOf(previewFlag("flg_a", "깃발 1", "zon_a", 4)),
        crew =
            FestivalLiveBoardSnapshot.Crew(
                id = "crw",
                name = "크루",
                members =
                    listOf(
                        FestivalLiveBoardSnapshot.Crew.Member("usr_a", "가나", "here", "zon_a", PreviewNow),
                        FestivalLiveBoardSnapshot.Crew.Member("usr_b", "다라", "leaving", "zon_c", PreviewNow.minusSeconds(3_600)),
                    ),
            ),
        receivedAt = PreviewNow,
    )

private val PreviewState =
    ZoneMapState(
        festivalId = "preview",
        viewerUserId = "usr_a",
        map = ZoneMapLoad.Loaded(PreviewMap),
        snapshot = PreviewSnapshot,
        receivedAt = PreviewNow,
    )

@Preview(heightDp = 900)
@Composable
private fun ZoneMapLegendPreview() {
    FestiCoreTheme { ZoneMapContent(state = PreviewState, now = PreviewNow) }
}

@Preview(heightDp = 900)
@Composable
private fun ZoneMapSelectedPreview() {
    FestiCoreTheme { ZoneMapContent(state = PreviewState.copy(selectedZoneId = "zon_c", isMapChanged = true), now = PreviewNow) }
}

@Preview
@Composable
private fun ZoneMapFollowGuidePreview() {
    FestiCoreTheme { ZoneMapContent(state = PreviewState.copy(snapshot = null, isOffline = true), now = PreviewNow) }
}

@Preview
@Composable
private fun ZoneMapListPreview() {
    val list = PreviewMap.copy(aspect = null, zones = PreviewMap.zones.map { it.copy(area = null) })
    FestiCoreTheme { ZoneMapContent(state = PreviewState.copy(map = ZoneMapLoad.Loaded(list)), now = PreviewNow) }
}

@Preview
@Composable
private fun ZoneMapLoadingPreview() {
    FestiCoreTheme { ZoneMapContent(state = ZoneMapState(festivalId = "preview", viewerUserId = null), now = PreviewNow) }
}

@Preview
@Composable
private fun ZoneMapMissingPreview() {
    FestiCoreTheme {
        ZoneMapContent(state = ZoneMapState(festivalId = "preview", viewerUserId = null, map = ZoneMapLoad.Missing), now = PreviewNow)
    }
}
