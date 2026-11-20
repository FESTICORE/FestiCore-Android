package io.festicore.ui.designkit.component.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Width = 311.dp

/** 확인 다이얼로그에 띄울 내용. 되돌리기 어려운 동작 전에 띄우고 되돌리는 방법을 함께 적는다. 인스턴스마다 다른 값으로 본다(iOS `id = UUID()`). */
class FCDialogItem(
    val title: String,
    val message: String,
    val confirmTitle: String = "확인",
    val cancelTitle: String = "취소",
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit = {},
)

/** 제목 · 설명 정렬. iOS `TextAlignment`와 같은 선택지다. */
enum class FCDialogTextAlign {
    Leading,
    Center,
    Trailing,
}

/**
 * 가운데 확인 모달 카드를 Compose `Dialog`로 띄운다. 버튼을 누르면 해당 콜백 뒤 [onDismiss]를 부른다.
 *
 * [textAlign]은 iOS `textAlignment(_:)`, [confirmStyle]은 `confirmStyle(_:)` 설정이다(기본 primary, S09 기수 보드는 inverse).
 */
@Composable
fun FCDialog(
    item: FCDialogItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    textAlign: FCDialogTextAlign = FCDialogTextAlign.Leading,
    confirmStyle: FCButtonStyle = FCButtonStyle.Primary,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FCDialogCard(item, onDismiss, modifier, textAlign, confirmStyle)
    }
}

@Composable
internal fun FCDialogCard(
    item: FCDialogItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    textAlign: FCDialogTextAlign = FCDialogTextAlign.Leading,
    confirmStyle: FCButtonStyle = FCButtonStyle.Primary,
) {
    Column(
        modifier =
            modifier
                .width(Width)
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r20))
                .padding(
                    start = FestiCoreSpacing.paddingP20,
                    top = FestiCoreSpacing.paddingP24,
                    end = FestiCoreSpacing.paddingP20,
                    bottom = FestiCoreSpacing.paddingP20,
                ),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
    ) {
        // Figma Dialog 문구는 줄 높이 박스(제목 24 · 설명 18/줄)로 쌓는다.
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            DialogText(
                text = item.title,
                style = FestiCoreTextStyle.baseHeadlineBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                textAlign = textAlign,
                modifier = Modifier.semantics { heading() },
            )
            DialogText(
                text = item.message,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
                textAlign = textAlign,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCButton(
                title = item.cancelTitle,
                modifier = Modifier.weight(1f),
                style = FCButtonStyle.Secondary,
                size = FCButtonSize.Medium,
                isFullWidth = true,
            ) {
                item.onCancel()
                onDismiss()
            }
            FCButton(
                title = item.confirmTitle,
                modifier = Modifier.weight(1f),
                style = confirmStyle,
                size = FCButtonSize.Medium,
                isFullWidth = true,
            ) {
                item.onConfirm()
                onDismiss()
            }
        }
    }
}

// iOS처럼 여러 줄은 글자 폭 안에서 정렬하고, 가운데 정렬일 때만 글자 덩어리를 가운데에 둔다.
@Composable
private fun DialogText(
    text: String,
    style: TextStyle,
    textAlign: FCDialogTextAlign,
    modifier: Modifier = Modifier,
) {
    val align =
        when (textAlign) {
            FCDialogTextAlign.Leading -> TextAlign.Start
            FCDialogTextAlign.Center -> TextAlign.Center
            FCDialogTextAlign.Trailing -> TextAlign.End
        }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (textAlign == FCDialogTextAlign.Center) Alignment.TopCenter else Alignment.TopStart,
    ) {
        BasicText(text = text, modifier = modifier, style = style.copy(textAlign = align))
    }
}
