package io.festicore.feature.profile.account

import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthLoginResult
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.feature.profile.AccountState
import io.festicore.feature.profile.RecordingAnalyticsUseCase
import io.festicore.service.analytics.event.AnalyticsButton
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `AccountSettingsFeatureTests` 4건(같은 이름)과 `SettingsHomeFeatureTests.accountScreenRequestsAreForwardedToProfile`(S2에서 넘긴 것).
 * 분석 이벤트 확인은 `service/analytics`가 없어 뺀다. 닉네임 저장은 가짜 저장소로 하고 서버에 보내지 않는다.
 * 그 밖에 Android 추가 사례(저장 중 닫기 막기 · 서버 `invalidNickname` 문구 · 계정 입력 반영 · 로그인 방식 표기)를 둔다.
 */
class AccountSettingsViewModelTest {
    /** 닉네임만 쓰는 가짜 저장소. 그 외 호출은 실패로 본다. */
    private class NicknameAuthRepository(private val result: suspend (String) -> AuthUser) : AuthRepository {
        val nicknames = mutableListOf<String>()

        override val sessionExpired: Flow<Unit> = emptyFlow()

        override fun hasSessionExpired(sessionId: String): Boolean = error("미등록")

        override suspend fun login(idToken: String): AuthLoginResult = error("미등록")

        override suspend fun fetchProfile(expectedSessionId: String?): AuthProfile = error("미등록")

        override suspend fun signUp(
            nickname: String,
            termsVersion: String,
            expectedUserId: String,
            expectedSessionId: String?,
        ): AuthUser = error("미등록")

        override suspend fun updateNickname(nickname: String): AuthUser {
            nicknames += nickname
            return result(nickname)
        }

        override suspend fun signOut() = error("미등록")

        override suspend fun withdraw() = error("미등록")

        override suspend fun isLoggedIn(): Boolean = error("미등록")

        override suspend fun clearSession() = error("미등록")

        override suspend fun clearSessionIf(sessionId: String): Boolean = error("미등록")
    }

    private fun user(nickname: String?, provider: String = "apple") = AuthUser(
        id = "user-a",
        provider = provider,
        email = null,
        nickname = nickname,
        nicknameConfirmed = true,
        termsAcceptedAt = "2026-10-01",
        termsVersion = "2026-10",
        pushOptIn = false,
        profileImageUrl = null,
    )

    private fun profile(nickname: String) = AuthProfile(user = user(nickname), currentTermsVersion = "2026-10")

    private fun viewModel(
        account: AccountState = AccountState(profile = profile("로이"), hasSession = true),
        repository: AuthRepository = NicknameAuthRepository { throw AuthDomainError.Unavailable() },
        analytics: RecordingAnalyticsUseCase = RecordingAnalyticsUseCase(),
    ) = AccountSettingsViewModel(initialAccount = account, authRepository = repository, analyticsUseCase = analytics)

    private val logoutAlert = AccountSettingsDestination.Alert(AccountConfirmation.Logout)
    private val withdrawAlert = AccountSettingsDestination.Alert(AccountConfirmation.Withdraw)

    private fun nicknameSheet(
        nickname: String,
        isSaving: Boolean = false,
        errorMessage: String? = null,
    ) = AccountSettingsDestination.Nickname(NicknameEditState(nickname, isSaving, errorMessage))

    @Test
    fun logoutAndWithdrawRequireConfirmation() = runTest {
        viewModel().test(this) {
            containerHost.onLogoutTapped()
            expectState { copy(destination = logoutAlert) }
            // 같은 팝업을 다시 띄우지 않는다.
            containerHost.onLogoutTapped().join()
            containerHost.onConfirmationDismissed()
            expectState { copy(destination = null) }
            containerHost.onWithdrawTapped()
            expectState { copy(destination = withdrawAlert) }
            containerHost.onConfirmationConfirmed()
            expectState { copy(destination = null) }
            expectSideEffect(AccountSettingsSideEffect.WithdrawRequested)
            containerHost.onLogoutTapped()
            expectState { copy(destination = logoutAlert) }
            containerHost.onConfirmationConfirmed()
            expectState { copy(destination = null) }
            expectSideEffect(AccountSettingsSideEffect.LogoutRequested)
            // 확인 뒤 다이얼로그가 부르는 닫기는 아무것도 바꾸지 않는다.
            containerHost.onConfirmationDismissed().join()
        }
    }

