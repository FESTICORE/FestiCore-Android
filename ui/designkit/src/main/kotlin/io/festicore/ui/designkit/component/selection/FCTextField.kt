package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import java.text.BreakIterator

private val Height = 52.dp
private val BorderWidth = 1.5.dp
private val ClearIconSize = 16.dp

/**
 * 텍스트 입력(닉네임 등). 라벨 · 글자 수 · 도움말 · 오류 상태를 표시하고 입력 중에는 지우기 버튼을 보인다.
 * iOS `text` 바인딩은 [text] + [onTextChange]로 받는다.
 *
 * @param label 위 라벨(iOS `label(_:)`).
 * @param maxLength 최대 글자 수(iOS `maxLength(_:)`). 값이 있으면 넘는 입력을 잘라 넘기고 라벨 오른쪽에 글자 수를 표시한다.
 * @param counter 라벨 오른쪽 글자 수 문구(iOS `counter(_:)`). 입력을 자르지 않고 [maxLength] 표시보다 우선한다.
 * @param helper 아래 도움말(iOS `helper(_:)`).
 * @param error 오류 문구(iOS `error(_:)`). 값이 있으면 도움말 대신 빨간 테두리와 문구를 표시한다.
 */
@Composable
fun FCTextField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    maxLength: Int? = null,
    counter: String? = null,
    helper: String? = null,
    error: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(FestiCoreRadius.r12)
    val borderColor =
        when {
            error != null -> FestiCoreSemanticColor.statusNegative.color
            isFocused -> FestiCoreSemanticColor.labelNormal.color
            else -> null
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        if (label != null) {
            FCFieldLabel(label, counter = textFieldCounter(text, maxLength, counter))
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Height)
                    .background(FestiCoreSemanticColor.fillNormal.color, shape)
                    .then(if (borderColor != null) Modifier.border(BorderWidth, borderColor, shape) else Modifier)
                    .padding(horizontal = FestiCoreSpacing.paddingP16),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { onTextChange(limitTextLength(it, maxLength)) },
                modifier = Modifier.weight(1f).semantics { contentDescription = label ?: placeholder },
                textStyle = FestiCoreTextStyle.baseBodyRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelNormal.color,
                ),
                singleLine = true,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(FestiCoreSemanticColor.labelNormal.color),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            BasicText(
                                text = placeholder,
                                style = FestiCoreTextStyle.baseBodyRegular.textStyleWithoutLineBox.copy(
                                    color = FestiCoreSemanticColor.labelAssistive.color,
                                ),
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (isFocused && text.isNotEmpty()) {
                Image(
                    painter = painterResource(R.drawable.ic_circle_xmark),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .clearAndSetSemantics {
                                contentDescription = "지우기"
                                role = Role.Button
                            }.clickable { onTextChange("") }
                            .size(ClearIconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                )
            }
        }
        val message = error ?: helper
        if (message != null) {
            // Figma Text Field의 Helper 12/16
            BasicText(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                style =
                    FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(
                        color = if (error ==
                            null
                        ) {
                            FestiCoreSemanticColor.labelAssistive.color
                        } else {
                            FestiCoreSemanticColor.statusNegative.color
                        },
                    ),
            )
        }
    }
}

/** 라벨 오른쪽 문구. 직접 준 [counter]가 우선이고, 없으면 [maxLength]가 있을 때 "글자 수 / 최대"다(iOS `counter ?? maxLength.map`). */
internal fun textFieldCounter(
    text: String,
    maxLength: Int?,
    counter: String?,
): String? = counter ?: maxLength?.let { "${characterCount(text)} / $it" }

/** [maxLength]를 넘는 입력을 앞에서부터 그 글자 수만큼만 남긴다(iOS `String(newValue.prefix(maxLength))`). */
internal fun limitTextLength(text: String, maxLength: Int?): String {
    if (maxLength == null || characterCount(text) <= maxLength) return text
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    iterator.first()
    val end = iterator.next(maxLength.coerceAtLeast(0))
    return text.substring(0, if (end == BreakIterator.DONE) text.length else end)
}

/** Swift `String.count`처럼 사용자가 보는 글자(grapheme) 단위로 센다. 이모지 하나는 1글자다. */
internal fun characterCount(text: String): Int {
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}
