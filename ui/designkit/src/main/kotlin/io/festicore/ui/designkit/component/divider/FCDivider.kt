package io.festicore.ui.designkit.component.divider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private val DefaultThickness = 1.dp

@Composable
fun FCDivider(modifier: Modifier = Modifier, thickness: Dp = DefaultThickness) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(thickness)
                .background(FestiCoreSemanticColor.lineNormal.color),
    )
}
