package io.festicore.feature.bearer.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.ui.designkit.theme.FestiCoreDarkMode
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewZones = listOf(BearerZone(id = "zone_a", name = "리벳 스테이지 앞 왼쪽"), BearerZone(id = "zone_b", name = "원더 스테이지 뒤"))
private val PreviewAt = Instant.now()

private fun previewState(lifecycle: BearerFlagLifecycle, activity: BearerActivity? = BearerActivity.Slam): BearerBoardState {
    val status =
        BearerFlagStatus(
            flagId = "flg_preview",
            lifecycle = lifecycle,
            zoneId = "zone_a",
            activity = activity,
            updatedAt = PreviewAt,
            updatedBy = BearerCoBearer(userId = "usr_other", nickname = "미리보기"),
        )
    return BearerBoardState(
        festivalId = "fst_preview",
        flagId = "flg_preview",
        viewerUserId = "usr_preview",
        board = BearerBoard(profile = BearerFlagProfile(id = "flg_preview", name = "미리보기 깃발"), status = status, followerCount = 1234),
        festival = BearerFestival(id = "fst_preview", name = "페스티벌 미리보기", zones = PreviewZones),
        current = BearerBoardCurrent.of(status),
        lastActivity = activity,
    )
}

@Preview(showBackground = true)
@Composable
private fun BearerBoardActivePreview() {
    FestiCoreTheme { BearerBoardContent(state = previewState(BearerFlagLifecycle.Active)) }
}

@Preview(showBackground = true)
@Composable
private fun BearerBoardRestingOfflinePreview() {
    FestiCoreTheme {
        BearerBoardContent(
            state = previewState(BearerFlagLifecycle.Resting, activity = null).copy(isOffline = true, lastSyncedAt = PreviewAt),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerBoardScheduledPreview() {
    FestiCoreTheme { BearerBoardContent(state = previewState(BearerFlagLifecycle.Scheduled, activity = null)) }
}

@Preview(showBackground = true)
@Composable
private fun BearerBoardEndedRestartUsedPreview() {
    FestiCoreTheme {
        val state = previewState(BearerFlagLifecycle.Ended, activity = null)
        BearerBoardContent(state = state.copy(board = state.board?.copy(restartUsedToday = true)))
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerBoardCheckPreview() {
    FestiCoreTheme {
        BearerBoardContent(
            state = previewState(
                BearerFlagLifecycle.Active,
            ).copy(check = BearerBoardCheck(id = 1, title = "존을 바꿨어요", message = "연결되면 팔로워에게 알려요")),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerBoardLoadingPreview() {
    FestiCoreTheme { BearerBoardContent(state = BearerBoardState(festivalId = "fst_preview", flagId = "flg_preview", viewerUserId = null)) }
}

@Preview(showBackground = true)
@Composable
private fun BearerBoardFailedPreview() {
    FestiCoreTheme {
        BearerBoardContent(
            state = BearerBoardState(festivalId = "fst_preview", flagId = "flg_preview", viewerUserId = null, loadFailed = true),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BearerZonePickerPreview() {
    FestiCoreTheme { FestiCoreDarkMode { BearerZonePicker(zones = PreviewZones, selectedZoneId = "zone_a") } }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BearerZonePickerEmptyPreview() {
    FestiCoreTheme { FestiCoreDarkMode { BearerZonePicker(zones = emptyList(), selectedZoneId = null) } }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BearerActivityPickerPreview() {
    FestiCoreTheme { FestiCoreDarkMode { BearerActivityPicker(selected = BearerActivity.Slam) } }
}
