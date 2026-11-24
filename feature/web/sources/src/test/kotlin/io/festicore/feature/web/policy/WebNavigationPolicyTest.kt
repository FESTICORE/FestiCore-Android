package io.festicore.feature.web.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `WebNavigationPolicyTests`의 document 범위와 응답 판정. */
class WebNavigationPolicyTest {
    private val host = "festicore.app"

    @Test
    fun `같은 호스트의 https 문서만 연다`() {
        assertTrue(WebNavigationPolicy.allowsDocument("https://festicore.app/terms", host))
        assertTrue(WebNavigationPolicy.allowsDocument("https://FESTICORE.app/privacy", host))
        assertFalse(WebNavigationPolicy.allowsDocument("http://festicore.app/terms", host))
        assertFalse(WebNavigationPolicy.allowsDocument("https://evil.example/terms", host))
        assertFalse(WebNavigationPolicy.allowsDocument("mailto:help@festicore.app", host))
        assertFalse(WebNavigationPolicy.allowsDocument("file:///data/terms", host))
        assertFalse(WebNavigationPolicy.allowsDocument("https://festicore.app/terms", null))
        assertFalse(WebNavigationPolicy.allowsDocument(null, host))
        assertFalse(WebNavigationPolicy.allowsDocument("https://bad host/", host))
    }

    @Test
    fun `main frame 오류 상태는 거절한다`() {
        assertTrue(WebNavigationPolicy.acceptsResponse(statusCode = 200, isMainFrame = true))
        assertFalse(WebNavigationPolicy.acceptsResponse(statusCode = 404, isMainFrame = true))
        assertFalse(WebNavigationPolicy.acceptsResponse(statusCode = 500, isMainFrame = true))
        assertTrue(WebNavigationPolicy.acceptsResponse(statusCode = 404, isMainFrame = false))
        assertFalse(WebNavigationPolicy.acceptsResponse(statusCode = null, isMainFrame = true))
    }
}
