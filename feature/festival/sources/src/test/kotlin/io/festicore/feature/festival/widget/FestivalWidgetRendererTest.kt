package io.festicore.feature.festival.widget

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Outline
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.festicore.feature.festival.sources.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FestivalWidgetRendererTest {
    @Test fun compactSizesKeepTheActionInsideTheWidget() {
        val context = RuntimeEnvironment.getApplication()
        val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE)
        for ((size, width) in listOf(FestivalWidgetSize.Small to 150, FestivalWidgetSize.Medium to 280)) {
            val model = FestivalWidgetPresentation(WidgetPhase.Playing, "긴 공연 제목 두 줄까지", "23:30–01:00 공연 시간", info = "긴 무대 이름과 다음 공연 정보")
            val view = createWidgetViews(context, model, size, open).apply(context, FrameLayout(context))
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(150, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, 150)
            val footer = view.findViewById<TextView>(R.id.widget_footer)
            assertTrue("$size footer bottom=${footer.bottom}", footer.bottom <= 150 - view.paddingBottom)
            assertTrue("$size action keeps readable height", footer.height >= 34)
        }
    }

    @Test fun headerBitmapContainsGlyphsWithinBoundedMemory() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = widgetHeadingBitmap(context, "곧 시작해요! 긴 축제 제목도 안전하게", 300, 22f, 2)
        assertTrue(bitmap.width >= 120)
        assertTrue(bitmap.width <= 512)
        assertTrue(bitmap.height <= 192)
        assertTrue(bitmap.allocationByteCount <= 512 * 192 * 4)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.any { android.graphics.Color.alpha(it) > 0 })
    }

    @Test fun `each native size exposes the real content and accessibility without Compose`() {
        val context = RuntimeEnvironment.getApplication()
        val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE)
        for (size in FestivalWidgetSize.entries) {
            val model = FestivalWidgetPresentation(WidgetPhase.Playing, "아티스트", "23:30–01:00", rows = listOf("첫 공연", "다음 공연"))
            val view = createWidgetViews(context, model, size, open).apply(context, FrameLayout(context))
            assertTrue(view.contentDescription.toString().contains("아티스트"))
            assertNotNull(view.findViewById<android.widget.ImageView>(R.id.widget_title_image).drawable)
            assertTrue(view.contentDescription.toString().contains("23:30–01:00"))
        }
    }

    @Test fun `native widget uses bundled fonts and clips its rounded background`() {
        val context = RuntimeEnvironment.getApplication()
        val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE)
        val view = createWidgetViews(
            context,
            FestivalWidgetPresentation(WidgetPhase.Playing, "아티스트", "23:30–01:00"),
            FestivalWidgetSize.Medium,
            open,
        ).apply(context, FrameLayout(context))
        assertEquals(
            context.resources.getFont(io.festicore.ui.designkit.R.font.pretendard_variable),
            view.findViewById<TextView>(R.id.widget_subtitle).typeface,
        )
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 180)
        assertTrue("Widget root must clip its rounded outline", view.clipToOutline)
        val outline = Outline()
        view.outlineProvider.getOutline(view, outline)
        assertTrue("Widget root must have a rounded outline", outline.radius > 0f)
    }

    @Test fun `provider is private and declares home widget metadata`() {
        val context = RuntimeEnvironment.getApplication()
        val component = android.content.ComponentName(context, FestivalWidgetProvider::class.java)
        val info = runCatching {
            context.packageManager.getReceiverInfo(component, android.content.pm.PackageManager.GET_META_DATA)
        }.getOrNull()
        assertNotNull(info)
        assertFalse(info?.exported ?: true)
        assertTrue((info?.metaData?.getInt("android.appwidget.provider") ?: 0) != 0)
    }
}
