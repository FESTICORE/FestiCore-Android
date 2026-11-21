package io.festicore.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpStateTest {
    /** 3종 체크 · 현재 버전 동의 · 닉네임 있음 · 제출 가능 상태. */
    private val ready =
        SignUpState(
            currentTermsVersion = TERMS_V1,
            nickname = "페스티",
            acceptedServiceTerms = true,
            acceptedPrivacyTerms = true,
            acceptedLocationTerms = true,
            termsDraftVersion = TERMS_V1,
        )

    @Test
    fun `canSubmit 조건표`() {
        val cases =
            listOf(
                "모두 충족" to (ready to true),
                "제출 중" to (ready.copy(isSubmitting = true) to false),
                "약관 재변경 소진" to (ready.copy(termsUpdateExhausted = true) to false),
                "서비스 약관 미체크" to (ready.copy(acceptedServiceTerms = false) to false),
                "개인정보 미체크" to (ready.copy(acceptedPrivacyTerms = false) to false),
                "위치 약관 미체크" to (ready.copy(acceptedLocationTerms = false) to false),
                "체크 시점 버전이 서버 버전과 다름" to (ready.copy(currentTermsVersion = TERMS_V2) to false),
                "닉네임 공백뿐" to (ready.copy(nickname = "   ") to false),
                "닉네임 빈 값" to (ready.copy(nickname = "") to false),
            )
        cases.forEach { (name, case) -> assertEquals(name, case.second, case.first.canSubmit) }
    }

    @Test
    fun `서버 닉네임은 비어 있고 고치지 않았을 때 한 번만 채운다`() {
        val empty = SignUpState(currentTermsVersion = TERMS_V1)
        assertEquals("페스티", empty.applying("페스티", TERMS_V1).nickname)
        assertEquals("내닉", empty.copy(nickname = "내닉").applying("페스티", TERMS_V1).nickname)
        assertEquals("", empty.copy(nicknameEdited = true).applying("페스티", TERMS_V1).nickname)
        assertEquals("", empty.applying(null, TERMS_V1).nickname)
    }

    @Test
    fun `약관 버전이 바뀐 프로필을 받으면 필수 체크를 모두 지우고 닉네임은 둔다`() {
        val next = ready.applying("다른", TERMS_V2)
        assertFalse(next.acceptedServiceTerms || next.acceptedPrivacyTerms || next.acceptedLocationTerms)
        assertNull(next.termsDraftVersion)
        assertEquals(TERMS_V2, next.currentTermsVersion)
        assertEquals("페스티", next.nickname)
        assertTrue(ready.applying(null, TERMS_V1).acceptedTerms)
    }
}
