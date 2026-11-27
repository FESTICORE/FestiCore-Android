package io.festicore.feature.festival.widget

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import io.festicore.feature.festival.sources.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

internal enum class FestivalWidgetSize { Small, Medium, Large }
internal fun createWidgetViews(
    context: Context,
    model: FestivalWidgetPresentation,
    size: FestivalWidgetSize,
    open: PendingIntent,
): RemoteViews {
    val ink = FestiCoreSemanticColor.staticInk.light.toArgb()
    val lime = FestiCoreSemanticColor.staticLime.light.toArgb()
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    return RemoteViews(context.packageName, R.layout.festival_widget).apply {
        setInt(R.id.widget_root, "setBackgroundColor", lime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setViewOutlinePreferredRadius(R.id.widget_root, FestiCoreRadius.r24.value, TypedValue.COMPLEX_UNIT_DIP)
        }
        val horizontal = if (size == FestivalWidgetSize.Large) 16 else 14
        val vertical = if (size == FestivalWidgetSize.Large) 16 else 12
        setViewPadding(R.id.widget_root, dp(horizontal), dp(vertical), dp(horizontal), dp(vertical))
        setViewVisibility(R.id.widget_heading_image, if (size == FestivalWidgetSize.Large) View.VISIBLE else View.GONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setViewLayoutHeight(R.id.widget_title_image, if (size == FestivalWidgetSize.Large) 56f else 44f, TypedValue.COMPLEX_UNIT_DIP)
        }
        val headingWidth = if (size == FestivalWidgetSize.Small) 122 else 252
        setImageViewBitmap(R.id.widget_heading_image, widgetHeadingBitmap(context, "FESTICORE", headingWidth, 14f, 1))
        setImageViewBitmap(
            R.id.widget_title_image,
            widgetHeadingBitmap(
                context,
                model.title,
                headingWidth,
                if (model.title.length >
                    16
                ) {
                    16f
                } else {
                    22f
                },
                2,
            ),
        )
        val subtitle = if (size == FestivalWidgetSize.Medium) {
            listOf(model.subtitle, model.info).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            model.subtitle
        }
        setTextViewText(R.id.widget_subtitle, subtitle)
        setTextViewText(R.id.widget_info, model.info)
        setViewVisibility(R.id.widget_info, if (model.info.isEmpty() || size != FestivalWidgetSize.Large) View.GONE else View.VISIBLE)
        setTextViewText(R.id.widget_footer, model.footer)
        listOf(R.id.widget_subtitle, R.id.widget_info).forEach { setTextColor(it, ink) }
        setInt(R.id.widget_footer, "setBackgroundColor", ink)
        setTextColor(R.id.widget_footer, lime)
        setInt(R.id.widget_footer, "setMinimumHeight", dp(if (size == FestivalWidgetSize.Large) 44 else 34))
        removeAllViews(R.id.widget_rows)
        if (size == FestivalWidgetSize.Large) {
            model.rows.take(4).forEach { row ->
                addView(
                    R.id.widget_rows,
                    RemoteViews(context.packageName, R.layout.festival_widget_row).apply {
                        setTextViewText(R.id.widget_row, row)
                        setTextColor(R.id.widget_row, ink)
                        setOnClickPendingIntent(R.id.widget_row, open)
                    },
                )
            }
        }
        setOnClickPendingIntent(R.id.widget_root, open)
        setOnClickPendingIntent(R.id.widget_footer, open)
        setContentDescription(
            R.id.widget_root,
            listOf(model.title, model.subtitle, model.info, model.footer).filter {
                it.isNotBlank()
            }.joinToString(", "),
        )
    }
}
