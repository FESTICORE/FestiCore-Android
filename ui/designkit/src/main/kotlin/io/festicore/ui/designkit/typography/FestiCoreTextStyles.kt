package io.festicore.ui.designkit.typography

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import io.festicore.ui.designkit.font.FestiCoreFontFamily
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle

/**
 * 토큰의 서체. iOS `FestiCoreTextStyle.resolvedFont`처럼 point 계열은 Bagel Fat One, 나머지는 Pretendard다.
 * 생성 토큰에는 서체 정보가 없어 여기서 매핑한다.
 */
val FestiCoreTextStyle.fontFamily: FontFamily
    get() =
        when (this) {
            FestiCoreTextStyle.pointDisplay,
            FestiCoreTextStyle.pointHeading,
            FestiCoreTextStyle.pointLabel,
            FestiCoreTextStyle.pointTitle,
            -> FestiCoreFontFamily.bagelFatOne

            else -> FestiCoreFontFamily.pretendard
        }

/**
 * 한국어를 글자가 아니라 어절 단위로 줄바꿈한다(iOS 기본 `hangulWordPriority`와 같은 결과).
 * `WordBreak.Phrase`는 API 33 이상에서 적용되고, 그 미만은 기본 줄바꿈을 쓴다.
 * 어절 줄바꿈은 글자 로케일이 한국어일 때만 동작하므로 기기 언어와 상관없이 [FestiCoreLocaleList]를 함께 둔다.
 */
val FestiCoreLineBreak: LineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

/** 앱 문구는 한국어다. 기기 언어가 한국어가 아니어도 한국어 줄바꿈 · 글리프 규칙을 쓴다. */
val FestiCoreLocaleList: LocaleList = LocaleList("ko-KR")

val FestiCoreTextStyle.textStyle: TextStyle
    get() =
        TextStyle(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = size,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            lineBreak = FestiCoreLineBreak,
            localeList = FestiCoreLocaleList,
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
