package io.festicore.core.storage.shared

import android.content.Context

/** 시작 시 SDK보다 먼저 동의를 확인하기 위한 작은 동기 저장소. 값은 개인 정보가 아닌 Boolean 하나다. */
internal class SharedPreferencesAnalyticsConsentStore(context: Context) : AnalyticsConsentStore {
    private val preferences = context.getSharedPreferences("festicore_analytics_consent", Context.MODE_PRIVATE)
    override fun isEnabled(): Boolean = runCatching { preferences.getBoolean("enabled", false) }.getOrDefault(false)
    override fun setEnabled(enabled: Boolean): Boolean =
        runCatching { preferences.edit().putBoolean("enabled", enabled).commit() }.getOrDefault(false)
}
