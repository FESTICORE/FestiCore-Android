package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/** iOS `FCTag.Tone` */
enum class FCTagTone {
    Neutral,
    Secondary,
    Lime,
    Warning,
    OnImage,

    /** Figma Live Tag tone=inverse(S06 지도 위 무대 표시) */
    Inverse,
}

/**
 * 작은 라벨. neutral은 정보·나, secondary는 약한 보라 강조, lime은 내 일정, warning은 주의, onImage는 사진 위에 쓴다.
 *
 * @param tone 색 구성(iOS `tone(_:)`).
 */
@Composable
fun FCTag(
    label: String,
    modifier: Modifier = Modifier,
    tone: FCTagTone = FCTagTone.Neutral,
) {
    val background =
        when (tone) {
            FCTagTone.Neutral -> FestiCoreSemanticColor.fillNormal
            FCTagTone.Secondary -> FestiCoreSemanticColor.accentBackgroundPurple
            FCTagTone.Lime -> FestiCoreSemanticColor.staticLime
            FCTagTone.Warning -> FestiCoreSemanticColor.accentBackgroundOrange
            FCTagTone.OnImage -> FestiCoreSemanticColor.staticalphaWhite20
            FCTagTone.Inverse -> FestiCoreSemanticColor.inverseBackground
        }.color
    val foreground =
        when (tone) {
            FCTagTone.Neutral -> FestiCoreSemanticColor.labelNeutral
            FCTagTone.Secondary -> FestiCoreSemanticColor.accentForegroundPurple
            FCTagTone.Lime -> FestiCoreSemanticColor.staticInk
            FCTagTone.Warning -> FestiCoreSemanticColor.accentForegroundOrange
            FCTagTone.OnImage -> FestiCoreSemanticColor.staticWhite
            FCTagTone.Inverse -> FestiCoreSemanticColor.inverseLabel
        }.color
    // Figma Tag(18)는 줄 높이 박스(14) 기준이다.
    BasicText(
        text = label,
        modifier =
            modifier
                .background(background, RoundedCornerShape(FestiCoreRadius.r4))
                .padding(vertical = FestiCoreSpacing.marginM2, horizontal = FestiCoreSpacing.marginM6),
        style = FestiCoreTextStyle.baseCaption2Medium.textStyle.copy(color = foreground),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
