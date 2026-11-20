package io.festicore.ui.designkit.component.selection

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.label.rememberReduceMotion
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private val Width = 51.dp
private val Height = 31.dp
private val KnobSize = 27.dp
private val KnobInset = 2.dp
private val ShadowElevation = 2.dp
private const val SHADOW_OPACITY = 0.15f
private const val ANIMATION_MILLIS = 200

/**
 * 켜기/끄기 스위치. 알림, 잠금화면 표시 같은 설정에 쓴다. iOS `isOn` 바인딩은 `isOn` + `onIsOnChange`로 받는다.
 * iOS 그림자(radius 2 · y 2 · 15%)는 Compose 그림자 높이 2로 그린다.
 */
@Composable
fun FCToggle(
    isOn: Boolean,
    onIsOnChange: (Boolean) -> Unit,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val knobOffset by animateDpAsState(
        targetValue = if (isOn) Width - KnobSize - KnobInset * 2 else 0.dp,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else ANIMATION_MILLIS),
        label = "FCToggleKnob",
    )
    val shadowColor = FestiCoreSemanticColor.staticBlack.color.copy(alpha = SHADOW_OPACITY)
    Box(
        modifier =
            modifier
                .size(Width, Height)
                .clip(RoundedCornerShape(Height / 2))
                .background(if (isOn) FestiCoreSemanticColor.primaryNormal.color else FestiCoreSemanticColor.fillStrong.color)
                .toggleable(value = isOn, interactionSource = null, indication = null, role = Role.Switch, onValueChange = onIsOnChange)
                .semantics {
                    contentDescription = accessibilityLabel
                    stateDescription = if (isOn) "켬" else "끔"
                },
    ) {
        Box(
            modifier =
                Modifier
                    .padding(KnobInset)
                    .offset(x = knobOffset)
                    .size(KnobSize)
                    .shadow(ShadowElevation, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .background(FestiCoreSemanticColor.primaryOnPrimary.color, CircleShape),
        )
    }
}
