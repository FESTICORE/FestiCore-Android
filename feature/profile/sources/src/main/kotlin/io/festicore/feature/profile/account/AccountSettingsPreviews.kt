package io.festicore.feature.profile.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.ui.designkit.theme.FestiCoreTheme

// 프리뷰 전용 가짜 값.
private val PreviewProfile =
    AuthProfile(
        user =
            AuthUser(
                id = "preview_user",
                provider = "google",
                email = null,
                nickname = "미리보기",
                nicknameConfirmed = true,
                termsAcceptedAt = null,
                termsVersion = null,
                pushOptIn = false,
                profileImageUrl = null,
            ),
        currentTermsVersion = "preview",
    )

@Preview(showBackground = true)
@Composable
private fun AccountSettingsSignedInPreview() {
    FestiCoreTheme { AccountSettingsContent(state = AccountSettingsState(profile = PreviewProfile)) }
}

@Preview(showBackground = true)
@Composable
private fun AccountSettingsLoadingPreview() {
    FestiCoreTheme { AccountSettingsContent(state = AccountSettingsState(isLoading = true)) }
}

@Preview(showBackground = true)
@Composable
private fun AccountSettingsUnavailablePreview() {
    FestiCoreTheme {
        AccountSettingsContent(state = AccountSettingsState(message = "로그아웃에 실패했습니다. 다시 시도해 주세요."))
    }
}

@Preview(showBackground = true)
@Composable
private fun NicknameEditPreview() {
    FestiCoreTheme {
        NicknameEditContent(state = NicknameEditState(nickname = "미리보기", errorMessage = NicknameEditState.LengthMessage))
    }
}
