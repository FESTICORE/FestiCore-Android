package io.festicore.feature.festival.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

data class FestivalWidgetPinStatus(
    val isPinSupported: Boolean,
    val isInstalled: Boolean,
)

class FestivalWidgetPinController(private val context: Context) {
    val installationChanges: kotlinx.coroutines.flow.Flow<Unit> get() = confirmations
    internal companion object {
        private val confirmations = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        fun onPinConfirmed() {
            confirmations.tryEmit(Unit)
        }
    }

    private val provider get() = ComponentName(context, FestivalWidgetProvider::class.java)
    fun status(): FestivalWidgetPinStatus {
        val manager = AppWidgetManager.getInstance(context)
        return FestivalWidgetPinStatus(manager.isRequestPinAppWidgetSupported, manager.getAppWidgetIds(provider).isNotEmpty())
    }
    fun requestPin(): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, FestivalWidgetProvider::class.java).setAction(FestivalWidgetProvider.PIN_CONFIRMED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            manager.requestPinAppWidget(provider, null, callback)
        } catch (_: IllegalStateException) {
            false
        }
    }
}
