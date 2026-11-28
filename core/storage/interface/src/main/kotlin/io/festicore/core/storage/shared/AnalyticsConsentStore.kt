package io.festicore.core.storage.shared

/** SDK 초기화 전에 읽는 선택적 분석·진단 수집 동의. 최초 설치는 거부 상태다. */
interface AnalyticsConsentStore {
    fun isEnabled(): Boolean

    /** 영구 저장에 실패하면 false. 동의하지 않은 상태에서 수집을 시작하지 않는다. */
    fun setEnabled(enabled: Boolean): Boolean
}
