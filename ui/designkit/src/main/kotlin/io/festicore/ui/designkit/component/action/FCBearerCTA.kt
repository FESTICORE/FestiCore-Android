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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val FlagMarkSize = 96.dp
private val FlagIconSize = 42.dp
private val CtaHeight = 72.dp
private val BorderWidth = 1.dp

enum class FCBearerCTAState {
    Start,
    Restart,
}

data class FCBearerCTACopy(
    val title: String,
    val buttonTitle: String,
    val caption: String,
    val note: String? = null,
) {
    companion object {
        fun defaultValue(state: FCBearerCTAState): FCBearerCTACopy = when (state) {
            FCBearerCTAState.Start ->
                FCBearerCTACopy(
                    title = "오늘 깃발을 올릴 준비가 됐나요?",
                    buttonTitle = "깃발 올리기",
                    caption = "올리면 팔로워에게 알림이 가요",
                )

            FCBearerCTAState.Restart ->
                FCBearerCTACopy(
                    title = "오늘 깃발을 내렸어요",
                    buttonTitle = "다시 시작하기",
                    caption = "오늘 1번 더 시작할 수 있어요",
                    note = "종료하지 않아도 공연이 끝나고 2시간 뒤에 자동으로 내려가요",
                )
        }
    }
}

/** [copy]가 null이면 [state]의 기본 문구를 쓴다. */
@Composable
fun FCBearerCTA(
    state: FCBearerCTAState,
    modifier: Modifier = Modifier,
    copy: FCBearerCTACopy? = null,
    onClick: () -> Unit,
) {
    val isStart = state == FCBearerCTAState.Start
    val text = copy ?: FCBearerCTACopy.defaultValue(state)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(FlagMarkSize)
                    .background(
                        if (isStart) FestiCoreSemanticColor.accentPointLime.color else FestiCoreSemanticColor.backgroundElevated.color,
                        CircleShape,
                    ).then(
                        if (isStart) Modifier else Modifier.border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, CircleShape),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_flag),
                contentDescription = null,
                modifier = Modifier.size(FlagIconSize),
                colorFilter =
                    ColorFilter.tint(
                        if (isStart) FestiCoreSemanticColor.staticInk.color else FestiCoreSemanticColor.labelNormal.color,
                    ),
            )
        }
        BasicText(
            text = text.title,
            style =
                FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(
                    color = FestiCoreSemanticColor.labelNormal.color,
                    textAlign = TextAlign.Center,
                ),
        )
        Spacer(modifier = Modifier.height(FestiCoreSpacing.paddingP8))
        val shape = RoundedCornerShape(FestiCoreRadius.r24)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CtaHeight)
                    .clip(shape)
                    .background(
                        if (isStart) FestiCoreSemanticColor.accentPointLime.color else FestiCoreSemanticColor.inverseBackground.color,
                    )
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                    .padding(horizontal = FestiCoreSpacing.paddingP20),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text.buttonTitle,
                style =
                    FestiCoreTextStyle.baseCalloutBold.textStyle.copy(
                        color = if (isStart) FestiCoreSemanticColor.staticInk.color else FestiCoreSemanticColor.inverseLabel.color,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            text = text.caption,
            style =
                FestiCoreTextStyle.baseCalloutMedium.textStyle.copy(
                    color = FestiCoreSemanticColor.labelNeutral.color,
                    textAlign = TextAlign.Center,
                ),
        )
        text.note?.let { note ->
            BasicText(
                text = note,
                modifier = Modifier.fillMaxWidth(),
                style =
                    FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(
                        color = FestiCoreSemanticColor.labelAssistive.color,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
    }
}
