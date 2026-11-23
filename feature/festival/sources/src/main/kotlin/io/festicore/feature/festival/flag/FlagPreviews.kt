package io.festicore.feature.festival.flag

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import java.time.Instant

// 프리뷰 전용 가짜 값.
private fun previewFlag(
    id: String,
    name: String,
    status: String,
    followerCount: Int,
    tags: List<String>,
    zoneId: String?,
) = FestivalLiveBoardSnapshot.Flag(
    id = id,
    name = name,
    status = status,
    zoneId = zoneId,
    activity = null,
    thumbnailUrl = null,
    tags = tags,
    followerCount = followerCount,
)

private val PreviewSnapshot =
    FestivalLiveBoardSnapshot(
        festivalId = "preview",
        activeFlags = emptyList(),
        followedFlags = listOf(previewFlag("f2", "떼창 연합", "resting", 540, listOf("sing"), null)),
        crew = null,
        receivedAt = Instant.parse("2026-10-20T06:00:00Z"),
        directoryFlags =
            listOf(
                previewFlag("f1", "슬램 원정대", "active", 1284, listOf("slam", "newbie"), "zone_a"),
                previewFlag("f2", "떼창 연합", "resting", 540, listOf("sing"), null),
                previewFlag("f3", "기차놀이 클럽", "scheduled", 213, listOf("train"), "zone_b"),
            ),
    )

private val PreviewDirectory = FlagDirectoryState(zoneNames = mapOf("zone_a" to "리벳 스테이지 앞 왼쪽", "zone_b" to "원더 스테이지 앞"))

@Preview(name = "깃발 목록", showBackground = true, heightDp = 900)
@Composable
private fun FlagLoadedPreview() {
    FlagContent(
        FlagState(festivalId = "preview", snapshot = PreviewSnapshot, viewState = FlagViewState.Loaded, directory = PreviewDirectory),
    )
}

@Preview(name = "깃발 목록 · 정렬 메뉴 · 토스트", showBackground = true, heightDp = 900)
@Composable
private fun FlagSortMenuPreview() {
    FlagContent(
        FlagState(
            festivalId = "preview",
            snapshot = PreviewSnapshot,
            viewState = FlagViewState.Loaded,
            directory = PreviewDirectory.copy(isSortMenuPresented = true, toast = "팔로우는 100개까지 할 수 있어요"),
        ),
    )
}

@Preview(name = "깃발 목록 · 오프라인", showBackground = true, heightDp = 900)
@Composable
private fun FlagOfflinePreview() {
    FlagContent(
        FlagState(festivalId = "preview", snapshot = PreviewSnapshot, viewState = FlagViewState.Failed, directory = PreviewDirectory),
    )
}

@Preview(name = "깃발 목록 · 불러오는 중", showBackground = true)
@Composable
private fun FlagLoadingPreview() {
    FlagContent(FlagState(festivalId = "preview", viewState = FlagViewState.Loading))
}

@Preview(name = "깃발 목록 · 실패", showBackground = true)
@Composable
private fun FlagFailedPreview() {
    FlagContent(FlagState(festivalId = "preview", viewState = FlagViewState.Failed))
}

@Preview(name = "깃발 목록 · 축제 없음", showBackground = true)
@Composable
private fun FlagNoFestivalPreview() {
    FlagContent(FlagState())
}
