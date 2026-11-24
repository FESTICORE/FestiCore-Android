package io.festicore.feature.bearer.claim

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private const val PREVIEW_FESTIVAL_ID = "fst_preview"
private val PreviewNow = Instant.parse("2026-11-24T10:00:00Z")

@Preview(showBackground = true)
@Composable
private fun BearerClaimEmptyPreview() {
    FestiCoreTheme { BearerClaimContent(state = BearerClaimState(festivalId = PREVIEW_FESTIVAL_ID)) }
}

@Preview(showBackground = true)
@Composable
private fun BearerClaimTypingPreview() {
    FestiCoreTheme { BearerClaimContent(state = BearerClaimState(festivalId = PREVIEW_FESTIVAL_ID, code = "R4K9")) }
}

@Preview(showBackground = true)
@Composable
private fun BearerClaimInvalidPreview() {
    FestiCoreTheme {
        BearerClaimContent(
            state =
                BearerClaimState(
                    festivalId = PREVIEW_FESTIVAL_ID,
                    code = "R4K9T2",
                    failure = BearerClaimFailure.Invalid(remainingAttempts = 2),
                    consecutiveFailures = 1,
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerClaimLockedPreview() {
    FestiCoreTheme {
        BearerClaimContent(
            state =
                BearerClaimState(
                    festivalId = PREVIEW_FESTIVAL_ID,
                    failure = BearerClaimFailure.Locked,
                    lockedUntil = PreviewNow.plusSeconds(600),
                    now = PreviewNow,
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerClaimMessagePreview() {
    FestiCoreTheme {
        BearerClaimContent(
            state =
                BearerClaimState(
                    festivalId = PREVIEW_FESTIVAL_ID,
                    code = "R4K9T2",
                    failure = BearerClaimFailure.Message(BearerClaimViewModel.message(BearerError.BearerLimit())),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerClaimSubmittingPreview() {
    FestiCoreTheme { BearerClaimContent(state = BearerClaimState(festivalId = PREVIEW_FESTIVAL_ID, code = "R4K9T2", isSubmitting = true)) }
}
