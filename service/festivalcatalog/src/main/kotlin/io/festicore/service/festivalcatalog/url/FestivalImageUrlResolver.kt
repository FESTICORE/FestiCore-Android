package io.festicore.service.festivalcatalog.url

import java.net.URI
import java.net.URISyntaxException

/**
 * 썸네일 값을 불러올 주소로 바꾼다. iOS `FestivalImageURLResolver`.
 * - 스킴이 있으면 https일 때만 그대로 쓴다.
 * - 스킴이 없으면 `/`로 시작하고 `//`로 시작하지 않으며 [baseUrl]이 https일 때만 [baseUrl] 기준으로 합친다.
 * - 나머지는 null.
 */
class FestivalImageUrlResolver(private val baseUrl: String?) {
    fun resolve(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val uri = value.toUriOrNull() ?: return null
        uri.scheme?.let { scheme -> return value.takeIf { scheme.equals(HTTPS, ignoreCase = true) } }
        if (!value.startsWith("/") || value.startsWith("//")) return null
        val base = baseUrl?.toUriOrNull() ?: return null
        if (!base.scheme.equals(HTTPS, ignoreCase = true)) return null
        return base.resolve(uri).toString()
    }

    private fun String.toUriOrNull(): URI? = try {
        URI(this)
    } catch (_: URISyntaxException) {
        null
    }

    private companion object {
        const val HTTPS = "https"
    }
}
