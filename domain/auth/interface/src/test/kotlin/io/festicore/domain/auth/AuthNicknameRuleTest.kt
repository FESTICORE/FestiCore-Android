package io.festicore.domain.auth
import io.festicore.domain.auth.entity.AuthNicknameRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthNicknameRuleTest {
    @Test
    fun `앞뒤 공백을 빼고 1자 이상 12자 이하만 맞다`() {
        assertFalse(AuthNicknameRule.isValid(""))
        assertFalse(AuthNicknameRule.isValid("   \n"))
        assertTrue(AuthNicknameRule.isValid(" a "))
        assertTrue(AuthNicknameRule.isValid("  abcdefghijkl  "))
        assertFalse(AuthNicknameRule.isValid("abcdefghijklm"))
    }

    @Test
    fun `12자 이하 입력은 그대로 둔다`() {
        assertEquals(" abcdefghijkl ", AuthNicknameRule.limited(" abcdefghijkl "))
        assertEquals("ab ", AuthNicknameRule.limited("ab "))
    }

    @Test
    fun `13자 입력은 공백을 빼고 앞 12자로 자른다`() {
        assertEquals("abcdefghijkl", AuthNicknameRule.limited(" abcdefghijklm "))
    }

    @Test
    fun `한글과 이모지는 보이는 글자 하나를 1자로 센다`() {
        val korean = "가나다라마바사아자차카타"
        val emoji = "👍🏽".repeat(11) + "👨‍👩‍👧"

        assertTrue(AuthNicknameRule.isValid(korean))
        assertTrue(AuthNicknameRule.isValid(emoji))
        assertEquals("12 / 12", AuthNicknameRule.counterText(emoji))
        assertEquals(korean, AuthNicknameRule.limited(korean + "파"))
        assertEquals(emoji, AuthNicknameRule.limited(emoji + "🇰🇷"))
    }

    @Test
    fun `카운터는 공백을 뺀 길이를 보인다`() {
        assertEquals("3 / 12", AuthNicknameRule.counterText(" 로이a "))
    }
}
