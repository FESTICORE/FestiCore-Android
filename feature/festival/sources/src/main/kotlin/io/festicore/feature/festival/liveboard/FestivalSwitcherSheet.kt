package io.festicore.feature.festival.liveboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.feature.festival.festivallist.FestivalPoster
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val RowHeight = 68.dp
private val ListMaxHeight = 204.dp
private val PosterSize = 44.dp
private val CheckSize = 20.dp
private val ChevronSize = 16.dp
private val AllFestivalsHeight = 56.dp

/**
 * 축제 바꾸기 시트(iOS `FestivalSwitcherView`). 항목은 기기에 받은 팩이 있는 축제다.
 * 제목은 "내가 예매한 페스티벌"이지만 iOS처럼 받은 팩 기준이다(설계 위험 6).
 */
@Composable
internal fun FestivalSwitcherSheet(
    items: List<LiveBoardSwitcherItem>?,
    selectedFestivalId: String,
    onDismissRequest: () -> Unit,
    onFestivalChosen: (String) -> Unit,
    onSeeAllFestivals: () -> Unit,
) {
    FCSheet(item = items, onDismissRequest = onDismissRequest) { festivals ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(FestiCoreSemanticColor.backgroundElevated.color)
                    .padding(start = FestiCoreSpacing.paddingP20, end = FestiCoreSpacing.paddingP20, bottom = FestiCoreSpacing.paddingP8),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = FestiCoreSpacing.paddingP12),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
            ) {
                Text(
                    text = "페스티벌 바꾸기",
                    style = FestiCoreTextStyle.baseTitle3Bold.textStyle,
                    color = FestiCoreSemanticColor.labelNormal.color,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "내가 예매한 페스티벌",
                    style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                )
            }
            Column(modifier = Modifier.height(minOf(RowHeight * festivals.size, ListMaxHeight)).verticalScroll(rememberScrollState())) {
                festivals.forEach { festival ->
                    SwitcherRow(festival, isSelected = festival.id == selectedFestivalId) { onFestivalChosen(festival.id) }
                }
            }
            // Figma Festival Switch Sheet: 목록 · 구분선 · 전체 보기 행 사이 8
            Box(
                modifier =
                    Modifier
                        .padding(vertical = FestiCoreSpacing.paddingP8)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(FestiCoreSemanticColor.lineNormal.color),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AllFestivalsHeight)
                        .clickable(onClick = onSeeAllFestivals)
                        .semantics(mergeDescendants = true) {
                            onClick(label = "페스티벌 목록으로 이동합니다") {
                                onSeeAllFestivals()
                                true
                            }
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "전체 페스티벌 보기",
                    style = FestiCoreTextStyle.baseBodyRegular.textStyle,
                    color = FestiCoreSemanticColor.labelNormal.color,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painter = painterResource(R.drawable.ic_chevron_right_16),
                    contentDescription = null,
                    modifier = Modifier.size(ChevronSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNeutral.color),
                )
            }
        }
    }
}

@Composable
private fun SwitcherRow(
    festival: LiveBoardSwitcherItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = "${festival.name}, ${festival.subtitle}"
                    selected = isSelected
                    onClick(label = "페스티벌 라이브 보드로 이동합니다") {
                        onClick()
                        true
                    }
                },
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Figma Thumbnail 모서리 12. 목록 포스터(반경 8)를 더 큰 반경으로 한 번 더 자른다.
        Box(modifier = Modifier.size(PosterSize).clip(RoundedCornerShape(FestiCoreRadius.r12))) {
            FestivalPoster(url = festival.thumbnailUrl, imageRevision = 0)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM2)) {
            Text(
                text = festival.name,
                style = FestiCoreTextStyle.baseCalloutMedium.textStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = festival.subtitle,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                color = FestiCoreSemanticColor.labelAssistive.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(CheckSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
            )
        }
    }
}
