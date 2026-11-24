package io.festicore.feature.timetable.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.domain.timetable.entity.TimetableLineupDay
import io.festicore.feature.timetable.TimetablePushGate
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCLineupRow
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage
import java.util.Locale

private val PlaceholderIconSize = 20.dp

/** 시간표 공개 전 안내와 라인업(iOS `TimetableUnpublishedView`). */
@Composable
internal fun TimetableUnpublished(
    lineup: List<TimetableLineupDay>,
    notificationStatus: TimetableNotificationStatus,
    onNotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = FestiCoreSpacing.paddingP16, end = FestiCoreSpacing.paddingP16, top = FestiCoreSpacing.paddingP16)
                .fcScrollBottomInset(),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24 + FestiCoreSpacing.marginM2),
    ) {
        FCEmptyCard(
            title = "시간표가 공개되면 알려 드릴게요",
            message = notificationMessage(notificationStatus),
            modifier =
                Modifier
                    .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                    .padding(bottom = FestiCoreSpacing.marginM6),
            icon = R.drawable.ic_bell,
            action = notifyAction(notificationStatus, onNotify),
        )
        lineup.forEach { day -> LineupSection(day) }
    }
}

/** "알림 받기"(저장 중이면 "설정 중" · 무동작). 팩 알림 켜기(A1)와 푸시(P0) 전에는 눌러도 아무 일이 없어 숨긴다([TimetablePushGate]). */
internal fun notifyAction(
    status: TimetableNotificationStatus,
    onNotify: () -> Unit,
    isPushReady: Boolean = TimetablePushGate.IS_PUSH_READY,
): FCCardAction? {
    if (!isPushReady) return null
    val isSaving = status == TimetableNotificationStatus.Saving
    return FCCardAction(if (isSaving) "설정 중" else "알림 받기", FCButtonStyle.Primary) { if (!isSaving) onNotify() }
}

internal fun notificationMessage(status: TimetableNotificationStatus): String = when (status) {
    TimetableNotificationStatus.Idle -> "대략적인 라인업은 먼저 확인할 수 있어요.\n시간표 공개 후 원하는 무대를 선택해 내 일정에 넣어 보세요."
    TimetableNotificationStatus.Saving -> "알림 설정을 저장하고 있어요."
    TimetableNotificationStatus.Enabled -> "팩 업데이트 알림을 켰어요."
    TimetableNotificationStatus.PermissionRequired -> "팩 업데이트 알림을 켰어요. 기기 설정에서도 알림을 허용해 주세요."
    TimetableNotificationStatus.Failed -> "알림 설정을 저장하지 못했어요. 다시 시도해 주세요."
}

@Composable
private fun LineupSection(day: TimetableLineupDay) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
        BasicText(
            text = day.title,
            modifier = Modifier.semantics { heading() },
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(FestiCoreRadius.r16))) {
            day.artists.forEachIndexed { index, artist ->
                FCLineupRow(
                    number = String.format(Locale.ROOT, "%02d", index + 1),
                    artist = artist.name,
                    showsSeparator = index < day.artists.size - 1,
                    // 사진이 없거나 불러오지 못한 아티스트도 같은 자리에 기본 모양을 두어 줄이 어긋나지 않게 한다.
                    thumbnail = {
                        FCRemoteImage(
                            url = artist.imageUrl,
                            modifier = Modifier.fillMaxSize(),
                            placeholderIcon = R.drawable.ic_music_20,
                            placeholderIconSize = PlaceholderIconSize,
                        )
                    },
                )
            }
        }
    }
}
