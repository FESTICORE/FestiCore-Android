package io.festicore.app.application

import android.app.Application
import android.content.Intent
import io.festicore.app.MainActivity
import io.festicore.app.deeplink.AppDeeplinkBridge
import io.festicore.core.storage.shared.PendingDeeplinkStore
import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FestivalWidgetNavigationTest {
    @Test fun `actual widget pending intents are accepted by cold and warm bridge`() = runTest {
        // Attach a base context without starting analytics or Koin.
        val app = FestiCoreApplication()
        shadowOf(app).callAttach(RuntimeEnvironment.getApplication())
        val store = object : PendingDeeplinkStore {
            var value: String? = null
            override suspend fun save(encoded: String) {
                value = encoded
            }
            override suspend fun consume(): String? = value.also { value = null }
        }
        val bridge = AppDeeplinkBridge(store, backgroundScope)
        for (id in listOf(null, "festival-a", "festival-b")) {
            val pending = app.widgetOpenIntent(id)
            val intent = shadowOf(pending).savedIntent
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(app.packageName, intent.`package`)
            assertTrue(pending.isImmutable)
            assertTrue(bridge.handleIntent(intent))
            runCurrent()
            assertEquals(id?.let { FestiCoreDeeplink.Festival(it) } ?: FestiCoreDeeplink.FestivalList, bridge.consumePending())
        }
    }
}
