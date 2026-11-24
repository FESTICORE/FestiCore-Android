package io.festicore.service.analytics.usecase

import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.service.analytics.event.AnalyticsEvent

/**
 * iOS `AnalyticsUseCase.liveValue`. 토큰이 없어 Mixpanel을 시작하지 않았으면 [mixpanel]이 null을 돌려 아무것도 보내지 않는다.
 * 사용자 결정: Sentry 제외. iOS의 `SentrySDK.setUser` · 사용자 동작 breadcrumb · `user.action.count`는 옮기지 않는다.
 */
internal class AnalyticsUseCaseImpl(
    private val mixpanel: () -> MixpanelClient?,
    private val baseSuperProperties: Map<String, Any>,
) : AnalyticsUseCase {
    override fun registerBaseProperties() {
        mixpanel()?.registerSuperProperties(baseSuperProperties)
    }

    override fun identify(userId: String, method: String?) {
        if (userId.isEmpty()) return
        val client = mixpanel() ?: return
        client.identify(userId)
        // 로그인 컨텍스트를 슈퍼 프로퍼티로 등록 → 이후 모든 이벤트에 자동 첨부.
        val superProperties = mutableMapOf<String, Any>("is_logged_in" to true)
        if (!method.isNullOrEmpty()) {
            superProperties["login_provider"] = method
            client.setPeopleProperty("provider", method)
        }
        client.registerSuperProperties(superProperties)
    }

    override fun track(event: AnalyticsEvent) {
        val name = eventName(event)
        val properties = eventProperties(event)
        FestiCoreLogger.debug("Mixpanel track — event: $name, properties: $properties", FestiCoreLogCategory.APP)
        mixpanel()?.track(name, properties)
    }

    override fun reset() {
        val client = mixpanel() ?: return
        // 슈퍼 프로퍼티 포함 전체 초기화 → 계정 분리. 공통 프로퍼티만 다시 등록한다(is_logged_in 없음 = 로그아웃).
        client.reset()
        client.registerSuperProperties(baseSuperProperties)
    }
}
