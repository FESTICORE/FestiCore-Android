package io.festicore.feature.web

import kotlinx.serialization.Serializable

/**
 * 앱 안 웹 화면의 진입 인자(iOS `WebFeature.State`의 `url` · `title`). 처음 연 주소와 같은 호스트의 https 문서만 보여 준다(iOS `WebNavigationScope.document`).
 *
 * @param url 처음 열 주소.
 * @param title 상단 바 제목.
 */
@Serializable
data class WebPage(
    val url: String,
    val title: String,
)
