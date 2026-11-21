package io.festicore.feature.auth

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthTransitionsTest {
    @Test
    fun `push · pop은 iOS easeInOut 0_3초(베지어 0_42 · 0 · 0_58 · 1)다`() {
        assertEquals(300, AuthTransitions.DURATION_MILLIS)
        val iosEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { x ->
            assertEquals(iosEaseInOut.transform(x), AuthTransitions.easing.transform(x), 0.0001f)
        }
    }

    @Test
    fun `push는 아래 화면을 그대로 두고 pop은 아래 화면을 움직이지 않는다`() {
        assertEquals(AuthTransitions.holdUnderneath(), AuthTransitions.push().initialContentExit)
        assertEquals(EnterTransition.None, AuthTransitions.pop().targetContentEnter)
    }
}
