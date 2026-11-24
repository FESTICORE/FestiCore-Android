package io.festicore.ui.animation.lottie

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class FestiCoreLottieSpeedTest {
    @Test
    fun `목표 길이를 주면 고유 길이를 목표 길이로 나눈 속도로 재생한다`() {
        // Splash.json: 0~180프레임 60fps = 3초, iOS duration(2) → 1.5배
        assertEquals(1.5f, lottieSpeed(intrinsicSeconds = 3.0, target = 2.seconds), 0f)
    }

    @Test
    fun `목표 길이가 없거나 길이가 유효하지 않으면 1배로 재생한다`() {
        assertEquals(1f, lottieSpeed(intrinsicSeconds = 3.0, target = null), 0f)
        assertEquals(1f, lottieSpeed(intrinsicSeconds = null, target = 2.seconds), 0f)
        assertEquals(1f, lottieSpeed(intrinsicSeconds = 0.0, target = 2.seconds), 0f)
        assertEquals(1f, lottieSpeed(intrinsicSeconds = 3.0, target = 0.seconds), 0f)
    }
}
