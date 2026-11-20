package io.festicore.ui.designkit.component.overlay

import androidx.compose.runtime.Composable
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews

@FCPreviews
@Composable
private fun FCBannerPreview() {
    FCPreviewSurface {
        FCBanner("시간표가 바뀌었어요 · 3개 변경") {}
        FCBanner("오프라인이에요", showsChevron = false) {}
    }
}

@FCPreviews
@Composable
private fun FCCheckBadgePreview() {
    FCPreviewSurface { FCCheckBadge() }
}

@FCPreviews
@Composable
private fun FCDoneHeroPreview() {
    FCPreviewSurface {
        FCDoneHero(title = "깃발이 준비됐어요", message = "공연 당일 기수 보드에서 깃발을 올리면\n팔로워에게 알림이 가요.")
    }
}

@FCPreviews
@Composable
private fun FCSearchEmptyPreview() {
    FCPreviewSurface {
        FCSearchEmpty(query = "락페")
        FCSearchEmpty(query = "")
    }
}
