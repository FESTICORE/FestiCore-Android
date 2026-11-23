package io.festicore.feature.profile.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.feature.profile.AccountState
import io.festicore.feature.profile.appearance.AppearanceMode
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
private fun SettingsHomeGuestPreview() {
    FestiCoreTheme { SettingsHomeContent(state = SettingsHomeState(), appVersion = "1.0.0") }
}

@Preview(showBackground = true)
@Composable
private fun SettingsHomeSignedInPreview() {
    FestiCoreTheme {
        SettingsHomeContent(
            state = SettingsHomeState(
                account = AccountState(profile = PreviewProfile, hasSession = true),
                appearanceMode = AppearanceMode.System,
            ),
            appVersion = "1.0.0",
        )
    }
}
