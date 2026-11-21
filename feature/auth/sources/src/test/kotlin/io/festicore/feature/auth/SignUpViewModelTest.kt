package io.festicore.feature.auth

import io.festicore.domain.auth.AuthDomainError
import io.festicore.domain.auth.AuthProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

class SignUpViewModelTest {
    private val route = AuthRoute.SignUp(userId = "u1", nickname = "페스티", currentTermsVersion = TERMS_V1, sessionId = "s1")

    private val prefilled = SignUpState(currentTermsVersion = TERMS_V1, nickname = "페스티")
    private val consented =
        prefilled.copy(
            acceptedServiceTerms = true,
            acceptedPrivacyTerms = true,
            acceptedLocationTerms = true,
            termsDraftVersion = TERMS_V1,
        )
    private val submitting = consented.copy(isSubmitting = true)

    @Test
    fun `서버 닉네임을 미리 채워 시작한다`() {
        assertEquals(prefilled, SignUpViewModel(route, FakeAuthRepository()).container.stateFlow.value)
        val noNickname = route.copy(nickname = null)
        assertEquals("", SignUpViewModel(noNickname, FakeAuthRepository()).container.stateFlow.value.nickname)
    }

    @Test
    fun `닉네임은 공백을 뺀 12자까지만 받고 카운터는 그 길이를 센다`() = runTest {
        SignUpViewModel(route, FakeAuthRepository()).test(this) {
            containerHost.onNicknameChanged("가나다라마바사아자차카타파하")
            expectState { copy(nickname = "가나다라마바사아자차카타", nicknameEdited = true) }
        }
        assertEquals("12 / 12", io.festicore.domain.auth.AuthNicknameRule.counterText("가나다라마바사아자차카타"))
    }

