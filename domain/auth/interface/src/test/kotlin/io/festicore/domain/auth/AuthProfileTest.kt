package io.festicore.domain.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthProfileTest {
    private val user = AuthUser(
        id = "u1",
        provider = "google",
        email = null,
        nickname = "roy",
        nicknameConfirmed = true,
        termsAcceptedAt = "2026-11-21T00:00:00Z",
        termsVersion = "2026-10",
        pushOptIn = true,
        profileImageUrl = null,
    )

    @Test
    fun `모든 조건을 채우면 가입 완료다`() {
        assertTrue(AuthProfile(user, "2026-10").isEligible)
    }

    @Test
    fun `서버 약관 버전이 비면 가입 완료가 아니다`() {
        assertFalse(AuthProfile(user.copy(termsVersion = ""), "").isEligible)
    }

    @Test
    fun `닉네임을 확정하지 않았으면 가입 완료가 아니다`() {
        assertFalse(AuthProfile(user.copy(nicknameConfirmed = false), "2026-10").isEligible)
    }

    @Test
    fun `약관 동의 시각이 없으면 가입 완료가 아니다`() {
        assertFalse(AuthProfile(user.copy(termsAcceptedAt = null), "2026-10").isEligible)
    }

    @Test
    fun `동의한 약관 버전이 현재 버전과 다르면 가입 완료가 아니다`() {
        assertFalse(AuthProfile(user, "2026-11").isEligible)
        assertFalse(AuthProfile(user.copy(termsVersion = null), "2026-10").isEligible)
    }
}
