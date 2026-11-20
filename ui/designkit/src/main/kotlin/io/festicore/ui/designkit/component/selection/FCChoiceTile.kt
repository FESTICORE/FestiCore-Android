package io.festicore.ui.designkit.component.selection

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val ZoneHeight = 44.dp
private val ZoneIconSize = 16.dp
private val ActivityIconSize = 24.dp
private val BorderWidth = 1.dp
private const val MINIMUM_SCALE = 0.85f

/** 선택 타일 크기(iOS `FCChoiceTile.Size`). 존은 가로 한 줄(44), 활동은 아이콘 위 큰 글자(96, 높이는 화면이 준다)다. */
enum class FCChoiceTileSize {
    Zone,
    Activity,
}

/**
 * Figma Choice Tile(290:484 · 290:488). 기수 존 · 활동 시트(S09 B · C)와 크루 "모여!" 존 시트(S07 E)가 함께 쓴다.
 *
 * @param size 존 · 활동 크기(iOS `size(_:)`).
 * @param isSelected 선택 상태(iOS `isSelected(_:)`).
 */
@Composable
fun FCChoiceTile(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: FCChoiceTileSize = FCChoiceTileSize.Zone,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    val foreground = if (isSelected) FestiCoreSemanticColor.inverseLabel.color else FestiCoreSemanticColor.labelNormal.color
    val background = if (isSelected) FestiCoreSemanticColor.inverseBackground.color else FestiCoreSemanticColor.backgroundElevated.color
    val tile =
        modifier
            .clip(shape)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .background(background, shape)
            .then(if (isSelected) Modifier else Modifier.border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape))
    when (size) {
        FCChoiceTileSize.Zone -> {
            val textStyle = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = foreground)
            Row(
                modifier = tile.fillMaxWidth().heightIn(min = ZoneHeight).padding(FestiCoreSpacing.paddingP12),
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChoiceTileIcon(icon, ZoneIconSize, foreground)
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = textStyle,
                    maxLines = 1,
                    // iOS minimumScaleFactor(0.85)
                    autoSize =
                        TextAutoSize.StepBased(
                            minFontSize = textStyle.fontSize * MINIMUM_SCALE,
                            maxFontSize = textStyle.fontSize,
                        ),
                )
            }
        }

        FCChoiceTileSize.Activity -> {
            Column(
                modifier = tile.fillMaxWidth().fillMaxHeight().padding(FestiCoreSpacing.paddingP16),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChoiceTileIcon(icon, ActivityIconSize, foreground)
                BasicText(
                    text = title,
                    style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = foreground),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChoiceTileIcon(
    @DrawableRes icon: Int,
    side: Dp,
    tint: Color,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(side),
        colorFilter = ColorFilter.tint(tint),
    )
}
