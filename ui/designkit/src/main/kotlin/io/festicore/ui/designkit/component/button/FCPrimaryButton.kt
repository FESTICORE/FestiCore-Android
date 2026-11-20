package io.festicore.ui.designkit.component.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

@Composable
fun FCPrimaryButton(
    title: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .alpha(if (isEnabled) 1f else FestiCoreState.disabledOpacity)
                .fillMaxWidth()
                .clip(RoundedCornerShape(FestiCoreRadius.r12))
                .background(FestiCoreSemanticColor.primaryNormal.color)
                .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
                .padding(vertical = FestiCoreSpacing.paddingP16),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = title,
            style = FestiCoreTextStyle.baseBodyBold.textStyle.copy(color = FestiCoreSemanticColor.primaryOnPrimary.color),
        )
    }
}
