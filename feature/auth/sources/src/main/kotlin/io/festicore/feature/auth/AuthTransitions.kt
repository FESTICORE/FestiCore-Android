package io.festicore.feature.auth

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * 덮개 흐름 스택의 push · pop 전환(iOS `MainTabView.launchFlow()`의 `.move(edge: .trailing)` + `easeInOut(duration: 0.3)`).
 * push는 새 화면이 오른쪽에서 들어와 아래 화면 위를 덮고 아래 화면은 그대로 둔다. pop은 맨 위 화면이 오른쪽으로 빠지고 아래 화면이 상태 그대로 드러난다.
 * SwiftUI `easeInOut`은 Compose [EaseInOut](`CubicBezierEasing(0.42, 0, 0.58, 1)`)으로 옮긴 근사다.
 */
internal object AuthTransitions {
    const val DURATION_MILLIS = 300
    val easing: Easing = EaseInOut

    private fun slideSpec(): FiniteAnimationSpec<IntOffset> = tween(DURATION_MILLIS, easing = easing)

    /** 아래 화면은 움직이지 않고 push가 끝날 때까지 남긴다(`ExitTransition.KeepUntilTransitionsFinished`는 공개 API가 아니라 불투명도 1 유지로 대신한다). */
    fun holdUnderneath(): ExitTransition = fadeOut(tween(DURATION_MILLIS), targetAlpha = 1f)

    fun push(): ContentTransform = slideInHorizontally(slideSpec()) { it } togetherWith holdUnderneath()

    /** Navigation 3는 pop 때 나가는 화면을 위에 그린다(zIndex를 낮춘 쪽이 들어오는 화면). predictive back도 같은 전환을 손가락 진행도로 감는다. */
    fun pop(): ContentTransform = EnterTransition.None togetherWith slideOutHorizontally(slideSpec()) { it }
}
