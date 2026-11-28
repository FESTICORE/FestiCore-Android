package io.festicore.service.device

import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun registrationLocale(locale: Locale): String {
    val language = locale.language.takeIf { it.matches(Regex("[A-Za-z]{2,3}")) } ?: return "en"
    val normalized = try {
        val clean = Locale.Builder().setLocale(locale).clearExtensions().build()
        Locale.Builder().setLanguage(clean.language).setScript(clean.script).setRegion(clean.country).build().toLanguageTag()
    } catch (_: java.util.IllformedLocaleException) {
        language
    }
    return normalized.takeIf { it.length <= 35 && it.matches(Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) } ?: language
}

internal fun deviceRegistrationMetadata(context: Context) = buildJsonObject {
    @Suppress("DEPRECATION")
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    put("appVersion", requireNotNull(version))
    put("osVersion", Build.VERSION.RELEASE)
    put("locale", registrationLocale(Locale.getDefault()))
    put("timezone", TimeZone.getDefault().id)
}
