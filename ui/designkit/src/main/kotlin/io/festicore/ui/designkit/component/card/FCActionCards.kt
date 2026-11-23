package io.festicore.ui.designkit.component.card

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val NextSetProgressHeight = 4.dp
private val HomeIconSize = 16.dp
private val HomeRideLineSpacing = 10.dp

// iOS FCDoneCard 실측 간격
private val DoneTopInset = 17.dp
private val DoneHeaderToStats = 18.dp
private val DoneBottomInset = 22.dp

private val EmptyIconSize = 24.dp
private val EmptyIconBackgroundSize = 48.dp
private val EmptyCloseIconSize = 16.dp
private val EmptyCloseTouchSize = 44.dp

/** 카드 아래 버튼 하나. 버튼 스타일 기본은 secondary다. */
data class FCCardAction(
    val title: String,
    val style: FCButtonStyle = FCButtonStyle.Secondary,
    val onClick: () -> Unit,
)

@Composable
private fun FCCardActionRow(actions: List<FCCardAction>) {
    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        actions.forEach { item ->
            FCButton(
                title = item.title,
                modifier = Modifier.weight(1f),
                style = item.style,
                size = FCButtonSize.Medium,
                isFullWidth = true,
                onClick = item.onClick,
            )
        }
    }
}

/** 내 다음 일정 카드. [progress]는 0..1로 자른다. [label]은 위 라벨(iOS `label(_:)`)이다. */
@Composable
fun FCNextSetCard(
    artist: String,
    meta: String,
    remain: String,
    updated: String,
    progress: Double,
    modifier: Modifier = Modifier,
    label: String = "내 다음 일정",
) {
    val ink = FestiCoreSemanticColor.staticInk.color
    val subtle = FestiCoreSemanticColor.staticalphaBlack60.color
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .background(FestiCoreSemanticColor.staticLime.color, RoundedCornerShape(FestiCoreRadius.r20))
                .padding(FestiCoreSpacing.paddingP20),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = label, style = FestiCoreTextStyle.baseFootnoteBold.textStyleWithoutLineBox.copy(color = ink))
            Spacer(modifier = Modifier.weight(1f))
            BasicText(text = updated, style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(color = subtle))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
            ) {
                BasicText(
                    text = artist,
                    style = FestiCoreTextStyle.baseTitle1Bold.textStyleWithoutLineBox.copy(color = ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = meta,
                    style = FestiCoreTextStyle.baseSubheadlineRegular.textStyleWithoutLineBox.copy(color = subtle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(text = remain, style = FestiCoreTextStyle.pointTitle.textStyleWithoutLineBox.copy(color = ink))
        }
        BoxWithConstraints(
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    .fillMaxWidth()
                    .height(NextSetProgressHeight)
                    .clip(CircleShape)
                    .background(FestiCoreSemanticColor.staticalphaBlack20.color),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(maxWidth * progress.coerceIn(0.0, 1.0).toFloat())
                        .fillMaxHeight()
                        .background(ink),
            )
        }
    }
}

/** 귀가 카드의 버튼 스타일. 다크 모드에서는 잉크 계열, 라이트에서는 secondary · accent로 바꾼다. */
internal fun fcHomeCardActionStyle(style: FCButtonStyle, isDark: Boolean): FCButtonStyle = when (style) {
    FCButtonStyle.Secondary, FCButtonStyle.Ghost, FCButtonStyle.InkOutline ->
        if (isDark) FCButtonStyle.InkOutline else FCButtonStyle.Secondary

    FCButtonStyle.Primary, FCButtonStyle.Accent, FCButtonStyle.Inverse, FCButtonStyle.Ink ->
        if (isDark) FCButtonStyle.Ink else FCButtonStyle.Accent
}

