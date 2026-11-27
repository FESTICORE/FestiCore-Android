package io.festicore.feature.festival.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FestivalWidgetPinControllerTest {
    @Test fun `pin support and installed status come from launcher not stored preference`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        manager.setRequestPinAppWidgetSupported(true)
        manager.addBoundWidget(21, AppWidgetProviderInfo().apply { provider = ComponentName(context, FestivalWidgetProvider::class.java) })
        assertEquals(FestivalWidgetPinStatus(true, true), FestivalWidgetPinController(context).status())
    }

    @Test fun `supported request is submitted and unsupported request is rejected`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        manager.setRequestPinAppWidgetSupported(true)
        assertTrue(FestivalWidgetPinController(context).requestPin())
        manager.setRequestPinAppWidgetSupported(false)
        assertFalse(FestivalWidgetPinController(context).requestPin())
    }
}
