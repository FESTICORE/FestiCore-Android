package io.festicore.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LaunchTransitionsTest {
    private val rootSpring = spring<Float>(dampingRatio = 0.94f, stiffness = LaunchTransitions.ROOT_SPRING_STIFFNESS)

    @Test
    fun `루트 스프링은 iOS response 0_52 · dampingFraction 0_94를 stiffness = (2π÷response)²로 옮긴다`() {
        assertEquals(0.94f, LaunchTransitions.ROOT_SPRING_DAMPING_RATIO)
        assertEquals(146.0f, LaunchTransitions.ROOT_SPRING_STIFFNESS, 0.1f)
        assertEquals(rootSpring, LaunchTransitions.rootSpring<Float>())
    }

    @Test
    fun `덮개는 0_35초 · 재확인 가입 덮개는 0_3초 easeInOut(베지어 0_42 · 0 · 0_58 · 1)이다`() {
        val iosEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f).forEach { x ->
            assertEquals(iosEaseInOut.transform(x), LaunchTransitions.easing.transform(x), 0.0001f)
        }
        assertEquals(tween<Float>(350, easing = iosEaseInOut), LaunchTransitions.coverTween<Float>())
        assertEquals(tween<Float>(300, easing = iosEaseInOut), LaunchTransitions.signUpCoverTween<Float>())
    }

    @Test
    fun `스플래시는 흐려지며 0_98로 줄고 온보딩은 흐려지며 들어온다(N1)`() {
        assertEquals(fadeOut(rootSpring) + scaleOut(rootSpring, targetScale = 0.98f), LaunchTransitions.rootExit(RootScreen.Splash))
        assertEquals(fadeIn(rootSpring), LaunchTransitions.rootEnter(RootScreen.Onboarding, isLaunchLoginPresented = false))
        assertEquals(fadeOut(rootSpring), LaunchTransitions.rootExit(RootScreen.Onboarding))
    }

    @Test
    fun `홈은 첫 진입 덮개가 있으면 흐려지며 들어오고 없으면 오른쪽에서 밀려 들어온다(N2 · N3)`() {
        assertEquals(fadeIn(rootSpring), LaunchTransitions.rootEnter(RootScreen.Home, isLaunchLoginPresented = true))
        val slide = LaunchTransitions.rootEnter(RootScreen.Home, isLaunchLoginPresented = false)
        assertNotEquals(fadeIn(rootSpring), slide)
        assertNotEquals(EnterTransition.None, slide)
    }

    @Test
    fun `화면을 알 수 없으면 전환 없이 바꾼다`() {
        assertEquals(EnterTransition.None, LaunchTransitions.rootEnter(null, isLaunchLoginPresented = false))
        assertEquals(ExitTransition.None, LaunchTransitions.rootExit(null))
    }
}