    @Test
    fun `전체 동의는 3종을 함께 바꾸고 체크 시점 약관 버전을 기록한다`() = runTest {
        SignUpViewModel(route, FakeAuthRepository()).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onAllConsentToggled()
            expectState(prefilled)
        }
    }

    @Test
    fun `개별 체크를 모두 하면 전체 동의가 되고 하나를 풀면 다음을 누를 수 없다`() = runTest {
        SignUpViewModel(route, FakeAuthRepository()).test(this) {
            containerHost.onServiceTermsChanged(true)
            expectState { copy(acceptedServiceTerms = true, termsDraftVersion = TERMS_V1) }
            containerHost.onPrivacyTermsChanged(true)
            expectState { copy(acceptedPrivacyTerms = true) }
            containerHost.onLocationTermsChanged(true)
            expectState(consented)
            containerHost.onPrivacyTermsChanged(false)
            expectState { copy(acceptedPrivacyTerms = false) }
        }
    }

    @Test
    fun `가입에 성공하면 세션에 묶어 보내고 완료를 알린다`() = runTest {
        val repository = FakeAuthRepository()
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectSideEffect(SignUpSideEffect.Completed)
        }
        assertEquals(listOf(listOf("페스티", TERMS_V1, "u1", "s1")), repository.signUpCalls)
    }

    @Test
    fun `가입 직후 다시 읽은 프로필의 약관 버전이 올랐으면 체크를 지우고 가입 정보에 남는다`() = runTest {
        val repository = FakeAuthRepository(profile = { AuthProfile(user(), TERMS_V2) })
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectState(prefilled.copy(currentTermsVersion = TERMS_V2, message = "약관이 변경됐어요. 내용을 확인하고 다시 동의해 주세요."))
        }
        assertEquals(1, repository.fetchCount)
    }

    @Test
    fun `가입 직후 프로필을 다시 읽지 못해도 가입은 성공했으므로 완료를 알린다`() = runTest {
        val repository = FakeAuthRepository(profile = { throw AuthDomainError.Offline() })
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectSideEffect(SignUpSideEffect.Completed)
        }
        assertEquals(1, repository.fetchCount)
    }

    @Test
    fun `제출할 수 없는 상태면 보내지 않는다`() = runTest {
        val repository = FakeAuthRepository()
        SignUpViewModel(route, repository).test(this) {
            containerHost.onSubmitTapped()
        }
        assertEquals(0, repository.signUpCalls.size)
    }

    @Test
    fun `약관이 바뀌면 체크를 지우고 프로필을 한 번 다시 읽고 다시 바뀌면 제출을 막는다`() = runTest {
        val repository =
            FakeAuthRepository(
                profile = { AuthProfile(newUserProfile.user, TERMS_V2) },
                signUpResult = { throw AuthDomainError.TermsVersionOutdated() },
            )
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectState(
                prefilled.copy(isSubmitting = true, hasRetriedTermsUpdate = true, message = "약관이 변경됐어요. 내용을 확인하고 다시 동의해 주세요."),
            )
            expectState {
                copy(currentTermsVersion = TERMS_V2, isSubmitting = false, message = "변경된 약관을 확인하고 다시 동의해 주세요.")
            }

            containerHost.onAllConsentToggled()
            expectState {
                copy(acceptedServiceTerms = true, acceptedPrivacyTerms = true, acceptedLocationTerms = true, termsDraftVersion = TERMS_V2)
            }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true, message = null) }
            expectState {
                copy(
                    acceptedServiceTerms = false,
                    acceptedPrivacyTerms = false,
                    acceptedLocationTerms = false,
                    termsDraftVersion = null,
                    isSubmitting = false,
                    termsUpdateExhausted = true,
                    message = "약관이 다시 변경됐어요. 나중에 다시 시도해 주세요.",
                )
            }
        }
        assertEquals(1, repository.fetchCount)
        assertEquals(TERMS_V2, repository.signUpCalls.last()[1])
    }

    @Test
    fun `401이면 첫 진입 로그인으로 돌아간다`() = runTest {
        val repository = FakeAuthRepository(signUpResult = { throw AuthDomainError.NotAuthenticated() })
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectSideEffect(SignUpSideEffect.SessionLost)
        }
    }

    @Test
    fun `오프라인이면 오프라인 저장 실패 문구를 보인다`() = runTest {
        val repository = FakeAuthRepository(signUpResult = { throw AuthDomainError.Offline() })
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectState(consented.copy(message = "오프라인에서 저장하지 못했어요. 연결 후 다시 시도해 주세요."))
        }
    }

    @Test
    fun `그 외 실패는 저장 실패 문구를 보인다`() = runTest {
        val repository = FakeAuthRepository(signUpResult = { throw AuthDomainError.Unavailable() })
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectState(consented.copy(message = "저장하지 못했어요. 다시 시도해 주세요."))
        }
    }

    @Test
    fun `가입 응답이 가입 조건을 채우지 못하면 필수 정보 문구를 보인다`() = runTest {
        val repository = FakeAuthRepository(signUpResult = { newUserProfile.user })
        SignUpViewModel(route, repository).test(this) {
            containerHost.onAllConsentToggled()
            expectState(consented)
            containerHost.onSubmitTapped()
            expectState(submitting)
            expectState(consented.copy(message = "필수 정보가 확인되지 않았어요. 다시 시도해 주세요."))
        }
    }

    @Test
    fun `뒤로 가면 세션을 지우고 닫힘을 알린다`() = runTest {
        val repository = FakeAuthRepository()
        SignUpViewModel(route, repository).test(this) {
            containerHost.onCloseTapped()
            expectSideEffect(SignUpSideEffect.Closed)
        }
        assertEquals(1, repository.clearCount)
    }

    @Test
    fun `약관 상세를 누르면 그 약관을 연다`() = runTest {
        SignUpViewModel(route, FakeAuthRepository()).test(this) {
            containerHost.onLegalDocumentTapped(LegalDocument.Privacy)
            expectSideEffect(SignUpSideEffect.OpenLegalDocument(LegalDocument.Privacy))
        }
    }
}
