package io.festicore.ui.designkit.component.list

import androidx.compose.runtime.Composable
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.component.label.FCStatusChipStatus

@FCPreviews
@Composable
private fun FCListRowsPreview() {
    FCPreviewSurface {
        FCSectionHeader("지금 무대", actionTitle = "전체보기", onAction = {})
        FCSectionHeader("최근 검색어", actionTitle = "전체 삭제", isCompact = true)
        FCActiveFlagRow(name = "앵콜원정대", meta = "원더 앞 · 떼창", time = "2분 전", status = FCStatusChipStatus.Active) {}
        FCCrewRow(name = "민지", zone = "리벳 앞 왼쪽", status = "밥", time = "3분 전", isMe = true)
        FCCrewRow(name = "민지", zone = "리벳 앞 왼쪽", status = "밥", time = "1시간 전", isStale = true)
        FCLineupRow(number = "01", artist = "노이즈캔디")
        FCLineupRow(number = "02", artist = "스모킹구스", showsSeparator = false)
        FCListRow("알림 설정", value = "켬") {}
        FCListRow("다크 모드", isSelected = true) {}
        FCListRow("잠금화면 표시", isOn = true, onIsOnChange = {})
    }
}

@FCPreviews
@Composable
private fun FCListRowsSecondPreview() {
    FCPreviewSurface {
        FCNotificationRow(title = "시간표가 바뀌었어요", message = "메인 스테이지 순서가 바뀌었어요", time = "5분 전", isRead = false)
        FCNotificationRow(title = "새 깃발", message = "팔로우한 깃발이 움직였어요", time = "1시간 전", isRead = true, isEditing = true, isSelected = true)
        FCPackRow(name = "펜타포트 2026", meta = "v3 · 12MB", hasUpdate = true) {}
        FCPlanRow(time = "19:30", artist = "실리카겔", stage = "메인 스테이지", state = FCPlanRowState.Now, isMine = true)
        FCPlanRow(time = "17:00", artist = "새소년", stage = "서브 스테이지", state = FCPlanRowState.Past)
        FCSearchResultRow(
            kind = FCSearchResultRowKind.Festival(image = null, dDay = "D-3"),
            title = "펜타포트 락 페스티벌",
            meta = "인천 · 8월 1일",
            highlightedQuery = "락",
        ) {}
        FCSearchResultRow(kind = FCSearchResultRowKind.Artist(initial = "실"), title = "실리카겔", meta = "아티스트") {}
        FCStageRow(
            stage = "메인 스테이지",
            now = FCStageRowSlot(artist = "실리카겔", time = "19:30 – 20:30"),
            next = FCStageRowSlot(artist = "새소년", time = "21:00"),
            isMine = true,
        )
    }
}
