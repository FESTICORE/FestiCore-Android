package io.festicore.feature.profile

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * 마이 탭 스택 push · pop(iOS `ProfileCoordinatorView`의 시스템 push). 라이브 · 깃발 탭(`FestivalTransitions`)과 같은 값이다:
 * iOS 시스템 값은 코드에 없어 설계 D8 제안값(350ms `FastOutSlowInEasing`, 아래 화면 1/3 이동)을 쓴다.
 * push는 새 화면이 오른쪽에서 들어오고, pop은 위 화면이 오른쪽으로 나간다.
 */
internal object ProfileTransitions {
    const val DURATION_MILLIS = 350

    val easing: Easing = FastOutSlowInEasing

    private fun slideSpec(): FiniteAnimationSpec<IntOffset> = tween(DURATION_MILLIS, easing = easing)

    fun push(): ContentTransform = slideInHorizontally(slideSpec()) { it } togetherWith slideOutHorizontally(slideSpec()) { -it / 3 }

    fun pop(): ContentTransform = slideInHorizontally(slideSpec()) { -it / 3 } togetherWith slideOutHorizontally(slideSpec()) { it }
}
