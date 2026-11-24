package io.festicore.feature.web.policy

import java.net.URI
import java.net.URISyntaxException

/**
 * 웹 화면 안에서 허용할 이동(iOS `WebNavigationPolicy`의 `document` 범위).
 * 예매(`booking`) 범위는 쓰는 화면이 아직 없어 옮기지 않았다.
 */
internal object WebNavigationPolicy {
    /** 처음 연 주소와 같은 호스트의 https 문서만 연다. 다른 호스트 · http · 앱 스킴은 열지 않는다(외부로도 넘기지 않는다). */
    fun allowsDocument(url: String?, allowedHost: String?): Boolean {
        val uri = url?.let(::parse) ?: return false
        val host = uri.host?.lowercase() ?: return false
        val expected = allowedHost?.lowercase() ?: return false
        return uri.scheme?.lowercase() == "https" && host == expected
    }

    /** main frame 응답은 HTTP 상태가 200..<300일 때만 문서로 보여 준다. */
    fun acceptsResponse(statusCode: Int?, isMainFrame: Boolean): Boolean {
        if (!isMainFrame) return true
        return statusCode != null && statusCode in 200..299
    }

    /** 허용 호스트. [url]을 읽지 못하면 `null`이다. */
    fun hostOf(url: String): String? = parse(url)?.host

    private fun parse(url: String): URI? = try {
        URI(url)
    } catch (_: URISyntaxException) {
        null
    }
}
