package io.festicore.feature.web

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import io.festicore.feature.web.policy.WebNavigationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebDocumentClientTest {
    private val url = "https://festicore.app/terms"
    private val finished = mutableListOf<Int>()
    private val failed = mutableListOf<Int>()

    private fun client(loadId: Int) = WebDocumentClient(
        loadId = loadId,
        allowedHost = WebNavigationPolicy.hostOf(url),
        onFinished = { finished += it },
        onFailed = { failed += it },
        onRenderProcessGone = {},
    )

    private fun webView() = WebView(RuntimeEnvironment.getApplication())

    @Test
    fun `다시 시도 뒤 이전 시도의 늦은 콜백은 이전 loadId로만 알린다`() {
        val first = client(loadId = 1)
        val firstView = webView()
        first.start(url, firstView)
        val second = client(loadId = 2)
        val secondView = webView()
        second.start(url, secondView)

        // 멈춘 시도 1의 늦은 HTTP 오류 · 완료는 시도 2를 실패/성공으로 바꾸지 않는다.
        first.onReceivedHttpError(firstView, mainFrameRequest(), WebResourceResponse("text/html", "utf-8", 500, "Error", emptyMap(), null))
        first.onPageFinished(firstView, url)
        assertEquals(listOf(1), failed)
        assertEquals(emptyList<Int>(), finished)

        second.onPageFinished(secondView, url)
        assertEquals(listOf(2), finished)
    }

    @Test
    fun `실패를 알린 시도는 늦은 완료로 성공이 되지 않는다`() {
        val client = client(loadId = 1)
        val view = webView()
        client.start(url, view)
        client.onReceivedHttpError(view, mainFrameRequest(), WebResourceResponse("text/html", "utf-8", 404, "Not Found", emptyMap(), null))
        client.onPageFinished(view, url)
        assertEquals(listOf(1), failed)
        assertEquals(emptyList<Int>(), finished)
    }

    @Test
    fun `시작하지 않은 웹뷰의 콜백은 알리지 않는다`() {
        val client = client(loadId = 1)
        client.onPageFinished(webView(), url)
        assertEquals(emptyList<Int>(), finished)
        assertEquals(emptyList<Int>(), failed)
    }

    @Test
    fun `정책을 통과하지 못한 처음 주소는 로드하지 않고 그 시도의 실패로 알린다`() {
        val client = client(loadId = 3)
        val view = webView()
        client.start("http://festicore.app/terms", view)
        assertNull(shadowOf(view).lastLoadedUrl)
        assertEquals(listOf(3), failed)
    }

    private fun mainFrameRequest(): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(this@WebDocumentClientTest.url)

        override fun isForMainFrame(): Boolean = true

        override fun isRedirect(): Boolean = false

        override fun hasGesture(): Boolean = false

        override fun getMethod(): String = "GET"

        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }
}
