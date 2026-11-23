package io.festicore.feature.profile.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.feature.profile.AccountState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewPacks =
    listOf(
        FestivalReceivedPack(id = "preview_a", name = "미리보기 페스티벌 A", packVersion = 12, latestPackVersion = 13),
        FestivalReceivedPack(id = "preview_b", name = "미리보기 페스티벌 B", packVersion = 3, checkedAt = Instant.parse("2026-11-04T03:00:00Z")),
        FestivalReceivedPack(id = "preview_c", name = "미리보기 페스티벌 C", packVersion = 5, latestPackVersion = 6, autoUpdateFailed = true),
    )

@Preview(showBackground = true)
@Composable
private fun ProfileGuestLoadedPreview() {
    FestiCoreTheme {
        ProfileContent(
            state = ProfileState(packs = PreviewPacks, packChangedSets = mapOf("preview_a" to 2), packLoad = ProfilePackLoad.Loaded),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileSignedInEmptyPreview() {
    FestiCoreTheme {
        ProfileContent(state = ProfileState(account = AccountState(hasSession = true), packLoad = ProfilePackLoad.Loaded))
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileLoadingPreview() {
    FestiCoreTheme { ProfileContent(state = ProfileState(packLoad = ProfilePackLoad.Loading)) }
}

@Preview(showBackground = true)
@Composable
private fun ProfileFailedWithMessagePreview() {
    FestiCoreTheme {
        ProfileContent(state = ProfileState(account = AccountState(message = "미리보기 계정 문구"), packLoad = ProfilePackLoad.Failed))
    }
}
