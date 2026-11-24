package io.festicore.feature.onboarding

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "라이트", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "다크", showBackground = true, widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
internal annotation class OnboardingPreviews

@OnboardingPreviews
@Composable
private fun OnboardingFirstPagePreview() {
    OnboardingContent(state = OnboardingState(page = 0), onPageChanged = {}, onNextTapped = {}, onSkipTapped = {})
}

@OnboardingPreviews
@Composable
private fun OnboardingLastPagePreview() {
    OnboardingContent(state = OnboardingState(page = 3), onPageChanged = {}, onNextTapped = {}, onSkipTapped = {})
}
