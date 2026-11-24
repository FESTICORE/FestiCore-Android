package io.festicore.feature.timetable

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
 * 타임테이블 탭 스택 push · pop(iOS `TimetableCoordinatorView`의 TCAFlow push, 설계 T12). 라이브 · 깃발 · 마이 탭과 같은 값이다:
 * 350ms `FastOutSlowInEasing`, push는 새 화면이 오른쪽에서 들어오고 아래 화면은 1/3 왼쪽으로, pop은 위 화면이 오른쪽으로 나간다.
 */
internal object TimetableTransitions {
    const val DURATION_MILLIS = 350

    val easing: Easing = FastOutSlowInEasing

    private fun slideSpec(): FiniteAnimationSpec<IntOffset> = tween(DURATION_MILLIS, easing = easing)

    fun push(): ContentTransform = slideInHorizontally(slideSpec()) { it } togetherWith slideOutHorizontally(slideSpec()) { -it / 3 }

    fun pop(): ContentTransform = slideInHorizontally(slideSpec()) { -it / 3 } togetherWith slideOutHorizontally(slideSpec()) { it }
}
