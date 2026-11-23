package io.festicore.ui.designkit.component.toast

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private val IconSize = 16.dp
private const val ANIMATION_MILLIS = 250

/** 토스트 한 건. 같은 문구도 새로 띄울 수 있게 인스턴스마다 다른 값으로 본다(iOS `id = UUID()`). */
class FCToastItem(
    @param:DrawableRes val icon: Int,
    val message: String,
)

/** 화면 상단에 표시하는 짧은 알림. [iconColor]는 iOS `iconColor(_:)` 설정이다. */
@Composable
fun FCToast(
    item: FCToastItem,
    modifier: Modifier = Modifier,
    iconColor: Color = FestiCoreSemanticColor.staticWhite.color,
) {
    Row(
        modifier =
            modifier
                .background(FestiCoreSemanticColor.staticalphaBlack80.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(item.icon),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(iconColor),
        )
        BasicText(
            text = item.message,
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.staticWhite.color),
        )
    }
}

/**
 * iOS `View.fcToast(item:duration:topPadding:)`. [item]이 있으면 [content] 위쪽에 토스트를 띄우고
 * [duration] 뒤 `onItemChange(null)`로 비운다. 그 사이 다른 아이템으로 바뀌면 새 아이템 기준으로 다시 잰다.
 */
@Composable
fun FCToastHost(
    item: FCToastItem?,
    onItemChange: (FCToastItem?) -> Unit,
    modifier: Modifier = Modifier,
    duration: Duration = 2.seconds,
    topPadding: Dp = 30.dp,
    content: @Composable () -> Unit,
) {
    val currentOnItemChange by rememberUpdatedState(onItemChange)
    val currentItem by rememberUpdatedState(item)
    Box(modifier = modifier) {
        content()
        AnimatedContent(
            targetState = item,
            modifier = Modifier.align(Alignment.TopCenter),
            transitionSpec = {
                val spec = tween<Float>(ANIMATION_MILLIS, easing = EaseOut)
                (slideInVertically(tween(ANIMATION_MILLIS, easing = EaseOut)) { -it } + fadeIn(spec)) togetherWith
                    (slideOutVertically(tween(ANIMATION_MILLIS, easing = EaseOut)) { -it } + fadeOut(spec))
            },
            contentAlignment = Alignment.TopCenter,
            label = "FCToast",
        ) { shown ->
            if (shown != null) FCToast(shown, modifier = Modifier.padding(top = topPadding))
        }
    }
    if (item != null) {
        LaunchedEffect(item) {
            delay(duration)
            if (currentItem === item) currentOnItemChange(null)
        }
    }
}
