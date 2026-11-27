package io.festicore.feature.festival.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import io.festicore.domain.festival.widget.FestivalWidgetUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun createFestivalWidgetUpdater(
    context: Context,
    useCase: FestivalWidgetUseCase,
    clock: java.time.Clock = java.time.Clock.systemUTC(),
): FestivalWidgetUpdater = FestivalWidgetUpdater(useCase, { read ->
    val host = context.applicationContext as? FestivalWidgetHost
    if (host != null) {
        val manager = AppWidgetManager.getInstance(context)
        val model = presentWidget(read.snapshot, clock.millis(), read.unavailable)
        val open = host.widgetOpenIntent(model.festivalId)
        manager.getAppWidgetIds(ComponentName(context, FestivalWidgetProvider::class.java)).forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val views = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.widget.RemoteViews(
                    mapOf(
                        android.util.SizeF(150f, 150f) to createWidgetViews(context, model, FestivalWidgetSize.Small, open),
                        android.util.SizeF(280f, 150f) to createWidgetViews(context, model, FestivalWidgetSize.Medium, open),
                        android.util.SizeF(280f, 280f) to createWidgetViews(context, model, FestivalWidgetSize.Large, open),
                    ),
                )
            } else {
                android.widget.RemoteViews(
                    createWidgetViews(context, model, widgetSize(options, landscape = true), open),
                    createWidgetViews(context, model, widgetSize(options, landscape = false), open),
                )
            }
            manager.updateAppWidget(id, views)
        }
    }
}, CoroutineScope(SupervisorJob() + Dispatchers.IO))

internal fun widgetSize(width: Int, height: Int): FestivalWidgetSize = when {
    width >= 280 && height >= 280 -> FestivalWidgetSize.Large
    width >= 280 -> FestivalWidgetSize.Medium
    else -> FestivalWidgetSize.Small
}

internal fun widgetSize(options: android.os.Bundle, landscape: Boolean): FestivalWidgetSize = widgetSize(
    options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150),
    options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150),
)
