package io.festicore.service.analytics.configuration

import android.content.Context
import io.festicore.service.analytics.usecase.MixpanelApiClient
import io.festicore.service.analytics.usecase.MixpanelClient

/** iOS `MixpanelConfiguration`. 토큰이 비거나 공백이면 Mixpanel 객체를 만들지 않는다(디스크 · 네트워크를 쓰지 않는다). */
internal object MixpanelConfiguration {
    /** 시작한 Mixpanel(iOS `Mixpanel.safeMainInstance()`). 토큰이 없으면 null이라 아무것도 보내지 않는다. */
    @Volatile
    var mainInstance: MixpanelClient? = null

    fun token(raw: String): String? = raw.trim().ifEmpty { null }

    fun configure(
        context: Context,
        rawToken: String,
        create: (Context, String) -> MixpanelClient = MixpanelApiClient::create,
    ) {
        val token = token(rawToken) ?: return
        if (mainInstance != null) return
        mainInstance = runCatching { create(context.applicationContext ?: context, token) }.getOrNull()
    }
}
