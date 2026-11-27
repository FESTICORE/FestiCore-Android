package io.festicore.feature.timetable.setdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.timetable.entity.TimetableInterval
import io.festicore.domain.timetable.entity.TimetableSetDetail
import io.festicore.domain.timetable.entity.TimetableSetOverlap
import java.time.Instant

// S03 실제 시트의 로컬 렌더용 데이터. 서버 저장·이미지 요청을 실행하지 않는다.
private val PreviewDetail = TimetableSetDetail(
    artist = "라임플래그스",
    stage = "리벳 스테이지",
    time = "17:00–17:50 · 50분",
    status = TimetableSetDetail.Status.Upcoming(92),
    isScheduled = false,
    memo = "앵콜 없이 정시에 끝나요. 리벳 앞 왼쪽이 덜 붐벼요.",
)

@Composable
private fun DetailPreviewContent(detail: TimetableSetDetail) {
    val start = Instant.parse("2026-11-20T08:00:00Z")
    TimetableSetDetailSheet(
        state = TimetableSetDetailState(
            setId = "preview",
            content = TimetableSetDetailContent(
                detail = detail,
                interval = TimetableInterval(start, start.plusSeconds(3000)),
                source = "출처: 주최측 공지 · 확인 11/20 14:00",
                isScheduleLoading = false,
                isScheduleLimitReached = false,
            ),
        ),
        now = null,
        onScheduleToggled = {},
        onDismissRequest = {},
    )
}

@Preview(name = "S03 · 기본")
@Composable
private fun SetDetailUpcomingPreview() = DetailPreviewContent(PreviewDetail)

@Preview(name = "S03 · 내 일정")
@Composable
private fun SetDetailScheduledPreview() = DetailPreviewContent(PreviewDetail.copy(isScheduled = true))

@Preview(name = "S03 · 공연 중")
@Composable
private fun SetDetailLivePreview() = DetailPreviewContent(PreviewDetail.copy(status = TimetableSetDetail.Status.Live))

@Preview(name = "S03 · 종료")
@Composable
private fun SetDetailEndedPreview() = DetailPreviewContent(PreviewDetail.copy(status = TimetableSetDetail.Status.Ended))

@Preview(name = "S03 · 시간 변경")
@Composable
private fun SetDetailChangedPreview() = DetailPreviewContent(PreviewDetail.copy(previousTime = "16:40", change = "20분 미뤄짐"))

@Preview(name = "S03 · 겹침")
@Composable
private fun SetDetailOverlapPreview() = DetailPreviewContent(
    PreviewDetail.copy(overlap = TimetableSetOverlap("크라잉버드", "원더 스테이지", "15:55–16:45", 35)),
)
