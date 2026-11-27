package io.festicore.feature.festival.widget

import android.app.PendingIntent

/** App supplies navigation and its injected updater; the receiver never uses a service locator. */
interface FestivalWidgetHost {
    val festivalWidgetUpdater: FestivalWidgetUpdater
    fun widgetOpenIntent(festivalId: String?): PendingIntent
}
