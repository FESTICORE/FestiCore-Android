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
