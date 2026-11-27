package io.festicore.feature.festival.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import kotlin.math.ceil

/** Launcher RemoteViews may ignore XML fontFamily. Only the heading uses a bounded bitmap. */
internal fun widgetHeadingBitmap(
    context: Context,
    text: String,
    widthDp: Int,
    textSp: Float,
    maxLines: Int,
): Bitmap {
    val metrics = context.resources.displayMetrics
    val scale = minOf(metrics.density, 512f / widthDp.coerceAtLeast(1))
    val width = (widthDp * scale).toInt().coerceIn(1, 512)
    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        typeface = context.resources.getFont(io.festicore.ui.designkit.R.font.bagel_fat_one_regular)
        textSize = textSp * scale * context.resources.configuration.fontScale.coerceAtMost(2f)
        color = FestiCoreSemanticColor.staticInk.light.toArgb()
    }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setMaxLines(maxLines.coerceIn(1, 2))
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    return Bitmap.createBitmap(width, ceil(layout.height.toDouble()).toInt().coerceIn(1, 192), Bitmap.Config.ARGB_8888).apply {
        density = (scale * 160).toInt().coerceAtLeast(1)
        layout.draw(Canvas(this))
    }
}
