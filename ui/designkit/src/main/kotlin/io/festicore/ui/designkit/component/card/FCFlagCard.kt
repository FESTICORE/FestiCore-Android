package io.festicore.ui.designkit.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCZoneChip
import io.festicore.ui.designkit.component.media.FCThumbnail
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val ThumbnailSize = 56.dp
private val BorderWidth = 1.dp

/** 깃발 카드 문맥. [Board]는 라이브 보드의 지금 활동, [Directory]는 깃발 디렉토리의 팔로우 · 태그를 보인다. */
sealed interface FCFlagCardContext {
    data class Board(
        val activity: String,
        val time: String,
    ) : FCFlagCardContext

    data class Directory(
        val followers: String,
        val tags: List<String>,
        val isFollowing: Boolean,
        val onFollow: () -> Unit,
    ) : FCFlagCardContext
}

/** Figma Flag Card: 팔로우 전은 inverse, 팔로우 중은 secondary 버튼이다. */
fun fcFlagCardFollowStyle(isFollowing: Boolean): FCButtonStyle = if (isFollowing) FCButtonStyle.Secondary else FCButtonStyle.Inverse

/**
 * 깃발 카드. [status]가 null이면 상태 칩을 숨긴다. [thumbnail]은 56×56 둥근 사각형으로 잘리고, 기본은 [image]를 보이는 [FCThumbnail]이다.
 * [isFollowEnabled]는 요청 중 · 오프라인에서 끄고, [showsFollow] · [showsZone]은 디렉토리 카드의 팔로우 버튼 · 존 칩 표시 여부다.
 * [onTap]이 있으면 카드 빈 곳을 눌러 상세를 연다. 팔로우 · 존 버튼은 각자 동작한다.
 */
@Composable
fun FCFlagCard(
    name: String,
    status: FCStatusChipStatus?,
    zone: String,
    context: FCFlagCardContext,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    isFollowEnabled: Boolean = true,
    showsFollow: Boolean = true,
    showsZone: Boolean = true,
    onZoneTap: () -> Unit = {},
    onTap: (() -> Unit)? = null,
    thumbnail: @Composable () -> Unit = { FCThumbnail(image) },
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    Row(
        modifier =
            modifier
                .clip(shape)
                .then(
                    if (onTap != null) {
                        Modifier.clickable(interactionSource = null, indication = null, onClickLabel = "깃발 상세 보기", onClick = onTap)
                    } else {
                        Modifier
                    },
                ).background(FestiCoreSemanticColor.backgroundElevated.color)
                .border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape)
                .padding(FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        Box(modifier = Modifier.size(ThumbnailSize).clip(RoundedCornerShape(FestiCoreRadius.r12))) {
            thumbnail()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = FestiCoreTextStyle.baseHeadlineBold.textStyleWithoutLineBox.copy(
                        color = FestiCoreSemanticColor.labelNormal.color,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (status != null) FCStatusChip(status)
            }
            when (context) {
                is FCFlagCardContext.Board -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FCZoneChip(zone, onClick = onZoneTap)
                        BasicText(
                            text = context.activity,
                            style = FestiCoreTextStyle.baseFootnoteBold.textStyleWithoutLineBox.copy(
                                color = FestiCoreSemanticColor.labelNormal.color,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BasicText(
                            text = "· ${context.time}",
                            modifier = Modifier.weight(1f),
                            style =
                                FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                                    color = FestiCoreSemanticColor.labelAssistive.color,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                is FCFlagCardContext.Directory -> {
                    BasicText(
                        text = context.followers,
                        style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNeutral.color,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
                        context.tags.forEach { FCTag(it) }
                    }
                    if (showsZone) FCZoneChip(zone, onClick = onZoneTap)
                }
            }
        }
        if (context is FCFlagCardContext.Directory && showsFollow) {
            FCButton(
                title = if (context.isFollowing) "팔로잉" else "팔로우",
                style = fcFlagCardFollowStyle(context.isFollowing),
                size = FCButtonSize.Medium,
                isEnabled = isFollowEnabled,
                onClick = context.onFollow,
            )
        }
    }
}
