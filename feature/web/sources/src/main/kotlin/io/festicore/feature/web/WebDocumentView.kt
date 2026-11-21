package io.festicore.feature.web

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * `android.webkit.WebView` 래퍼(iOS `WebRepresentableView`). 로드 시도([loadId])마다 웹뷰와 클라이언트를 새로 만들어,
 * 콜백이 그 웹뷰를 연 시도의 `loadId`로만 알리게 한다. 이전 시도의 늦은 콜백은 이전 `loadId`로 와서 무시된다.
 * 렌더 프로세스가 끝나면 실패를 알리고 웹뷰를 새로 만든다. 새 웹뷰는 다시 시도([loadId] 증가) 전까지 로드하지 않는다.
 *
 * @param isInteractive 문서가 보이는 상태인지. 아니면 접근성 트리에서 웹뷰를 숨긴다(iOS `accessibilityHidden`).
 */
@Composable
internal fun WebDocumentView(
    url: String,
    loadId: Int,
    isInteractive: Boolean,
    onFinished: (Int) -> Unit,
    onFailed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnFailed by rememberUpdatedState(onFailed)
    var generation by remember { mutableIntStateOf(0) }
    var stoppedLoadId by remember { mutableIntStateOf(0) }
    key(loadId, generation) {
        val client = remember {
            WebDocumentClient(
                loadId = loadId,
                allowedHost = WebNavigationPolicy.hostOf(url),
                onFinished = { currentOnFinished(it) },
                onFailed = { currentOnFailed(it) },
                onRenderProcessGone = {
                    stoppedLoadId = loadId
                    generation += 1
                },
            )
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    configureForDocument()
                    webViewClient = client
                }
            },
            modifier = modifier,
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
            update = { webView ->
                webView.importantForAccessibility =
                    if (isInteractive) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                if (loadId > 0 && loadId != stoppedLoadId && !client.isStarted) client.start(url, webView)
            },
        )
    }
}

/**
 * iOS `WKWebViewConfiguration()` 기본값에 맞춘다(JavaScript · DOM 저장소 켜짐, 새 창 · 자동 팝업 없음).
 * 파일 · content 주소 접근과 혼합 콘텐츠는 막는다. JavaScript 인터페이스는 붙이지 않는다.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForDocument() {
    setBackgroundColor(Color.TRANSPARENT)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
    }
}

/**
 * iOS `WebRepresentableView.Coordinator`. 한 로드 시도([loadId])에만 쓰고, 모든 콜백을 그 `loadId`로 알린다.
 * 새 창을 만들지 않아(`setSupportMultipleWindows(false)`) 새 창 링크도 지금 웹뷰에서 이 정책을 거친다(iOS `loadInPlace`).
 */
internal class WebDocumentClient(
    private val loadId: Int,
    private val allowedHost: String?,
    private val onFinished: (Int) -> Unit,
    private val onFailed: (Int) -> Unit,
    private val onRenderProcessGone: () -> Unit,
) : WebViewClient() {
    var isStarted: Boolean = false
        private set

    /** 아직 첫 페이지를 받지 못했다. 이때 막힌 main frame 이동은 실패로 본다. */
    private var isAwaitingFirstPage = false

    /** 이미 실패를 알렸다. 그 뒤 오는 완료 콜백이 결과를 바꾸지 않게 한다. */
    private var hasFailed = false

    fun start(url: String, webView: WebView) {
        if (isStarted) return
        isStarted = true
        isAwaitingFirstPage = true
        // WebView는 처음 loadUrl을 shouldOverrideUrlLoading에 넘기지 않아 여기서 같은 정책을 먼저 본다.
        if (!WebNavigationPolicy.allowsDocument(url, allowedHost)) {
            fail()
            return
        }
        webView.loadUrl(url)
    }

    private fun fail() {
        if (!isStarted || hasFailed) return
        hasFailed = true
        isAwaitingFirstPage = false
        onFailed(loadId)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (WebNavigationPolicy.allowsDocument(request.url?.toString(), allowedHost)) return false
        // 첫 페이지 전에 main frame이 막히면(다른 호스트 · http 리다이렉트) 로딩에 머물지 않게 실패로 넘긴다.
        if (isAwaitingFirstPage && request.isForMainFrame) fail()
        return true
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (!WebNavigationPolicy.acceptsResponse(errorResponse.statusCode, request.isForMainFrame)) fail()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) fail()
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        // 인증서 오류는 항상 거절한다. 첫 페이지 전이면 실패로 알린다.
        handler.cancel()
        if (isAwaitingFirstPage) fail()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (!isStarted || hasFailed) return
        isAwaitingFirstPage = false
        onFinished(loadId)
    }

    /** 렌더 프로세스가 끝나면 빈 화면 대신 실패와 다시 시도를 보인다. `true`를 돌려 앱이 같이 끝나지 않게 한다. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        fail()
        onRenderProcessGone()
        return true
    }
}
