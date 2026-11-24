package io.festicore.service.analytics.usecase

import io.festicore.service.analytics.event.AnalyticsEvent

/** 사용자 동작 추적 계약(iOS `AnalyticsUseCase`). 구현은 `service/analytics/sources`가 Koin에 등록한다. */
interface AnalyticsUseCase {
    /** 앱 시작 시 공통 슈퍼 프로퍼티(os_type · app_version · build) 등록. */
    fun registerBaseProperties()

    /** 로그인 확정 직후 사용자 ID 연결과 로그인 슈퍼 · 사용자 프로퍼티 설정. 사용자 ID는 여기로만 보낸다. */
    fun identify(userId: String, method: String?)

    /** 핵심 이벤트 추적. */
    fun track(event: AnalyticsEvent)

    /** 로그아웃 · 탈퇴 시 계정 분리(reset)와 공통 프로퍼티 재등록. */
    fun reset()
}

/** 아무것도 보내지 않는 구현(iOS `AnalyticsUseCase.previewValue`). 미리보기 · 이벤트를 보지 않는 테스트용이다. */
object PreviewAnalyticsUseCase : AnalyticsUseCase {
    override fun registerBaseProperties() = Unit

    override fun identify(userId: String, method: String?) = Unit

    override fun track(event: AnalyticsEvent) = Unit

    override fun reset() = Unit
}
