package io.festicore.feature.profile.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val DividerHeight = 1.dp

/**
 * 설정 그룹(Figma S13-0 · S13-J). 제목(Pretendard Bold 13, 줄 높이 18, 좌우 4) 아래 `backgroundElevated` · r16 카드. iOS `SettingsHomeView.section`.
 * [footnote]는 카드 아래 설명(화면 모드).
 */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        Text(
            text = title,
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle,
            color = FestiCoreSemanticColor.labelAssistive.color,
            modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP4).semantics { heading() },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FestiCoreRadius.r16))
                    .background(FestiCoreSemanticColor.backgroundElevated.color),
            content = content,
        )
        footnote?.let {
            Text(
                text = it,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                color = FestiCoreSemanticColor.labelAssistive.color,
                modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP4),
            )
        }
    }
}

/** 그룹 안 줄 구분선. Figma S13-0 Divider: 카드 너비를 채운다(iOS SettingsHomeView.swift:146-150 · AppearanceSettingsView.swift:72-74). */
@Composable
internal fun SettingsRowDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DividerHeight)
                .background(FestiCoreSemanticColor.lineNormal.color),
    )
}
