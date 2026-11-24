package io.festicore.service.analytics.event

/** 추적하는 핵심 이벤트. iOS `AnalyticsEvent`와 같은 경우 · 값만 둔다. 토큰 · 개인정보 속성은 두지 않는다. */
sealed interface AnalyticsEvent {
    /** 신규 계정으로 로그인이 확정됐을 때. */
    data class SignUp(val method: AnalyticsProvider) : AnalyticsEvent

    /** 화면 진입. */
    data class ScreenView(
        val screen: AnalyticsScreen,
        val referrer: AnalyticsScreen? = null,
    ) : AnalyticsEvent

    /** 버튼 탭 전반. */
    data class UiAction(
        val action: AnalyticsButton,
        val screen: AnalyticsScreen,
    ) : AnalyticsEvent

    /** 깃발 팔로우 · 해제. */
    data class FlagFollow(val data: FlagFollowData) : AnalyticsEvent

    /** 타임테이블 조작. */
    data class TimetableAction(val data: TimetableActionData) : AnalyticsEvent

    /** 페스티벌 팩 동기화 결과. */
    data class PackSync(val data: PackSyncData) : AnalyticsEvent
}
