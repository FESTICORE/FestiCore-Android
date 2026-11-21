package io.festicore.ui.designkit.typography

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import io.festicore.ui.designkit.font.FestiCoreFontFamily
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle

val FestiCoreTextStyle.textStyle: TextStyle
    get() =
        TextStyle(
            fontFamily = FestiCoreFontFamily.pretendard,
            fontWeight = fontWeight,
            fontSize = size,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
        )

/**
 * 줄 박스 없는 변형. 줄 높이는 줄 사이에만 두고 첫 줄 위 · 끝 줄 아래 여백을 없앤다.
 * iOS `festiCoreFont`만 쓰고 `festiCoreLineBox`를 쓰지 않는 곳에 쓴다. [textStyle]은 iOS `festiCoreLineBox`를 켠 상태와 같다.
 */
val FestiCoreTextStyle.textStyleWithoutLineBox: TextStyle
    get() =
        textStyle.copy(
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
        )
