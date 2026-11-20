package io.festicore.ui.designkit.component.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 40.dp
private val DotSize = 8.dp

/**
 * 무대 컬럼 머리. 점 색 = 무대 색.
 *
 * @param dotColor 점 색(iOS `dotColor(_:)`).
 */
@Composable
fun FCStageHeader(
    name: String,
    modifier: Modifier = Modifier,
    dotColor: FestiCoreSemanticColor = FestiCoreSemanticColor.accentPointLime,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Height)
                .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(DotSize).background(dotColor.color, CircleShape))
        BasicText(
            text = name,
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
