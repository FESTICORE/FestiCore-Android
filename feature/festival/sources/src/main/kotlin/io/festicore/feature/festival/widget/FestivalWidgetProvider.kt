package io.festicore.feature.festival.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class FestivalWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PIN_CONFIRMED) {
            FestivalWidgetPinController.onPinConfirmed()
            render(context)
        } else {
            super.onReceive(context, intent)
        }
    }
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) = render(context)
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) = render(context)
    private fun render(context: Context) {
        val updater = (context.applicationContext as? FestivalWidgetHost)?.festivalWidgetUpdater ?: return
        val pending = goAsync()
        updater.requestRender { pending?.finish() }
    }
    internal companion object {
        const val PIN_CONFIRMED = "io.festicore.widget.PIN_CONFIRMED"
    }
}
