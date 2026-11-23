package io.festicore.feature.profile.notice

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.entity.NoticeSummary
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val previewPublishedAt = Instant.parse("2026-11-03T15:30:00Z")

@Preview(showBackground = true)
@Composable
private fun NoticeListLoadedPreview() {
    FestiCoreTheme {
        NoticeListContent(
            state =
                NoticeListState(
                    load = NoticeLoad.Loaded,
                    notices =
                        listOf(
                            NoticeSummary(id = "a", title = "서비스 안내", publishedAt = previewPublishedAt),
                            NoticeSummary(id = "b", title = "점검 안내", publishedAt = previewPublishedAt),
                        ),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NoticeListLoadingPreview() {
    FestiCoreTheme { NoticeListContent(state = NoticeListState(load = NoticeLoad.Loading)) }
}

@Preview(showBackground = true)
@Composable
private fun NoticeListFailedPreview() {
    FestiCoreTheme { NoticeListContent(state = NoticeListState(load = NoticeLoad.Failed)) }
}

@Preview(showBackground = true)
@Composable
private fun NoticeListEmptyPreview() {
    FestiCoreTheme { NoticeListContent(state = NoticeListState(load = NoticeLoad.Loaded)) }
}

@Preview(showBackground = true)
@Composable
private fun NoticeDetailLoadedPreview() {
    FestiCoreTheme {
        NoticeDetailContent(
            state =
                NoticeDetailState(
                    id = "a",
                    title = "서비스 안내",
                    load = NoticeLoad.Loaded,
                    detail = NoticeDetail(id = "a", title = "서비스 안내", content = "공지 본문", publishedAt = previewPublishedAt),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NoticeDetailLoadingPreview() {
    FestiCoreTheme { NoticeDetailContent(state = NoticeDetailState(id = "a", title = "서비스 안내", load = NoticeLoad.Loading)) }
}
