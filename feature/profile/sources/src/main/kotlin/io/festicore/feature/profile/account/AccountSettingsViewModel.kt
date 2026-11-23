package io.festicore.feature.profile.account

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthNicknameRule
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.feature.profile.AccountState
import kotlin.coroutines.cancellation.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 계정(Figma S13-D) + 닉네임 변경 시트. iOS `AccountSettingsFeature` + `NicknameEditFeature`(Orbit은 자식 Store가 없어 한 ViewModel이 시트 상태도 가진다).
 * 로그아웃 · 탈퇴는 확인 팝업 뒤 요청(SideEffect)까지만 보낸다. 실행은 앱(`AccountCoordinator`, A1)이 한다.
 * 분석 `profile_logout` · `profile_withdraw`는 `service/analytics`가 없어 보내지 않는다.
 *
 * @param initialAccount 화면을 만들 때의 계정 상태.
 */
class AccountSettingsViewModel(
    initialAccount: AccountState,
    private val authRepository: AuthRepository,
) : ViewModel(),
    OrbitContainerHost<AccountSettingsState, AccountSettingsState, AccountSettingsSideEffect> {
    override val container =
        orbitContainer<AccountSettingsState, AccountSettingsSideEffect>(
            AccountSettingsState(
                profile = initialAccount.profile,
                isLoading = initialAccount.isLoading,
                message = initialAccount.message,
            ),
        )

    // view

    /** 앱 코디네이터가 계정 상태가 바뀔 때 보내는 입력. iOS `accountChanged(profile:isLoading:message:)`. */
    fun onAccountChanged(account: AccountState) = intent {
        // 다른 계정으로 바뀌면 이전 계정에서 연 시트 · 팝업을 닫는다. 이전 계정 입력이 새 계정에 저장되지 않게 한다.
        val accountChanged = account.profile?.user?.id != state.profile?.user?.id
        val next =
            state.copy(
                profile = account.profile,
                isLoading = account.isLoading,
                message = account.message,
                destination = if (accountChanged) null else state.destination,
            )
        if (next != state) reduce { next }
    }

    fun onBackTapped() = intent { postSideEffect(AccountSettingsSideEffect.BackRequested) }

    /** 프로필이 있고 처리 중이 아닐 때만 닉네임 시트를 연다. */
    fun onNicknameTapped() = intent {
        val user = state.profile?.user ?: return@intent
        if (state.isLoading) return@intent
        reduce { state.copy(destination = AccountSettingsDestination.Nickname(NicknameEditState(nickname = user.nickname ?: ""))) }
    }

    fun onRefreshTapped() = intent { postSideEffect(AccountSettingsSideEffect.RefreshRequested) }

    fun onWithdrawTapped() = presentConfirmation(AccountConfirmation.Withdraw)

    fun onLogoutTapped() = presentConfirmation(AccountConfirmation.Logout)

    /** 확인 팝업의 확인 버튼. iOS `alert(.confirmTapped)`. */
    fun onConfirmationConfirmed() = intent {
        val confirmation = (state.destination as? AccountSettingsDestination.Alert)?.confirmation ?: return@intent
        reduce { state.copy(destination = null) }
        postSideEffect(
            when (confirmation) {
                AccountConfirmation.Logout -> AccountSettingsSideEffect.LogoutRequested
                AccountConfirmation.Withdraw -> AccountSettingsSideEffect.WithdrawRequested
            },
        )
    }

    /** 확인 팝업의 취소 · 딤 · 뒤로. iOS `alert(.cancelTapped)` · `dismiss`. */
    fun onConfirmationDismissed() = intent {
        if (state.destination is AccountSettingsDestination.Alert) reduce { state.copy(destination = null) }
    }

    /** 닉네임 입력. iOS `NicknameEditFeature.View.nicknameChanged`. */
    fun onNicknameChanged(value: String) = intent {
        val edit = state.nicknameEdit() ?: return@intent
        reduce { state.withNickname(edit.copy(nickname = AuthNicknameRule.limited(value), errorMessage = null)) }
    }

    /** 닉네임 저장. iOS `NicknameEditFeature.View.saveTapped`. 저장 중이면 무시한다. */
    fun onNicknameSaveTapped() = intent {
        val edit = state.nicknameEdit() ?: return@intent
        if (edit.isSaving) return@intent
        if (!edit.isValid) {
            reduce { state.withNickname(edit.copy(errorMessage = NicknameEditState.LengthMessage)) }
            return@intent
        }
        reduce { state.withNickname(edit.copy(isSaving = true, errorMessage = null)) }
        saveNickname(edit.trimmedNickname)
    }

    /** 시트 딤 · 끌기 · 뒤로. 저장 중에는 닫지 않는다(iOS `fcSheetDismissDisabled`). */
    fun onNicknameDismissed() = intent {
        val edit = state.nicknameEdit() ?: return@intent
        if (edit.isSaving) return@intent
        reduce { state.copy(destination = null) }
    }

    // async

    private suspend fun saveNickname(nickname: String) = subIntent {
        val result =
            try {
                Result.success(authRepository.updateNickname(nickname))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        result.fold(onSuccess = { onNicknameSaved(it) }, onFailure = { onNicknameFailed(it) })
    }

    // inner

    /** iOS `nickname(.delegate(.saved))`: 시트를 닫고 화면의 프로필을 바꾼 뒤 앱에 알린다. */
    private suspend fun onNicknameSaved(user: AuthUser) = subIntent {
        // 저장 중 계정이 바뀌었으면 이전 계정 응답은 버린다.
        if (state.profile?.user?.id != user.id) return@subIntent
        reduce { state.copy(destination = null, profile = state.profile?.copy(user = user)) }
        postSideEffect(AccountSettingsSideEffect.NicknameUpdated(user))
    }

    private suspend fun onNicknameFailed(error: Throwable) = subIntent {
        val edit = state.nicknameEdit() ?: return@subIntent
        val message =
            if (error is AuthDomainError.InvalidNickname) NicknameEditState.LengthMessage else NicknameEditState.SAVE_FAILED_MESSAGE
        reduce { state.withNickname(edit.copy(isSaving = false, errorMessage = message)) }
    }

    /** 처리 중이거나 같은 팝업이 떠 있으면 다시 띄우지 않는다. iOS `presentConfirmation`. */
    private fun presentConfirmation(confirmation: AccountConfirmation) = intent {
        val alert = AccountSettingsDestination.Alert(confirmation)
        if (state.isLoading || state.destination == alert) return@intent
        reduce { state.copy(destination = alert) }
    }

    private fun AccountSettingsState.nicknameEdit(): NicknameEditState? = (destination as? AccountSettingsDestination.Nickname)?.edit

    private fun AccountSettingsState.withNickname(edit: NicknameEditState) = copy(destination = AccountSettingsDestination.Nickname(edit))
}
