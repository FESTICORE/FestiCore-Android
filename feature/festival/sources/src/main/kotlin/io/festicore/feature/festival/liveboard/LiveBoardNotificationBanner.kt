package io.festicore.feature.festival.liveboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.domain.usernotification.UserNotificationItem
import io.festicore.feature.festival.inbox.NotificationInboxPresentation
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import java.time.Instant

private val IconBoxSize = 40.dp
private val IconSize = 16.dp

/**
 * 보드 상단 새 알림 배너(iOS `FestivalLiveBoardScreen.notificationBanner`). 누르면 종 아이콘과 같다(알림함).
 * 시각은 [now](기기 시각)로 알림함과 같은 규칙으로 적는다.
 */
@Composable
internal fun LiveBoardNotificationBanner(
    notification: UserNotificationItem,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FestiCoreRadius.r20))
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .clickable(onClickLabel = "알림함으로 이동합니다", onClick = onClick)
                .padding(FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(IconBoxSize)
                    .background(FestiCoreSemanticColor.primaryNormal.color, RoundedCornerShape(FestiCoreRadius.r12)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(NotificationInboxPresentation.icon(notification.type)),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.primaryOnPrimary.color),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    style = FestiCoreTextStyle.baseCalloutBold.textStyle,
                    color = FestiCoreSemanticColor.labelNormal.color,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = NotificationInboxPresentation.time(notification, now),
                    style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                )
            }
            Text(
                text = notification.body,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                color = FestiCoreSemanticColor.labelNeutral.color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
