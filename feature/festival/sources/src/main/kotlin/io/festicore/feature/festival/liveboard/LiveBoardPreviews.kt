package io.festicore.feature.festival.liveboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewPack =
    FestivalPackDocument(
        id = "preview",
        name = "프리뷰 페스티벌",
        days = listOf(FestivalPackDocument.Day("2026-10-20", "2026-10-20T13:00:00+09:00", "2026-10-20T23:00:00+09:00")),
        stages = listOf(FestivalPackDocument.Stage("stg_a", "A 무대"), FestivalPackDocument.Stage("stg_b", "B 무대")),
        sets =
            listOf(
                FestivalPackDocument.Performance(
                    "s1",
                    "아티스트 1",
                    "stg_a",
                    startsAt = "2026-10-20T15:00:00+09:00",
                    endsAt = "2026-10-20T15:40:00+09:00",
                ),
                FestivalPackDocument.Performance(
                    "s2",
                    "아티스트 2",
                    "stg_a",
                    startsAt = "2026-10-20T16:00:00+09:00",
                    endsAt = "2026-10-20T16:40:00+09:00",
                ),
                FestivalPackDocument.Performance(
                    "s3",
                    "아티스트 3",
                    "stg_b",
                    startsAt = "2026-10-20T16:10:00+09:00",
                    endsAt = "2026-10-20T17:00:00+09:00",
                ),
            ),
        source = FestivalPackDocument.Source(name = "프리뷰 출처", url = null, verifiedAt = "2026-10-16T05:00:00Z"),
    )

private val PreviewState =
    LiveBoardState(
        festivalId = "preview",
        catalog = listOf(
            FestivalCatalogItem(id = "preview", name = "프리뷰 페스티벌", firstDate = "2026-10-20", lastDate = "2026-10-20", packVersion = 1),
        ),
        cachedPacks = mapOf("preview" to CachedFestivalPack("preview", 1, "v1", checkedAt = null, document = PreviewPack)),
        selectedPack = PreviewPack,
        connection = LiveBoardConnection.Live,
        snapshot =
            FestivalLiveBoardSnapshot(
                festivalId = "preview",
                activeFlags =
                    listOf(
                        FestivalLiveBoardSnapshot.Flag(
                            "f1",
                            "깃발 1",
                            "active",
                            null,
                            "slam",
                            null,
                            updatedAt = Instant.parse("2026-10-20T06:20:00Z"),
                        ),
                        FestivalLiveBoardSnapshot.Flag(
                            "f2",
                            "깃발 2",
                            "resting",
                            null,
                            null,
                            null,
                            updatedAt = Instant.parse("2026-10-20T06:10:00Z"),
                        ),
                    ),
                followedFlags = emptyList(),
                crew = null,
                serverAt = Instant.parse("2026-10-20T06:30:00Z"),
            ),
        receivedAt = Instant.parse("2026-10-20T06:30:00Z"),
        clockAnchorAt = Instant.parse("2026-10-20T06:30:00Z"),
    )

@Preview(heightDp = 1600)
@Composable
private fun LiveBoardContentPreview() {
    FestiCoreTheme { LiveBoardContent(state = PreviewState, deviceNow = Instant.parse("2026-10-20T06:31:00Z")) }
}

@Preview
@Composable
private fun LiveBoardLoadingPreview() {
    FestiCoreTheme {
        LiveBoardContent(
            state = LiveBoardState(festivalId = "preview", catalog = PreviewState.catalog, onlineDetailLoading = true),
            deviceNow = Instant.parse("2026-10-20T06:31:00Z"),
        )
    }
}

@Preview
@Composable
private fun LiveBoardNotFoundPreview() {
    FestiCoreTheme { LiveBoardContent(state = LiveBoardState(festivalId = "missing"), deviceNow = Instant.parse("2026-10-20T06:31:00Z")) }
}
