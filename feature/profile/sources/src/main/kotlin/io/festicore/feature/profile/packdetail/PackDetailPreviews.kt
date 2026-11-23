package io.festicore.feature.profile.packdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val previewDetail =
    FestivalPackDetail(
        pack =
            FestivalReceivedPack(
                id = "fst_preview",
                name = "프리뷰 페스티벌",
                packVersion = 12,
                latestPackVersion = 13,
                checkedAt = Instant.parse("2026-11-03T05:00:00Z"),
                sizeBytes = 524_288,
            ),
        directions =
            FestivalPackDetail.Directions(
                lastShuttleAt = "2026-11-22T22:20:00+09:00",
                lastTrainAt = "23:52",
                transit = FestivalPackDetail.Section(body = "출구로 나와 직진", stationName = "프리뷰역", walkMinutes = 8),
                memo = "프리뷰 메모",
                naverMapUrl = "https://example.com/naver",
                kakaoMapUrl = "https://example.com/kakao",
                source = FestivalPackDetail.Source(name = "프리뷰 출처", verifiedAt = Instant.parse("2026-11-03T05:00:00Z")),
            ),
        checklist =
            listOf(
                FestivalPackDetail.ChecklistItem(id = "ticket", label = "모바일 티켓", required = true),
                FestivalPackDetail.ChecklistItem(id = "earplug", label = "귀마개"),
            ),
        rules = listOf("유리병", "삼각대"),
    )

private val previewLoaded =
    PackDetailState(
        festivalId = "fst_preview",
        title = "프리뷰 페스티벌",
        load = PackDetailLoad.Loaded,
        detail = previewDetail,
        checklist = FestivalChecklist(checkedIds = setOf("ticket")),
        changes = FestivalPackChanges(fromVersion = 12, toVersion = 13, sizeBytes = 524_288, changedSets = 2),
        installedMapApps = setOf(MapApp.Naver),
    )

@Preview(showBackground = true)
@Composable
private fun PackDetailDirectionsPreview() {
    FestiCoreTheme { PackDetailContent(state = previewLoaded) }
}

@Preview(showBackground = true)
@Composable
private fun PackDetailChecklistPreview() {
    FestiCoreTheme { PackDetailContent(state = previewLoaded.copy(tab = PackDetailTab.Checklist)) }
}

@Preview(showBackground = true)
@Composable
private fun PackDetailManagePreview() {
    FestiCoreTheme { PackDetailContent(state = previewLoaded.copy(tab = PackDetailTab.Manage)) }
}

@Preview(showBackground = true)
@Composable
private fun PackDetailLoadingPreview() {
    FestiCoreTheme { PackDetailContent(state = PackDetailState(festivalId = "fst_preview", title = "프리뷰 페스티벌")) }
}

@Preview(showBackground = true)
@Composable
private fun PackDetailMissingPreview() {
    FestiCoreTheme {
        PackDetailContent(state = PackDetailState(festivalId = "fst_preview", title = "프리뷰 페스티벌", load = PackDetailLoad.Missing))
    }
}
