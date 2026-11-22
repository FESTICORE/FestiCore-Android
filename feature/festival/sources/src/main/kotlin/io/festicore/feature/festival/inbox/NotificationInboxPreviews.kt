package io.festicore.feature.festival.inbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.usernotification.UserNotificationItem
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewNow = Instant.parse("2026-11-04T03:00:00Z")

private val PreviewItems =
    listOf(
        UserNotificationItem("n1", "crew.gather", "preview", "모여! · 크루원", "A 무대 앞으로 모여 주세요", PreviewNow.minusSeconds(300), null, null),
        UserNotificationItem(
            "n2",
            "pack.updated",
            "preview",
            "시간표가 바뀌었어요",
            "B Stage 공연 시각이 바뀌었어요",
            PreviewNow.minusSeconds(3_600),
            PreviewNow,
            null,
        ),
        UserNotificationItem("n3", "festival.start", "preview", "곧 시작해요", "첫 공연이 30분 뒤 시작해요", PreviewNow.minusSeconds(90_000), null, null),
    )

@Preview(showBackground = true)
@Composable
private fun NotificationInboxListPreview() {
    FestiCoreTheme {
        NotificationInboxContent(
            state = NotificationInboxState(load = NotificationInboxLoad.Loaded, items = PreviewItems, nextCursor = "next"),
            now = PreviewNow,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationInboxSelectionPreview() {
    FestiCoreTheme {
        NotificationInboxContent(
            state =
                NotificationInboxState(
                    load = NotificationInboxLoad.Loaded,
                    items = PreviewItems,
                    isEditing = true,
                    selectedIds = setOf("n1"),
                ),
            now = PreviewNow,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationInboxLoadingPreview() {
    FestiCoreTheme { NotificationInboxContent(state = NotificationInboxState(load = NotificationInboxLoad.Loading), now = PreviewNow) }
}

@Preview(showBackground = true)
@Composable
private fun NotificationInboxEmptyPreview() {
    FestiCoreTheme { NotificationInboxContent(state = NotificationInboxState(load = NotificationInboxLoad.Loaded), now = PreviewNow) }
}

@Preview(showBackground = true)
@Composable
private fun NotificationInboxFailedPreview() {
    FestiCoreTheme { NotificationInboxContent(state = NotificationInboxState(load = NotificationInboxLoad.Failed), now = PreviewNow) }
}
