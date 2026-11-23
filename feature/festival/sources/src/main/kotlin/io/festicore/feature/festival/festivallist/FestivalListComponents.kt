package io.festicore.feature.festival.festivallist
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.festicore.feature.festival.accessibility.FestivalAccessibilityId
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.theme.FestiCoreTheme
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage

private val PosterIconSize = 36.dp

/**
 * 축제 포스터(iOS `FestivalPosterView`). 영역을 채우고 넘친 부분은 자르며 반경 8로 둥글린다.
 * 없음 · 실패는 `fillStrong` 바탕 + 음표 36. [imageRevision]이 바뀌면 다시 받는다(갱신 실패 뒤 복구).
 */
@Composable
internal fun FestivalPoster(
    url: String?,
    imageRevision: Int,
    modifier: Modifier = Modifier,
) {
    key(url, imageRevision) {
        FCRemoteImage(
            url = url,
            modifier = modifier.fillMaxSize().clip(RoundedCornerShape(FestiCoreRadius.r8)),
            placeholderIcon = R.drawable.ic_music_24,
            placeholderIconSize = PosterIconSize,
        )
    }
}

private val SkeletonMediaHeight = 140.dp
private val SkeletonBorderWidth = 1.dp

/** 목록 스켈레톤 카드 3장(iOS `FestivalListSkeletonView`). */
@Composable
internal fun FestivalListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(FestivalAccessibilityId.LIST_SKELETON),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        repeat(SKELETON_COUNT) { SkeletonCard() }
    }
}

/** Figma Festival Card와 같은 순서: 포스터, 제목 · 태그, 장소 · 날짜, 팩 안내, 예매처 줄. */
@Composable
private fun SkeletonCard() {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .border(SkeletonBorderWidth, FestiCoreSemanticColor.lineNormal.color, shape),
    ) {
        FCSkeletonView(FCSkeletonShape.Rect, Modifier.fillMaxWidth().height(SkeletonMediaHeight))
        Column(
            modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP12),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
                FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(200.dp, 24.dp))
                Spacer(Modifier.weight(1f))
                FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r4), Modifier.size(40.dp, 22.dp))
            }
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(240.dp, 18.dp))
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(260.dp, 16.dp))
        }
        Box(modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP12)) {
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(84.dp, 18.dp))
        }
    }
}

/** 검색 결과 스켈레톤(iOS `FestivalListView.searchSkeleton`). */
@Composable
internal fun FestivalSearchSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP16).testTag(FestivalAccessibilityId.SEARCH_SKELETON),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(120.dp, 20.dp))
        repeat(SKELETON_COUNT) {
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
                FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r12), Modifier.size(48.dp))
                Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                    FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(180.dp, 18.dp))
                    FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(230.dp, 16.dp))
                }
            }
        }
    }
}

private const val SKELETON_COUNT = 3

@Preview
@Composable
private fun FestivalListSkeletonPreview() {
    FestiCoreTheme {
        Column(Modifier.background(FestiCoreSemanticColor.backgroundNormal.color).padding(FestiCoreSpacing.paddingP16)) {
            FestivalListSkeleton()
        }
    }
}

@Preview
@Composable
private fun FestivalSearchSkeletonPreview() {
    FestiCoreTheme {
        FestivalSearchSkeleton(Modifier.background(FestiCoreSemanticColor.backgroundNormal.color))
    }
}
