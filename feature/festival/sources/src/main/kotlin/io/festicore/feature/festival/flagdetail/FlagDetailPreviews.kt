package io.festicore.feature.festival.flagdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.festival.entity.FestivalFlagDetail
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewNow = Instant.parse("2026-11-07T12:00:00Z")

private val PreviewDetail =
    FestivalFlagDetail(
        id = "flg_preview",
        name = "호락호락",
        thumbnailUrl = null,
        photoUrl = null,
        tags = listOf("slam", "sing", "newbie"),
        bio = "처음 와도 괜찮아요",
        followerCount = 1_284,
        isPlanVisible = true,
        planSetIds = listOf("set_past", "set_now", "set_late"),
        homeZoneId = null,
        status = FestivalFlagDetail.Status.Active,
        zoneId = "zone_a",
        activity = FestivalFlagDetail.Activity.Slam,
        statusUpdatedAt = PreviewNow.minusSeconds(240),
        isFollowing = false,
        isBearer = false,
        isHidden = false,
    )

private val PreviewContext =
    FlagDetailContext(
        zoneNames = mapOf("zone_a" to "리벳 스테이지 앞 왼쪽"),
        performances =
            mapOf(
                "set_past" to
                    FlagDetailContext.Performance(
                        "Sunset Riot",
                        "MAIN 스테이지",
                        PreviewNow.minusSeconds(10_800),
                        PreviewNow.minusSeconds(7_200),
                    ),
                "set_now" to
                    FlagDetailContext.Performance(
                        "The Lime Flags",
                        "SUB 스테이지",
                        PreviewNow.minusSeconds(600),
                        PreviewNow.plusSeconds(1_800),
                    ),
                "set_late" to
                    FlagDetailContext.Performance("모쉬핏", "TENT 스테이지", PreviewNow.plusSeconds(7_200), PreviewNow.plusSeconds(10_800)),
            ),
        mySetIds = setOf("set_now"),
        hasSession = true,
    )

private fun previewState(content: FlagDetailContent) =
    FlagDetailState(flagId = "flg_preview", context = PreviewContext, now = PreviewNow, content = content)

@Preview(name = "FlagDetail · 불러옴", heightDp = 1200)
@Composable
private fun FlagDetailLoadedPreview() {
    FlagDetailContent(state = previewState(FlagDetailContent.Loaded(PreviewDetail)))
}

@Preview(name = "FlagDetail · 오프라인")
@Composable
private fun FlagDetailOfflinePreview() {
    FlagDetailContent(
        state = previewState(FlagDetailContent.Loaded(PreviewDetail)).copy(isOffline = true, lastLoadedAt = PreviewNow.minusSeconds(180)),
    )
}

@Preview(name = "FlagDetail · 불러오는 중")
@Composable
private fun FlagDetailLoadingPreview() {
    FlagDetailContent(state = previewState(FlagDetailContent.Loading))
}

@Preview(name = "FlagDetail · 없는 깃발")
@Composable
private fun FlagDetailNotFoundPreview() {
    FlagDetailContent(state = previewState(FlagDetailContent.Failed(FlagDetailFailure.NotFound)))
}

@Preview(name = "FlagDetail · 다시 시도")
@Composable
private fun FlagDetailRetryPreview() {
    FlagDetailContent(state = previewState(FlagDetailContent.Failed(FlagDetailFailure.Unavailable)))
}
