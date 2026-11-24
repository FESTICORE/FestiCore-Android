package io.festicore.ui.animation.lottie

import org.junit.Assert.assertEquals
import org.junit.Test

/** 스플래시 onAppear가 Lottie 읽기가 끝난 뒤 한 번만 가는지 본다. 실제 Splash.json 해석은 에뮬레이터에서 확인한다. */
class LoadFinishedNotifierTest {
    @Test
    fun `읽기가 끝나기 전에는 알리지 않는다`() {
        var count = 0
        val notifier = LoadFinishedNotifier { count++ }

        notifier.update(isComplete = false)

        assertEquals(0, count)
    }

    @Test
    fun `읽기가 끝나면 한 번만 알린다`() {
        var count = 0
        val notifier = LoadFinishedNotifier { count++ }

        notifier.update(isComplete = false)
        notifier.update(isComplete = true)
        notifier.update(isComplete = true)
        notifier.update(isComplete = false)
        notifier.update(isComplete = true)

        assertEquals(1, count)
    }
}
