package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val Height = 56.dp
private val Spacing = 10.dp
private val IconSize = 20.dp
private val InputHeight = 40.dp
private val InputRadius = 10.dp

/**
 * 준비물에 내 항목을 추가하는 줄. 누르면 입력창과 추가 버튼으로 바뀐다.
 * iOS `text` 바인딩은 [text] + [onTextChange]로 받고, 입력 중 여부는 iOS `@State`처럼 컴포넌트 안에 둔다.
 */
@Composable
fun FCAddItemRow(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
) {
    var isTyping by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val canSubmit = text.isNotBlank()
    val submit = {
        if (canSubmit) {
            onAdd()
            isTyping = false
        }
    }
    Row(
        modifier = modifier.height(Height).padding(horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(Spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isTyping) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(InputHeight)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "내 항목" },
                textStyle = FestiCoreTextStyle.baseCalloutRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelNormal.color,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                singleLine = true,
                cursorBrush = SolidColor(FestiCoreSemanticColor.labelNormal.color),
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(InputRadius))
                                .padding(horizontal = FestiCoreSpacing.paddingP12),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        innerTextField()
                    }
                },
            )
            FCButton(title = "추가", size = FCButtonSize.Medium, isEnabled = canSubmit, onClick = submit)
        } else {
            val color = FestiCoreSemanticColor.labelNeutral.color
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button) { isTyping = true },
                horizontalArrangement = Arrangement.spacedBy(Spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_plus_20),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(color),
                )
                BasicText(
                    text = "내 항목 추가",
                    style = FestiCoreTextStyle.baseCalloutMedium.textStyleWithoutLineBox.copy(color = color),
                )
            }
        }
    }
}
