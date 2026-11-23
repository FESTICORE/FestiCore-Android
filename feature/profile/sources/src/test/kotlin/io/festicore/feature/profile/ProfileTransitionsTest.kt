package io.festicore.feature.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTransitionsTest {
    @Test
    fun `push · pop은 라이브 탭과 같은 350ms FastOutSlowIn이다`() {
        assertEquals(350, ProfileTransitions.DURATION_MILLIS)
        assertEquals(FastOutSlowInEasing, ProfileTransitions.easing)
    }
}
