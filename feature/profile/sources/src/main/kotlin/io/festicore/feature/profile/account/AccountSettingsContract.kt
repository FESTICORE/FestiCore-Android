package io.festicore.feature.profile.account

import io.festicore.domain.auth.entity.AuthNicknameRule
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser

/**
 * 계정 화면 상태. iOS `AccountSettingsFeature.State`. 계정 값은 앱이 소유하고(설계 쟁점 M13) 화면은 `onAccountChanged`로 받는다.
 *
 * @param profile 확인한 계정. 닉네임을 바꾸면 화면이 먼저 갱신한다.
 * @param isLoading 계정을 확인 · 로그아웃 · 탈퇴하는 중인지. 처리 중에는 팝업 · 닉네임 시트를 열지 않는다.
 * @param message 계정 작업 결과 문구. 처리 중이 아닐 때만 보인다.
 * @param destination 떠 있는 확인 팝업 또는 닉네임 시트. iOS `destination`(+ `confirmation`).
 */
data class AccountSettingsState(
    val profile: AuthProfile? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val destination: AccountSettingsDestination? = null,
)

/** iOS `AccountSettingsFeature.Destination`. 확인 팝업은 무엇을 확인하는지(`Confirmation`)를 함께 가진다. */
sealed interface AccountSettingsDestination {
    data class Alert(val confirmation: AccountConfirmation) : AccountSettingsDestination

    /** 닉네임 변경 시트. iOS `NicknameEditFeature.State`. */
    data class Nickname(val edit: NicknameEditState) : AccountSettingsDestination
}

/** 확인 팝업 문구. iOS `FCAlertState.logout()` · `withdraw()`(취소 버튼 "취소"). */
enum class AccountConfirmation(
    val title: String,
    val message: String,
    val confirmTitle: String,
) {
    Logout("로그아웃할까요?", "받은 팩과 내 일정은 이 기기에 그대로 남아 있어요.", "로그아웃"),
    Withdraw("회원 탈퇴", "계정과 로그인 정보가 삭제됩니다. 이 작업은 되돌릴 수 없습니다.", "탈퇴"),
}

/**
 * 닉네임 변경 시트 상태. iOS `NicknameEditFeature.State`. 가입 화면과 같은 1~12자 규칙(`AuthNicknameRule`).
 *
 * @param nickname 입력 값. `AuthNicknameRule.limited`로 자른 값이다.
 * @param isSaving 저장 요청 중. 이 동안 시트를 닫을 수 없다.
 * @param errorMessage 입력란 아래 오류 문구.
 */
data class NicknameEditState(
    val nickname: String,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val trimmedNickname: String get() = AuthNicknameRule.trimmed(nickname)

    val isValid: Boolean get() = AuthNicknameRule.isValid(nickname)

    companion object {
        /** 길이 규칙을 어겼거나 서버가 `invalidNickname`이라고 답했을 때. iOS `NicknameEditFeature.lengthMessage`. */
        val LengthMessage = "닉네임은 ${AuthNicknameRule.lengthRange.first}~${AuthNicknameRule.maxLength}자로 입력해 주세요."

        /** 그 외 저장 실패. */
        const val SAVE_FAILED_MESSAGE = "닉네임을 바꾸지 못했어요. 다시 시도해 주세요."

        /** 입력란 도움말. iOS `NicknameEditView` helper. */
        val HelperText = "${AuthNicknameRule.lengthRange.first}~${AuthNicknameRule.maxLength}자로 입력해 주세요"
    }
}

/** iOS `AccountSettingsDelegate`. 실제 로그아웃 · 탈퇴 · 재확인은 앱(`AccountCoordinator`, A1)이 하고, 경로 연결은 T1이다. */
sealed interface AccountSettingsSideEffect {
    /** 설정 홈으로 돌아간다. */
    data object BackRequested : AccountSettingsSideEffect

    /** 로그아웃 확인을 눌렀다. */
    data object LogoutRequested : AccountSettingsSideEffect

    /** 회원 탈퇴 확인을 눌렀다. */
    data object WithdrawRequested : AccountSettingsSideEffect

    /** "계정 정보 다시 확인". 앱이 세션 프로필을 다시 읽는다. */
    data object RefreshRequested : AccountSettingsSideEffect

    /** 닉네임을 저장했다. 앱이 계정 상태(마이 · 설정 홈 값)를 갱신한다. */
    data class NicknameUpdated(val user: AuthUser) : AccountSettingsSideEffect
}

/** 계정 화면 "로그인" 값. iOS `AccountSettingsView.providerName`(설계 쟁점 M1: Apple 표기도 그대로 둔다). */
internal fun accountProviderName(provider: String): String = when (provider.lowercase()) {
    "apple" -> "Apple"
    "google" -> "Google"
    else -> provider
}
