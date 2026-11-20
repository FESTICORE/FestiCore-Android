package io.festicore.ui.designkit.component.media

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private val DefaultSide = 56.dp
private val IconSize = 20.dp

/** [cornerRadius]가 null이면 반경과 아이콘을 기본 크기(56) 대비 [side] 비율로 키운다. */
@Composable
fun FCThumbnail(
    image: Painter?,
    modifier: Modifier = Modifier,
    @DrawableRes placeholderIcon: Int = R.drawable.ic_flag_20,
    side: Dp = DefaultSide,
    cornerRadius: Dp? = null,
) {
    val scale = side / DefaultSide
    val iconSize = if (cornerRadius == null) IconSize * scale else IconSize
    Box(
        modifier =
            modifier
                .clearAndSetSemantics {}
                .size(side)
                .clip(RoundedCornerShape(cornerRadius ?: (FestiCoreRadius.r12 * scale))),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                painter = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(FestiCoreSemanticColor.fillStrong.color),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(placeholderIcon),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                )
            }
        }
    }
}