    @Test
    fun logoutAndWithdrawTapsAreTrackedLikeIos() = runTest {
        val analytics = RecordingAnalyticsUseCase()
        viewModel(analytics = analytics).test(this) {
            containerHost.onWithdrawTapped()
            expectState { copy(destination = withdrawAlert) }
            containerHost.onConfirmationDismissed()
            expectState { copy(destination = null) }
            containerHost.onLogoutTapped()
            expectState { copy(destination = logoutAlert) }
        }
        assertEquals(
            listOf(
                AnalyticsEvent.UiAction(AnalyticsButton.ProfileWithdraw, AnalyticsScreen.Profile),
                AnalyticsEvent.UiAction(AnalyticsButton.ProfileLogout, AnalyticsScreen.Profile),
            ),
            analytics.events,
        )
    }

    @Test
    fun busyAccountDoesNotOpenConfirmationOrNickname() = runTest {
        viewModel(account = AccountState(profile = profile("로이"), hasSession = true, isLoading = true)).test(this) {
            containerHost.onLogoutTapped().join()
            containerHost.onWithdrawTapped().join()
            containerHost.onNicknameTapped().join()
        }
    }

    @Test
    fun savedNicknameUpdatesScreenAndNotifiesApp() = runTest {
        val updated = user("새이름")
        val repository = NicknameAuthRepository { updated }
        viewModel(repository = repository).test(this) {
            containerHost.onNicknameTapped()
            expectState { copy(destination = nicknameSheet("로이")) }
            containerHost.onNicknameChanged(" 새이름 ")
            expectState { copy(destination = nicknameSheet(" 새이름 ")) }
            containerHost.onNicknameSaveTapped()
            expectState { copy(destination = nicknameSheet(" 새이름 ", isSaving = true)) }
            expectState { copy(destination = null, profile = profile("새이름")) }
            expectSideEffect(AccountSettingsSideEffect.NicknameUpdated(updated))
        }
        // 앞뒤 공백을 빼고 보낸다.
        assertEquals(listOf("새이름"), repository.nicknames)
    }

    @Test
    fun switchingAccountClosesNicknameSheet() = runTest {
        val other = AuthProfile(user = user("다른").copy(id = "user-b"), currentTermsVersion = "2026-10")
        viewModel().test(this) {
            containerHost.onNicknameTapped()
            expectState { copy(destination = nicknameSheet("로이")) }
            containerHost.onAccountChanged(AccountState(profile = other, hasSession = true))
            expectState { copy(destination = null, profile = other) }
        }
    }

    @Test
    fun nicknameRulesFollowSignUp() = runTest {
        val repository = NicknameAuthRepository { throw AuthDomainError.Unavailable() }
        viewModel(account = AccountState(profile = profile(""), hasSession = true), repository = repository).test(this) {
            containerHost.onNicknameTapped()
            expectState { copy(destination = nicknameSheet("")) }
            containerHost.onNicknameSaveTapped()
            expectState { copy(destination = nicknameSheet("", errorMessage = "닉네임은 1~12자로 입력해 주세요.")) }
            // 앞뒤 공백은 길이에 세지 않는다.
            containerHost.onNicknameChanged("  열두글자딱맞는닉네임이에 ")
            expectState { copy(destination = nicknameSheet("  열두글자딱맞는닉네임이에 ")) }
            containerHost.onNicknameChanged("열세글자가넘는아주긴닉네임")
            expectState { copy(destination = nicknameSheet("열세글자가넘는아주긴닉네")) }
            // 앞 공백을 뺀 13자도 12자로 잘린다(값이 같아 상태는 그대로다).
            containerHost.onNicknameChanged("  열세글자가넘는아주긴닉네임").join()
            containerHost.onNicknameSaveTapped()
            expectState { copy(destination = nicknameSheet("열세글자가넘는아주긴닉네", isSaving = true)) }
            expectState {
                copy(destination = nicknameSheet("열세글자가넘는아주긴닉네", errorMessage = "닉네임을 바꾸지 못했어요. 다시 시도해 주세요."))
            }
        }
    }

