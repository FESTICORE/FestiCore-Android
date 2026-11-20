package io.festicore.ui.designkit.component.action

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.navigation.FCSelection
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Width = 200.dp
private val BorderWidth = 1.dp
private const val SHADOW_OPACITY = 0.12f
private val ShadowRadius = 12.dp
private val ShadowY = 8.dp

/** 선택 상태는 들지 않는다. iOS `@Binding selection`은 [selection] + [onSelectionChange]로 받는다. */
@Composable
fun FCSortMenu(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r12)
    Column(
        modifier =
            modifier
                .width(Width)
                .dropShadow(
                    shape = shape,
                    shadow =
                        Shadow(
                            radius = ShadowRadius,
                            color = FestiCoreSemanticColor.staticBlack.color,
                            offset = DpOffset(0.dp, ShadowY),
                            alpha = SHADOW_OPACITY,
                        ),
                ).clip(shape)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape)
                .padding(vertical = FestiCoreSpacing.paddingP4),
    ) {
        items.forEachIndexed { index, item ->
            FCMenuItem(
                label = item,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { onSelectionChange(index) },
                isSelected = FCSelection.isSelected(index = index, selection = selection, count = items.size),
            )
        }
    }
}
