package io.festicore.ui.designkit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.festicore.ui.designkit.font.FestiCoreFontFamily
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignKitTokenTest {
    @Test
    fun `텍스트 스타일은 Pretendard와 토큰 크기 · 굵기 · 행간 · 자간을 쓴다`() {
        val style = FestiCoreTextStyle.baseBodyBold.textStyle

        assertEquals(FestiCoreFontFamily.pretendard, style.fontFamily)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(17.sp, style.fontSize)
        assertEquals(FestiCoreTextStyle.baseBodyBold.lineHeight, style.lineHeight)
        assertEquals(0.em, style.letterSpacing)
    }

    @Test
    fun `point 계열은 iOS resolvedFont처럼 Bagel Fat One을 쓴다`() {
        val points =
            setOf(
                FestiCoreTextStyle.pointDisplay,
                FestiCoreTextStyle.pointHeading,
                FestiCoreTextStyle.pointLabel,
                FestiCoreTextStyle.pointTitle,
            )

        FestiCoreTextStyle.entries.forEach { token ->
            val expected = if (token in points) FestiCoreFontFamily.bagelFatOne else FestiCoreFontFamily.pretendard
            assertEquals(token.name, expected, token.textStyle.fontFamily)
            assertEquals(token.name, expected, token.textStyleWithoutLineBox.fontFamily)
        }
    }

    @Test
    fun `모든 텍스트 스타일은 한국어를 어절 단위로 줄바꿈한다`() {
        FestiCoreTextStyle.entries.forEach { token ->
            assertEquals(token.name, LineBreak.WordBreak.Phrase, token.textStyle.lineBreak.wordBreak)
            assertEquals(token.name, LineBreak.WordBreak.Phrase, token.textStyleWithoutLineBox.lineBreak.wordBreak)
            assertEquals(token.name, LocaleList("ko-KR"), token.textStyle.localeList)
        }
    }

    @Test
    fun `줄 박스 없는 변형은 첫 줄 위 · 끝 줄 아래 여백만 없앤다`() {
        val token = FestiCoreTextStyle.baseCalloutBold

        assertEquals(LineHeightStyle.Trim.None, token.textStyle.lineHeightStyle?.trim)
        assertEquals(LineHeightStyle.Trim.Both, token.textStyleWithoutLineBox.lineHeightStyle?.trim)
        assertEquals(token.textStyle.lineHeight, token.textStyleWithoutLineBox.lineHeight)
    }

    @Test
    fun `semantic 색은 light와 dark 값을 따로 가진다`() {
        assertEquals(Color(0xFF111113), FestiCoreSemanticColor.labelNormal.light)
        assertEquals(Color(0xFFFFFFFF), FestiCoreSemanticColor.labelNormal.dark)
    }

    @Test
    fun `레이아웃 토큰은 dp와 투명도로 읽는다`() {
        assertEquals(16.dp, FestiCoreSpacing.marginM16)
        assertEquals(4.dp, FestiCoreRadius.r4)
        assertEquals(0.3f, FestiCoreState.disabledOpacity)
    }
}
