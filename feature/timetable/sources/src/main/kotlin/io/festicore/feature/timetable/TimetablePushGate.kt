package io.festicore.feature.timetable

/**
 * 푸시 준비 여부(설계 "P0 전 임시 숨김"). 푸시 · 기기 등록(P0)과 팩 알림 켜기(A1)가 없는 동안 `false`로 두어,
 * 알림을 약속하는 문구(공연 시트 "공연 10분 전에 알려 드려요" · 내 일정 고르기 "고른 무대는 시작 10분 전에 알려 드려요.")와
 * 눌러도 아무 일이 없는 공개 전 "알림 받기" 버튼을 숨긴다. P0 · A1이 들어오면 이 값만 `true`로 바꾼다.
 */
internal object TimetablePushGate {
    const val IS_PUSH_READY: Boolean = false
}
