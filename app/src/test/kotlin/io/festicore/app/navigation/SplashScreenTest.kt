package io.festicore.app.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// 앱 Application(Koin 시작)을 쓰지 않는다. 같은 JVM의 다른 Robolectric 테스트와 Koin이 겹치지 않게 한다.
@Config(sdk = [35], application = Application::class)
class SplashScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Lottie를 읽은 뒤 onAppear를 한 번만 보낸다`() {
        var appearCount = 0
        composeRule.setContent { SplashScreen(onAppear = { appearCount++ }) }

        // 내장 PNG 9개가 든 Splash.json(약 620KB) 해석은 CI 러너에서 수 초 걸릴 수 있어 넉넉히 기다린다.
        composeRule.waitUntil(timeoutMillis = 30_000) { appearCount > 0 }
        composeRule.waitForIdle()
        assertEquals(1, appearCount)
    }
}
