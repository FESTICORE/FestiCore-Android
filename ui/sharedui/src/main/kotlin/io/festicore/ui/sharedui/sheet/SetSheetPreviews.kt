package io.festicore.ui.sharedui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.ui.designkit.component.toast.FCToast
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.sharedui.toast.FCToastItems

/** 프리뷰 가짜 값: 실제 서버를 부르지 않는다. */
private const val PREVIEW_IMAGE_URL = "https://example.com/artist.webp"

@Preview(name = "FCSetSheet", heightDp = 1600)
@Composable
private fun SetSheetPreview() {
    var isScheduled by remember { mutableStateOf(true) }
    Column(
        modifier =
            Modifier
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
    ) {
        FCSetSheet(
            artist = "라임플래그스",
            stage = "리벳 스테이지",
            time = "17:00–17:50 · 50분",
            relativeTime = "1시간 32분 후",
            isScheduled = isScheduled,
            onIsScheduledChange = { isScheduled = it },
            memo = "앵콜 없이 정시에 끝나요. 리벳 앞 왼쪽이 덜 붐벼요.",
            artistImageUrl = PREVIEW_IMAGE_URL,
            source = "출처: 주최측 공지 · 확인 10/16 14:00",
            onStageClick = {},
        )
        FCSetSheet(
            artist = "라임플래그스",
            stage = "리벳 스테이지",
            time = "17:00–17:50 · 50분",
            relativeTime = "1시간 32분 후",
            isScheduled = isScheduled,
            onIsScheduledChange = { isScheduled = it },
            state = FCSetSheetState.Overlap("크라잉버드(원더 스테이지 15:55–16:45)와 35분 겹쳐요"),
        )
        FCSetSheet(
            artist = "라임플래그스",
            stage = "리벳 스테이지",
            time = "17:00–17:50 · 50분",
            relativeTime = "1시간 32분 후",
            isScheduled = isScheduled,
            onIsScheduledChange = { isScheduled = it },
            state = FCSetSheetState.Changed(oldTime = "16:40", change = "20분 미뤄짐"),
        )
        FCSetSheet(
            artist = "라임플래그스",
            stage = "리벳 스테이지",
            time = "17:00–17:50 · 50분",
            relativeTime = "",
            isScheduled = false,
            onIsScheduledChange = {},
            state = FCSetSheetState.Ended,
        )
    }
}

@Preview(name = "FCToast 프리셋")
@Composable
private fun ToastPresetPreview() {
    Column(modifier = Modifier.padding(FestiCoreSpacing.paddingP16)) {
        FCToast(FCToastItems.scheduleAdded())
    }
}