/** 귀가 카드. 셔틀 · 기차 줄은 null이면 숨긴다. [label]은 위 라벨(iOS `label`)이다. */
@Composable
fun FCHomeCard(
    remain: String,
    shuttle: String?,
    train: String?,
    actions: List<FCCardAction>,
    modifier: Modifier = Modifier,
    label: String = "귀가",
) {
    val foreground = FestiCoreSemanticColor.inverseLabel.color
    val isDark = isSystemInDarkTheme()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.inverseBackground.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(FestiCoreSpacing.paddingP20),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_route_16),
                contentDescription = null,
                modifier = Modifier.size(HomeIconSize),
                colorFilter = ColorFilter.tint(foreground),
            )
            BasicText(text = label, style = FestiCoreTextStyle.baseFootnoteBold.textStyleWithoutLineBox.copy(color = foreground))
        }
        BasicText(text = remain, style = FestiCoreTextStyle.pointHeading.textStyleWithoutLineBox.copy(color = foreground))
        Column(verticalArrangement = Arrangement.spacedBy(HomeRideLineSpacing)) {
            val rideStyle = FestiCoreTextStyle.baseCalloutMedium.textStyleWithoutLineBox.copy(color = foreground)
            if (shuttle != null) BasicText(text = shuttle, style = rideStyle)
            if (train != null) BasicText(text = train, style = rideStyle)
        }
        FCCardActionRow(actions.map { it.copy(style = fcHomeCardActionStyle(it.style, isDark)) })
    }
}

/** 하루 마무리 카드의 통계 한 칸. */
data class FCDoneCardStat(
    val value: String,
    val label: String,
)

/** 하루 마무리 카드. */
@Composable
fun FCDoneCard(
    title: String,
    sub: String,
    stats: List<FCDoneCardStat>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(
                    start = FestiCoreSpacing.paddingP20,
                    end = FestiCoreSpacing.paddingP20,
                    top = DoneTopInset,
                    bottom = DoneBottomInset,
                ),
        verticalArrangement = Arrangement.spacedBy(DoneHeaderToStats),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            BasicText(
                text = title,
                style = FestiCoreTextStyle.pointHeading.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNormal.color),
            )
            BasicText(
                text = sub,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelAssistive.color,
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            stats.forEach { stat ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {}
                            .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r12))
                            .padding(FestiCoreSpacing.paddingP12),
                    verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6),
                ) {
                    BasicText(
                        text = stat.value,
                        style = FestiCoreTextStyle.baseTitle2Bold.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNormal.color,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        text = stat.label,
                        style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNeutral.color,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 빈 상태 카드. [action]이 있으면 아래 버튼을 보이고(iOS `action(_:)`), [onClose]가 있으면 오른쪽 위 닫기 버튼을 보인다(iOS `onClose(_:)`).
 */
@Composable
fun FCEmptyCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_users_24,
    action: FCCardAction? = null,
    onClose: (() -> Unit)? = null,
) {
    Box(modifier = modifier.background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FestiCoreSpacing.paddingP20,
                        end = FestiCoreSpacing.paddingP20,
                        top = FestiCoreSpacing.paddingP20,
                        bottom = if (action == null) FestiCoreSpacing.paddingP20 else FestiCoreSpacing.paddingP16,
                    ),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {}
                        .size(EmptyIconBackgroundSize)
                        .background(FestiCoreSemanticColor.fillNormal.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(EmptyIconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNeutral.color),
                )
            }
            Column(
                modifier = Modifier.padding(top = FestiCoreSpacing.paddingP4),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = title,
                    style =
                        FestiCoreTextStyle.baseHeadlineBold.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNormal.color,
                            textAlign = TextAlign.Center,
                        ),
                )
                BasicText(
                    text = message,
                    style =
                        FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNeutral.color,
                            textAlign = TextAlign.Center,
                        ),
                )
            }
            if (action != null) {
                FCButton(title = action.title, style = action.style, size = FCButtonSize.Medium, onClick = action.onClick)
            }
        }
        if (onClose != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(FestiCoreSpacing.paddingP4)
                        .size(EmptyCloseTouchSize)
                        .semantics { contentDescription = "안내 닫기" }
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_xmark_16),
                    contentDescription = null,
                    modifier = Modifier.size(EmptyCloseIconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                )
            }
        }
    }
}

/** 안내 섹션. 제목은 접근성 제목이고, [actions]가 있으면 아래 버튼 줄을 보인다. */
@Composable
fun FCGuideSection(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: List<FCCardAction> = emptyList(),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        BasicText(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = FestiCoreTextStyle.baseHeadlineBold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = message,
            style = FestiCoreTextStyle.baseCalloutRegular.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
        if (actions.isNotEmpty()) {
            Box(modifier = Modifier.padding(top = FestiCoreSpacing.paddingP4)) { FCCardActionRow(actions) }
        }
    }
}
