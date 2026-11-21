package io.festicore.feature.web

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** iOS `WebFeature`. 로드 시도마다 [WebState.loadId]를 올리고, 지금 시도의 결과만 반영한다. */
class WebViewModel(page: WebPage) :
    ViewModel(),
    OrbitContainerHost<WebState, WebState, WebSideEffect> {
    override val container = orbitContainer<WebState, WebSideEffect>(WebState(page))

    /** 처음 한 번만 로드를 시작한다. */
    fun onAppear() = intent {
        if (state.loadId != 0) return@intent
        reduce { state.copy(loadId = 1, phase = WebPhase.Loading) }
    }

    fun onCloseTapped() = intent { postSideEffect(WebSideEffect.BackToRoot) }

    fun onRetryTapped() = intent {
        reduce { state.copy(loadId = state.loadId + 1, phase = WebPhase.Loading) }
    }

    fun onLoadFinished(loadId: Int) = intent {
        if (loadId != state.loadId) return@intent
        reduce { state.copy(phase = WebPhase.Loaded) }
    }

    fun onLoadFailed(loadId: Int) = intent {
        if (loadId != state.loadId) return@intent
        reduce { state.copy(phase = WebPhase.Failed) }
    }
}
