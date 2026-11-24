package io.festicore.feature.timetable

import io.festicore.domain.timetable.TimetableInterval
import io.festicore.domain.timetable.TimetableSetDetail
import io.festicore.feature.timetable.main.TimetableNotificationStatus
import io.festicore.feature.timetable.main.notifyAction
import io.festicore.feature.timetable.schedulepick.schedulePickGuide
import io.festicore.feature.timetable.setdetail.TimetableSetDetailContent
import io.festicore.feature.timetable.setdetail.scheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 설계 "P0 전 임시 숨김": 푸시 · 팩 알림(P0 · A1) 전에는 알림 약속 문구와 "알림 받기" 버튼을 숨긴다. */
class TimetablePushGateTest {
    private val content =
        TimetableSetDetailContent(
            detail =
                TimetableSetDetail("라임", "리벳 스테이지", "17:00–17:50 · 50분", TimetableSetDetail.Status.Upcoming(30), false, null, null, null),
            interval = TimetableInterval(FIRST_DAY, FIRST_DAY.plusSeconds(3000)),
            source = "",
            isScheduleLoading = false,
            isScheduleLimitReached = false,
        )

    @Test
    fun `P0 전에는 푸시 준비 플래그가 꺼져 있다`() {
        assertFalse(TimetablePushGate.IS_PUSH_READY)
    }

    @Test
    fun `P0 전에는 공연 시트가 10분 전 알림을 약속하지 않는다`() {
        assertNull(scheduleHelper(content))
        // 읽는 중 · 한도 안내는 알림 약속이 아니라 그대로 보인다.
        assertEquals("내 일정을 불러오는 중이에요", scheduleHelper(content.copy(isScheduleLoading = true)))
    }

    @Test
    fun `P0 전에는 내 일정 고르기 안내에서 알림 약속 줄을 뺀다`() {
        assertEquals("나중에 타임테이블에서 언제든 바꿀 수 있어요.", schedulePickGuide())
        assertEquals("고른 무대는 시작 10분 전에 알려 드려요.\n나중에 타임테이블에서 언제든 바꿀 수 있어요.", schedulePickGuide(isPushReady = true))
    }

    @Test
    fun `P0 · A1 전에는 공개 전 알림 받기 버튼을 숨긴다`() {
        assertNull(notifyAction(TimetableNotificationStatus.Idle, onNotify = {}))
        assertEquals("알림 받기", notifyAction(TimetableNotificationStatus.Idle, onNotify = {}, isPushReady = true)?.title)
        assertEquals("설정 중", notifyAction(TimetableNotificationStatus.Saving, onNotify = {}, isPushReady = true)?.title)
    }
}
