package io.festicore.feature.web

/** 웹 화면 로드 단계(iOS `WebFeature.State.Phase`). */
enum class WebPhase {
    Loading,
    Loaded,
    Failed,
}

/**
 * iOS `WebFeature.State`.
 *
 * @param loadId 바뀔 때마다 웹뷰가 새로 로드한다. 콜백은 이 값을 함께 보내 이전 시도의 결과를 무시한다. 0이면 아직 로드하지 않았다.
 */
data class WebState(
    val page: WebPage,
    val loadId: Int = 0,
    val phase: WebPhase = WebPhase.Loading,
)

sealed interface WebSideEffect {
    /** 부모에게 닫기를 알린다(iOS `WebDelegate.backToRoot`). */
    data object BackToRoot : WebSideEffect
}
