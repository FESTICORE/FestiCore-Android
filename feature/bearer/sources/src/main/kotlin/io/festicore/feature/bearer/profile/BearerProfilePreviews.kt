package io.festicore.feature.bearer.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.ui.designkit.theme.FestiCoreTheme

// 프리뷰 전용 가짜 값.
private val PreviewZones = listOf(BearerZone(id = "zone_a", name = "리벳 스테이지 앞 왼쪽"), BearerZone(id = "zone_b", name = "원더 스테이지 뒤"))
private val PreviewProfile = BearerFlagProfile(id = "flg_preview", name = "새 깃발 preview")

private fun previewState(): BearerProfileState = BearerProfileState
    .initial(festivalId = "fst_preview", flagId = "flg_preview", profile = PreviewProfile, isNewBearer = true)
    .copy(festivalName = "페스티벌 미리보기", zones = PreviewZones)

@Preview(showBackground = true)
@Composable
private fun BearerProfileEmptyPreview() {
    FestiCoreTheme { BearerProfileContent(state = previewState()) }
}

@Preview(showBackground = true)
@Composable
private fun BearerProfileFilledPreview() {
    FestiCoreTheme {
        BearerProfileContent(
            state =
                previewState().copy(
                    name = "미리보기 깃발",
                    tags = listOf(BearerFlagTag.Slam, BearerFlagTag.Newbie),
                    homeZoneId = "zone_a",
                    plan = listOf("set_1", "set_2"),
                    bearerCount = 2,
                    nameError = BearerProfileViewModel.NAME_TAKEN_MESSAGE,
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerProfileLoadingPreview() {
    FestiCoreTheme { BearerProfileContent(state = BearerProfileState.initial(festivalId = "fst_preview", flagId = "flg_preview")) }
}

@Preview(showBackground = true)
@Composable
private fun BearerProfileLoadFailedPreview() {
    FestiCoreTheme {
        BearerProfileContent(state = BearerProfileState.initial(festivalId = "fst_preview", flagId = "flg_preview").copy(loadFailed = true))
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerProfileDonePreview() {
    FestiCoreTheme {
        BearerProfileContent(
            state =
                previewState().copy(
                    savedProfile = PreviewProfile.copy(name = "미리보기 깃발", tags = listOf(BearerFlagTag.Slam), homeZoneId = "zone_a"),
                ),
        )
    }
}
