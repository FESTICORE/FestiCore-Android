package io.festicore.ui.designkit.component.popup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.festicore.ui.designkit.component.overlay.FCCheckBadge
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private const val DIM_OPACITY = 0.8f
private const val MESSAGE_OPACITY = 0.8f
private const val FADE_MILLIS = 200

/** 확인 화면 한 건. 같은 문구도 새로 띄울 수 있게 인스턴스마다 다른 값으로 본다(iOS `id = UUID()`). */
class FCCheckOverlayItem(
    val title: String,
    val message: String,
)

/** 버튼을 누른 직후 잠깐 보이는 전체 화면 확인 */
@Composable
fun FCCheckOverlay(item: FCCheckOverlayItem, modifier: Modifier = Modifier) {
    // Figma Check Overlay: 체크 · 8 공간 · 제목 · 설명을 12 간격과 줄 높이 박스로 쌓는다.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.staticBlack.color.copy(alpha = DIM_OPACITY))
                .padding(FestiCoreSpacing.paddingP24)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FCCheckBadge()
        Spacer(modifier = Modifier.height(FestiCoreSpacing.paddingP8))
        BasicText(
            text = item.title,
            style =
                FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(
                    color = FestiCoreSemanticColor.staticWhite.color,
                    textAlign = TextAlign.Center,
                ),
        )
        BasicText(
            text = item.message,
            style =
                FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(
                    color = FestiCoreSemanticColor.staticWhite.color.copy(alpha = MESSAGE_OPACITY),
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

/**
 * iOS `View.fcCheckOverlay(item:duration:)`. [item]이 있으면 [content] 위에 확인 화면을 띄우고 햅틱을 준 뒤
 * [duration] 뒤 `onItemChange(null)`로 비운다. 그 사이 다른 아이템으로 바뀌면 새 아이템 기준으로 다시 잰다.
 */
@Composable
fun FCCheckOverlayHost(
    item: FCCheckOverlayItem?,
    onItemChange: (FCCheckOverlayItem?) -> Unit,
    modifier: Modifier = Modifier,
    duration: Duration = 1.seconds,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnItemChange by rememberUpdatedState(onItemChange)
    val currentItem by rememberUpdatedState(item)
    Box(modifier = modifier) {
        content()
        // 사라지는 동안에도 마지막 내용을 그리도록 아이템 전환을 AnimatedContent에 맡긴다.
        AnimatedContent(
            targetState = item,
            modifier = Modifier.matchParentSize(),
            transitionSpec = {
                fadeIn(tween(FADE_MILLIS, easing = EaseOut)) togetherWith fadeOut(tween(FADE_MILLIS, easing = EaseOut))
            },
            label = "FCCheckOverlay",
        ) { shown ->
            if (shown != null) FCCheckOverlay(shown)
        }
    }
    if (item != null) {
        LaunchedEffect(item) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(duration)
            if (currentItem === item) currentOnItemChange(null)
        }
    }
}
