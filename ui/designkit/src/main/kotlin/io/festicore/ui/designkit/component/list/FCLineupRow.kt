package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.divider.FCDivider
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val NumberWidth = 20.dp
private val ThumbnailSize = 40.dp

/**
 * 시간표 공개 전 라인업 한 줄(Figma Lineup Row: 번호 · 40 썸네일 · 아티스트).
 * Figma 행 65 = 64 + 아래 구분선 1, 마지막 행은 [showsSeparator]를 끈 64다.
 *
 * @param thumbnail 40×40 r8 썸네일 자리에 채울 사진(iOS `thumbnail(_:)`). null이면 Fill/Strong 바탕만 보인다.
 */
@Composable
fun FCLineupRow(
    number: String,
    artist: String,
    modifier: Modifier = Modifier,
    showsSeparator: Boolean = true,
    thumbnail: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .background(FestiCoreSemanticColor.backgroundElevated.color),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP16, vertical = FestiCoreSpacing.paddingP12),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = number,
                modifier = Modifier.width(NumberWidth),
                style =
                    FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(
                        color = FestiCoreSemanticColor.labelAssistive.color,
                        fontFeatureSettings = "tnum",
                    ),
                maxLines = 1,
            )
            // Figma 11-12: 썸네일은 번호와 아티스트 사이(x48)에 둔다.
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {}
                        .size(ThumbnailSize)
                        .clip(RoundedCornerShape(FestiCoreRadius.r8))
                        .background(FestiCoreSemanticColor.fillStrong.color),
            ) {
                thumbnail?.invoke()
            }
            BasicText(
                text = artist,
                modifier = Modifier.weight(1f),
                style = FestiCoreTextStyle.baseCalloutMedium.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showsSeparator) {
            FCDivider()
        }
    }
}
