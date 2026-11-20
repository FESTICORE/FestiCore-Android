package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IconSize = 16.dp

/** 최근 검색어 칩. 칩을 누르면 다시 검색하고 x는 그 검색어만 지운다. */
@Composable
fun FCRecentKeyword(
    keyword: String,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    // Figma Recent Keyword(30) = 위아래 6 + 줄 높이 박스 18
    Row(
        modifier =
            modifier
                .background(FestiCoreSemanticColor.fillStrong.color, CircleShape)
                .padding(
                    top = FestiCoreSpacing.marginM6,
                    bottom = FestiCoreSpacing.marginM6,
                    start = FestiCoreSpacing.paddingP12,
                    end = FestiCoreSpacing.paddingP8,
                ),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = keyword,
            modifier = Modifier.clickable(role = Role.Button, onClick = onSelect),
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            painter = painterResource(R.drawable.ic_xmark_16),
            contentDescription = null,
            modifier =
                Modifier
                    .clearAndSetSemantics {
                        contentDescription = "$keyword 삭제"
                        role = Role.Button
                    }.clickable(onClick = onDelete)
                    .size(IconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}