    @Test
    fun serverInvalidNicknameShowsLengthMessage() = runTest {
        viewModel(repository = NicknameAuthRepository { throw AuthDomainError.InvalidNickname() }).test(this) {
            containerHost.onNicknameTapped()
            expectState { copy(destination = nicknameSheet("로이")) }
            containerHost.onNicknameSaveTapped()
            expectState { copy(destination = nicknameSheet("로이", isSaving = true)) }
            expectState { copy(destination = nicknameSheet("로이", errorMessage = "닉네임은 1~12자로 입력해 주세요.")) }
        }
    }

    @Test
    fun savingNicknameCannotBeDismissedOrResubmitted() = runTest {
        val gate = CompletableDeferred<AuthUser>()
        val repository = NicknameAuthRepository { gate.await() }
        viewModel(repository = repository).test(this) {
            containerHost.onNicknameTapped()
            expectState { copy(destination = nicknameSheet("로이")) }
            containerHost.onNicknameSaveTapped()
            expectState { copy(destination = nicknameSheet("로이", isSaving = true)) }
            containerHost.onNicknameDismissed().join()
            containerHost.onNicknameSaveTapped().join()
            gate.complete(user("로이"))
            expectState { copy(destination = null) }
            expectSideEffect(AccountSettingsSideEffect.NicknameUpdated(user("로이")))
        }
        assertEquals(listOf("로이"), repository.nicknames)
    }

    @Test
    fun dismissingNicknameSheetClosesIt() = runTest {
        viewModel().test(this) {
            containerHost.onNicknameTapped()
            expectState { copy(destination = nicknameSheet("로이")) }
            containerHost.onNicknameDismissed()
            expectState { copy(destination = null) }
        }
    }

    @Test
    fun accountScreenRequestsAreForwardedToProfile() = runTest {
        // 설정 홈 → 마이 → 앱 전달은 Coordinator(T1)가 한다. 여기서는 계정 화면이 보내는 요청 전부를 본다.
        viewModel(account = AccountState(hasSession = true)).test(this) {
            containerHost.onRefreshTapped()
            expectSideEffect(AccountSettingsSideEffect.RefreshRequested)
            containerHost.onBackTapped()
            expectSideEffect(AccountSettingsSideEffect.BackRequested)
        }
    }

    @Test
    fun accountChangesReplaceProfileLoadingAndMessage() = runTest {
        viewModel(account = AccountState(hasSession = true, isLoading = true)).test(this) {
            containerHost.onAccountChanged(AccountState(hasSession = true, message = "로그아웃에 실패했습니다. 다시 시도해 주세요."))
            expectState { copy(isLoading = false, message = "로그아웃에 실패했습니다. 다시 시도해 주세요.") }
            containerHost.onAccountChanged(AccountState(profile = profile("로이"), hasSession = true))
            expectState { copy(profile = profile("로이"), message = null) }
            // 프로필이 없으면 닉네임 시트를 열지 않는다.
            containerHost.onAccountChanged(AccountState(hasSession = true))
            expectState { copy(profile = null) }
            containerHost.onNicknameTapped().join()
        }
    }

    @Test
    fun providerNameKeepsIosMapping() {
        assertEquals("Apple", accountProviderName("APPLE"))
        assertEquals("Google", accountProviderName("google"))
        assertEquals("kakao", accountProviderName("kakao"))
    }
}
