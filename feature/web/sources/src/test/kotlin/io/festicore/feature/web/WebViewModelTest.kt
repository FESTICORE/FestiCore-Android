package io.festicore.feature.web

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

class WebViewModelTest {
    private val page = WebPage(url = "https://festicore.app/terms", title = "서비스 이용약관")

    @Test
    fun `처음 나타나면 로드를 한 번만 시작하고 그 로드가 끝나면 문서를 보인다`() = runTest {
        WebViewModel(page).test(this) {
            containerHost.onAppear()
            expectState(WebState(page, loadId = 1, phase = WebPhase.Loading))
            // 다시 나타나도 새 로드를 시작하지 않는다.
            containerHost.onAppear()
            containerHost.onLoadFinished(1)
            expectState(WebState(page, loadId = 1, phase = WebPhase.Loaded))
        }
    }

    @Test
    fun `실패하면 다시 시도 전까지 실패에 머문다`() = runTest {
        WebViewModel(page).test(this) {
            containerHost.onAppear()
            expectState(WebState(page, loadId = 1, phase = WebPhase.Loading))
            containerHost.onLoadFailed(1)
            expectState(WebState(page, loadId = 1, phase = WebPhase.Failed))
        }
    }

    @Test
    fun `다시 시도하면 새 로드를 시작하고 이전 시도의 늦은 결과는 무시한다`() = runTest {
        WebViewModel(page).test(this) {
            containerHost.onAppear()
            expectState(WebState(page, loadId = 1, phase = WebPhase.Loading))
            containerHost.onLoadFailed(1)
            expectState(WebState(page, loadId = 1, phase = WebPhase.Failed))
            containerHost.onRetryTapped()
            expectState(WebState(page, loadId = 2, phase = WebPhase.Loading))
            containerHost.onLoadFinished(1)
            containerHost.onLoadFailed(1)
            containerHost.onLoadFinished(2)
            expectState(WebState(page, loadId = 2, phase = WebPhase.Loaded))
        }
    }

    @Test
    fun `닫기는 부모에 알린다`() = runTest {
        WebViewModel(page).test(this) {
            containerHost.onCloseTapped()
            expectSideEffect(WebSideEffect.BackToRoot)
        }
    }
}
