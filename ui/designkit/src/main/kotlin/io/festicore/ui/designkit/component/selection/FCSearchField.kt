package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 40.dp
private val IconSize = 16.dp

/**
 * 검색 입력창. 입력 중에는 지우기 버튼을 보인다. iOS `text` 바인딩은 [text] + [onTextChange]로 받는다.
 *
 * @param onSubmit 키보드 검색 버튼을 눌렀을 때 실행할 동작(iOS `onSubmit(_:)`).
 */
@Composable
fun FCSearchField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "페스티벌, 아티스트, 장소 검색",
    onSubmit: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .height(Height)
                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(horizontal = FestiCoreSpacing.paddingP12),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchFieldIcon(R.drawable.ic_magnifying_glass_16, Modifier.clearAndSetSemantics {})
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            textStyle = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            singleLine = true,
            cursorBrush = SolidColor(FestiCoreSemanticColor.labelNormal.color),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(
                                color = FestiCoreSemanticColor.labelAssistive.color,
                            ),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (text.isNotEmpty()) {
            SearchFieldIcon(
                R.drawable.ic_circle_xmark,
                Modifier
                    .clearAndSetSemantics {
                        contentDescription = "지우기"
                        role = Role.Button
                    }.clickable { onTextChange("") },
            )
        }
    }
}

@Composable
private fun SearchFieldIcon(icon: Int, modifier: Modifier) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(IconSize),
        colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
    )
}
