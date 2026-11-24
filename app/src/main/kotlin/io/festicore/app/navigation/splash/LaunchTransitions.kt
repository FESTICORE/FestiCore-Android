package io.festicore.app.navigation.splash

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlin.math.PI

/** 루트 화면 종류. `NavDisplay` 전환에서 들어오는 · 나가는 화면을 알도록 엔트리 metadata에 싣는다. */
internal enum class RootScreen {
    Splash,
    Onboarding,
    Home,
}

/**
 * 시작 흐름 화면 전환 값(iOS `AppCoordinatorView` · `MainTabView`).
 *
 * - 루트(N1~N3): iOS `spring(response: 0.52, dampingFraction: 0.94, blendDuration: 0.14)`.
 *   Compose에는 response가 없어 질량 1 기준 stiffness = (2π / response)²로 옮긴 근사다(≈ 146). blendDuration은 대응 값이 없어 뺐다.
 * - 덮개(N4 · N5): `easeInOut(duration: 0.35)`, 재확인 가입 덮개(N8): `easeInOut(duration: 0.3)`.
 *   SwiftUI `easeInOut`은 Compose [EaseInOut](`CubicBezierEasing(0.42, 0, 0.58, 1)`)으로 옮긴 근사다.
 */
internal object LaunchTransitions {
    const val ROOT_SPRING_RESPONSE_SECONDS = 0.52f
    const val ROOT_SPRING_DAMPING_RATIO = 0.94f
    val ROOT_SPRING_STIFFNESS: Float = (2 * PI / ROOT_SPRING_RESPONSE_SECONDS).let { it * it }.toFloat()
    const val SPLASH_EXIT_SCALE = 0.98f

    const val COVER_DURATION_MILLIS = 350
    const val SIGN_UP_COVER_DURATION_MILLIS = 300
    val easing: Easing = EaseInOut

    /** 엔트리 metadata 키. 값은 [RootScreen]. */
    const val ROOT_SCREEN_KEY = "io.festicore.app.rootScreen"

    fun <T> rootSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = ROOT_SPRING_DAMPING_RATIO, stiffness = ROOT_SPRING_STIFFNESS)

    fun <T> coverTween(): FiniteAnimationSpec<T> = tween(COVER_DURATION_MILLIS, easing = easing)

    fun <T> signUpCoverTween(): FiniteAnimationSpec<T> = tween(SIGN_UP_COVER_DURATION_MILLIS, easing = easing)

    /**
     * 루트 화면 바꿈. SwiftUI처럼 들어오는 화면은 자기 등장 전환을, 나가는 화면은 자기 퇴장 전환을 쓴다.
     * 들어오는 화면이 위에 그려진다(Navigation 3는 스택을 통째로 바꾸면 pop이 아니라 [transitionSpec]을 쓰고 새 화면 zIndex를 올린다).
     */
    fun root(
        initial: RootScreen?,
        target: RootScreen?,
        isLaunchLoginPresented: Boolean,
    ): ContentTransform = rootEnter(target, isLaunchLoginPresented) togetherWith rootExit(initial)

    /** 온보딩 `opacity`, 홈은 첫 진입 덮개가 있으면 `opacity`, 없으면 `move(edge: .trailing)`. */
    fun rootEnter(target: RootScreen?, isLaunchLoginPresented: Boolean): EnterTransition = when (target) {
        RootScreen.Onboarding -> fadeIn(rootSpring())
        RootScreen.Home -> if (isLaunchLoginPresented) fadeIn(rootSpring()) else slideInHorizontally(rootSpring()) { it }
        RootScreen.Splash, null -> EnterTransition.None
    }

    /** 스플래시 `opacity + scale(0.98)`, 온보딩 `opacity`, 홈 `move(edge: .leading)`. */
    fun rootExit(initial: RootScreen?): ExitTransition = when (initial) {
        RootScreen.Splash -> fadeOut(rootSpring()) + scaleOut(rootSpring(), targetScale = SPLASH_EXIT_SCALE)
        RootScreen.Onboarding -> fadeOut(rootSpring())
        RootScreen.Home -> slideOutHorizontally(rootSpring()) { -it }
        null -> ExitTransition.None
    }

    /** 첫 진입 덮개: 앱을 켤 때 서서히 보이고, 흐름을 마치면 메인 탭에 밀려 왼쪽으로 나간다. */
    fun launchCoverEnter(): EnterTransition = fadeIn(coverTween())

    fun launchCoverExit(): ExitTransition = slideOutHorizontally(coverTween()) { -it }

    /** 재확인 가입 덮개: 오른쪽에서 들어오고 오른쪽으로 나간다. 메인 탭은 제자리. */
    fun signUpCover(): ContentTransform =
        slideInHorizontally(signUpCoverTween()) { it } togetherWith slideOutHorizontally(signUpCoverTween()) { it }
}
