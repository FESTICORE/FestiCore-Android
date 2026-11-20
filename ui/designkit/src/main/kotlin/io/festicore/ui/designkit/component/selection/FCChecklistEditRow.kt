package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 56.dp
private val DeleteSize = 32.dp
private val IconSize = 20.dp

/**
 * 준비물 편집 한 줄. 내 항목은 삭제할 수 있고 운영팀 필수 항목은 삭제할 수 없다.
 *
 * @param onDelete 삭제 동작(iOS `onDelete(_:)`). `null`이면 운영팀 필수 항목으로 보고 삭제 버튼 대신 태그를 표시한다.
 */
@Composable
fun FCChecklistEditRow(
    label: String,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.height(Height).padding(horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onDelete != null) {
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {
                            contentDescription = "$label 삭제"
                            role = Role.Button
                        }.size(DeleteSize)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_trash_can_20),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.statusNegative.color),
                )
            }
        } else {
            FCTag("필수 · 삭제 불가")
        }
    }
}
