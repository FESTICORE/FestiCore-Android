package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private val Size = 24.dp
private val DefaultIconSize = 16.dp
private val BorderWidth = 1.5.dp

/** 원형 체크(iOS Selection `FCCheckCircle`). C5 `FCNotificationRow` 편집 모드가 쓰려고 C6보다 먼저 `internal`로 옮겼다. */
@Composable
internal fun FCCheckCircle(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = DefaultIconSize,
) {
    val base = modifier.clearAndSetSemantics {}.size(Size)
    if (isChecked) {
        Box(
            modifier = base.background(FestiCoreSemanticColor.staticLime.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.staticInk.color),
            )
        }
    } else {
        Box(modifier = base.border(BorderWidth, FestiCoreSemanticColor.lineStrong.color, CircleShape))
    }
}
