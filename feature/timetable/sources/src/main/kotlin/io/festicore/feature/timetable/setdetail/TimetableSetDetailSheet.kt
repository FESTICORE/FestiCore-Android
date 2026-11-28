package io.festicore.feature.timetable.setdetail

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.festicore.domain.timetable.entity.TimetableSetDetail
import io.festicore.feature.timetable.TimetablePushGate
import io.festicore.feature.timetable.main.TimetableState
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.component.toast.FCToast
import io.festicore.ui.sharedui.sheet.FCSetSheet
import io.festicore.ui.sharedui.sheet.FCSetSheetState
import io.festicore.ui.sharedui.toast.FCToastItems
import java.time.Instant

/**
 * 공연 상세 시트(iOS `TimetableSetDetailView`). [state]가 있으면 탭 바까지 덮는 `FCSheet`로 띄운다.
 * [now]는 화면이 가진 현재 시각(분)이다. 열린 동안 시작 · 종료 시각이 지나면 상태가 바뀐다(iOS `TimelineView(.everyMinute)`).
 */
@Composable
internal fun TimetableSetDetailSheet(
    state: TimetableSetDetailState?,
    now: Instant?,
    onScheduleToggled: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    FCSheet(
        item = state,
        onDismissRequest = onDismissRequest,
    ) { sheet ->
        val detail = now?.let(sheet::detail) ?: sheet.content.detail
        FCSetSheet(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            artist = detail.artist,
            stage = detail.stage,
            time = detail.time,
            relativeTime = detail.relativeTime,
            isScheduled = detail.isScheduled,
            onIsScheduledChange = { onScheduleToggled() },
            state = sheetState(detail),
            memo = detail.memo,
            artistImageUrl = detail.imageUrl,
            source = sheet.content.source.ifEmpty { null },
            scheduleTitle = SCHEDULE_TITLE,
            scheduleHelper = scheduleHelper(sheet.content),
            isScheduleDisabled = sheet.content.isScheduleLoading,
            bottomStatus = { FCToast(FCToastItems.scheduleAdded()) },
            isBottomStatusVisible = sheet.isScheduleToastVisible,
        )
    }
}

private const val SCHEDULE_TITLE = "내 일정에 넣기"

/** 끝남 → 겹침 → 변경 → 기본(iOS `sheetState`). */
internal fun sheetState(detail: TimetableSetDetail): FCSetSheetState {
    if (detail.status == TimetableSetDetail.Status.Ended) return FCSetSheetState.Ended
    detail.overlap?.let { return FCSetSheetState.Overlap(it.warning) }
    val previousTime = detail.previousTime
    val change = detail.change
    if (previousTime != null && change != null) return FCSetSheetState.Changed(previousTime, change)
    return FCSetSheetState.Default
}

/**
 * 서버가 저장한 공연을 시작 10분 전에 알린다(Figma S03, iOS `scheduleHelper`).
 * 푸시 전([TimetablePushGate])에는 알림을 약속하지 않도록 기본 안내를 비운다(설계 "P0 전 임시 숨김").
 */
internal fun scheduleHelper(content: TimetableSetDetailContent, isPushReady: Boolean = TimetablePushGate.IS_PUSH_READY): String? = when {
    content.isScheduleLoading -> "내 일정을 불러오는 중이에요"
    content.isScheduleLimitReached -> "내 일정은 최대 ${TimetableState.SCHEDULE_LIMIT}개까지 저장할 수 있어요"
    isPushReady -> "공연 10분 전에 알려 드려요"
    else -> null
}
