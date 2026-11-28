package io.festicore.feature.festival.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.TextView
import io.festicore.domain.festival.entity.FestivalWidgetSnapshot
import io.festicore.domain.festival.widget.FestivalWidgetRead
import io.festicore.domain.festival.widget.FestivalWidgetUseCase
import io.festicore.feature.festival.sources.R
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class WidgetPublisherTestApplication :
    Application(),
    FestivalWidgetHost {
    override lateinit var festivalWidgetUpdater: FestivalWidgetUpdater
    override fun widgetOpenIntent(festivalId: String?): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = WidgetPublisherTestApplication::class)
class FestivalWidgetPublisherTest {
    @Test fun portraitUsesMaxHeightAndLandscapeUsesMaxWidth() {
        val options = android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 500)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 350)
        }
        assertEquals(FestivalWidgetSize.Large, widgetSize(options, landscape = false))
        assertEquals(FestivalWidgetSize.Medium, widgetSize(options, landscape = true))
    }

    @Test fun `WI8 publisher uses supplied clock for actual remote views`() = runTest {
        val context = RuntimeEnvironment.getApplication() as WidgetPublisherTestApplication
        val snapshot = FestivalWidgetSnapshot(
            "a",
            "축제",
            1,
            "v1",
            days = listOf(FestivalWidgetSnapshot.Day("2020-01-01", Instant.parse("2020-01-01T06:00:00Z").toEpochMilli())),
        )
        val useCase = object : FestivalWidgetUseCase {
            override val changes = emptyFlow<Unit>()
            override suspend fun observeSessions() = Unit
            override suspend fun read() = FestivalWidgetRead(snapshot, 1)
        }
        context.festivalWidgetUpdater =
            createFestivalWidgetUpdater(context, useCase, Clock.fixed(Instant.parse("2020-01-01T05:00:00Z"), ZoneOffset.UTC))
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val id = manager.createWidget(FestivalWidgetProvider::class.java, R.layout.festival_widget)
        context.festivalWidgetUpdater.renderAll()
        org.junit.Assert.assertTrue(manager.getViewFor(id).contentDescription.toString().contains("곧 시작해요!"))
    }
}
