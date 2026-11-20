package io.festicore.ui.designkit.component.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.label.FCPackDownloadProgress
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val MediaHeight = 140.dp
private val BookmarkSize = 36.dp
private val BookmarkIconSize = 20.dp
private val IconSize = 16.dp
private val BorderWidth = 1.dp

/** iOS `FCFestivalCard.Status` */
sealed interface FCFestivalCardStatus {
    /** 진행 전, 태그에 D-N을 표시한다. */
    data class DDay(val label: String) : FCFestivalCardStatus

    /** 마감 임박, 빨간 태그 */
    data class Closing(val label: String) : FCFestivalCardStatus

    /** 티켓 오픈 예정 */
    data class Upcoming(val label: String = "오픈 예정") : FCFestivalCardStatus

    /** 종료, 내용을 흐리게 보인다. */
    data class Ended(val label: String = "마감") : FCFestivalCardStatus
}

/** iOS `FCFestivalCard.Pack` */
sealed interface FCFestivalCardPack {
    /** 팩을 받을 수 없을 때의 안내 문구 */
    data class Unavailable(val text: String) : FCFestivalCardPack

    /** 받기 버튼 */
    data class Ready(
        val title: String = "팩 받기",
        val onClick: () -> Unit,
    ) : FCFestivalCardPack

    /** 받는 중. [progress]가 있으면 실측한 0..1 값, 없으면 전송 중 표시만 한다. */
    data class Downloading(
        val progress: Double?,
        val text: String,
    ) : FCFestivalCardPack

    data class Received(val text: String) : FCFestivalCardPack
}

/**
 * 페스티벌 카드. [media]의 기본은 [image]를 보이는 [FCFestivalImage]다.
 * [pack]은 팩 영역 상태(iOS `pack(_:)`), [isBooked] · [onBookmark]는 예매 여부와 북마크 동작(iOS `booked(_:onToggle:)`, 동작이 없으면 북마크를 숨긴다),
 * [ticketLinkTitle] · [onTicketLink]는 예매처 링크(iOS `ticketLink(_:action:)`, 동작이 없으면 링크 줄을 숨긴다)다.
 */
@Composable
fun FCFestivalCard(
    title: String,
    meta: String,
    status: FCFestivalCardStatus,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    pack: FCFestivalCardPack = FCFestivalCardPack.Unavailable("예매한 페스티벌로 등록하면 팩을 받을 수 있어요"),
    isBooked: Boolean = false,
    ticketLinkTitle: String = "예매처 바로가기",
    onBookmark: (() -> Unit)? = null,
    onTicketLink: (() -> Unit)? = null,
    media: @Composable () -> Unit = { FCFestivalImage(image) },
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    val isEnded = status is FCFestivalCardStatus.Ended
    val endedOpacity = if (isEnded) FestiCoreState.disabledOpacity else 1f
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(MediaHeight).clipToBounds()) {
            media()
            // Figma S00 Festival Card status=ended: 마감은 이미지 위에 Static Alpha/Black 40 레이어
            if (isEnded) {
                Box(modifier = Modifier.matchParentSize().background(FestiCoreSemanticColor.staticalphaBlack40.color))
            }
            if (onBookmark != null) {
                BookmarkButton(isBooked, onBookmark, Modifier.align(Alignment.TopEnd).padding(FestiCoreSpacing.paddingP8))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP12),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f).alpha(endedOpacity),
                    style = FestiCoreTextStyle.baseHeadlineBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusTag(status)
            }
            BasicText(
                text = meta,
                modifier = Modifier.alpha(endedOpacity),
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PackArea(pack)
        }
        if (onTicketLink != null) {
            TicketLink(ticketLinkTitle, onTicketLink, Modifier.alpha(endedOpacity))
        }
    }
}

@Composable
private fun BookmarkButton(
    isBooked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .semantics {
                    contentDescription = if (isBooked) "예매한 페스티벌 해제" else "예매한 페스티벌로 등록"
                    selected = isBooked
                }.size(BookmarkSize)
                .clip(CircleShape)
                .background(
                    if (isBooked) FestiCoreSemanticColor.staticalphaBlack40.color else FestiCoreSemanticColor.staticalphaBlack20.color,
                ).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_bookmark_20),
            contentDescription = null,
            modifier = Modifier.size(BookmarkIconSize),
            colorFilter =
                ColorFilter.tint(
                    if (isBooked) FestiCoreSemanticColor.staticLime.color else FestiCoreSemanticColor.staticalphaWhite60.color,
                ),
        )
    }
}

@Composable
private fun StatusTag(status: FCFestivalCardStatus) {
    val (label, background, foreground) =
        when (status) {
            is FCFestivalCardStatus.DDay ->
                Triple(status.label, FestiCoreSemanticColor.inverseBackground, FestiCoreSemanticColor.inverseLabel)

            is FCFestivalCardStatus.Closing ->
                Triple(status.label, FestiCoreSemanticColor.accentPointRed, FestiCoreSemanticColor.staticWhite)

            is FCFestivalCardStatus.Upcoming ->
                Triple(status.label, FestiCoreSemanticColor.fillStrong, FestiCoreSemanticColor.labelNormal)

            // Figma S00 Festival Card status=ended: 마감 태그는 라이트 · 다크 모두 Static/Gray
            is FCFestivalCardStatus.Ended ->
                Triple(status.label, FestiCoreSemanticColor.staticGray, FestiCoreSemanticColor.staticWhite)
        }
    BasicText(
        text = label,
        modifier =
            Modifier
                .background(background.color, RoundedCornerShape(FestiCoreRadius.r4))
                .padding(vertical = FestiCoreSpacing.paddingP4, horizontal = FestiCoreSpacing.marginM6),
        style = FestiCoreTextStyle.baseCaption2Bold.textStyle.copy(color = foreground.color),
        maxLines = 1,
    )
}

@Composable
private fun PackArea(pack: FCFestivalCardPack) {
    when (pack) {
        is FCFestivalCardPack.Unavailable ->
            BasicText(
                text = pack.text,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            )

        is FCFestivalCardPack.Ready ->
            FCButton(
                title = pack.title,
                style = FCButtonStyle.Accent,
                size = FCButtonSize.Medium,
                isFullWidth = true,
                onClick = pack.onClick,
            )

        is FCFestivalCardPack.Downloading -> FCPackDownloadProgress(pack.text, progress = pack.progress)

        is FCFestivalCardPack.Received ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_circle_check_16),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.statusPositive.color),
                )
                BasicText(
                    text = pack.text,
                    style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                )
            }
    }
}

@Composable
private fun TicketLink(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val color = FestiCoreSemanticColor.labelNeutral.color
    Box(modifier = modifier.fillMaxWidth().clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = title, style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = color))
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_arrow_up_right_from_square_16),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(color),
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(BorderWidth)
                    .background(FestiCoreSemanticColor.lineNormal.color),
        )
    }
}
