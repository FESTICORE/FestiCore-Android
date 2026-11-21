package io.festicore.feature.festival

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * 라이브 탭 스택 push · pop(iOS 시스템 push). iOS 시스템 값은 코드에 없어 설계 D8 제안값(350ms `FastOutSlowInEasing`, 아래 화면 1/3 이동)을 쓴다.
 * 목록만 있는 지금은 쌓이는 화면이 없고, 라이브 보드 단계에서 그대로 쓴다.
 */
internal object FestivalTransitions {
    const val DURATION_MILLIS = 350

    private fun slideSpec(): FiniteAnimationSpec<IntOffset> = tween(DURATION_MILLIS, easing = FastOutSlowInEasing)

    fun push(): ContentTransform = slideInHorizontally(slideSpec()) { it } togetherWith slideOutHorizontally(slideSpec()) { -it / 3 }

    fun pop(): ContentTransform = slideInHorizontally(slideSpec()) { -it / 3 } togetherWith slideOutHorizontally(slideSpec()) { it }
}
