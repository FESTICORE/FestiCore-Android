package io.festicore.ui.designkit.component.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IconSize = 36.dp
private val VerticalPadding = 48.dp

/** 검색 결과가 없을 때 보여 주는 안내. [message]는 iOS `message(_:)` 설정이다. */
@Composable
fun FCSearchEmpty(
    query: String,
    modifier: Modifier = Modifier,
    message: String = "페스티벌 이름, 아티스트, 장소로 다시 찾아보세요",
) {
    // Figma Search Empty 202 = 위아래 48 + 아이콘 36 + 16 + 제목 24 + 12 + 설명 18
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = VerticalPadding, horizontal = FestiCoreSpacing.paddingP24),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_magnifying_glass),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            // Figma Search Empty 아이콘: Label/Disable
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelDisable.color),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = if (query.isEmpty()) "검색어를 입력해 주세요" else "‘$query’ 검색 결과가 없어요",
                style =
                    FestiCoreTextStyle.baseCalloutBold.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNormal.color,
                        textAlign = TextAlign.Center,
                    ),
            )
            BasicText(
                text = message,
                style =
                    FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(
                        color = FestiCoreSemanticColor.labelAssistive.color,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
    }
}
