package io.festicore.feature.web

import java.net.URI
import java.net.URISyntaxException

/**
 * 약관 · 정보 출처 문서 주소를 만든다. 가입 화면과 설정 홈이 함께 쓴다. iOS `Feature/Web` `LegalDocumentURL`.
 *
 * 기준 주소가 주소로 읽히고 scheme이 `https`이고 host가 비어 있지 않을 때만 쓴다. 아니면 문서 주소가 없다(null).
 *
 * @param baseUrl app이 `BuildConfig.LEGAL_BASE_URL`(예: `https://host/api/v1/legal/`)로 넘긴다.
 */
class LegalDocumentUrls(baseUrl: String) {
    private val base: URI? =
        try {
            URI(baseUrl).takeIf { it.scheme == "https" && !it.host.isNullOrEmpty() }
        } catch (_: URISyntaxException) {
            null
        }

    /** 기준 주소 뒤에 경로 한 칸([path], 예: `terms`)을 붙인 주소. 기준 주소를 쓸 수 없으면 null(iOS `appending(path:)`). */
    fun url(path: String): String? {
        val base = base ?: return null
        return URI(base.scheme, base.rawAuthority, base.path.orEmpty().trimEnd('/') + "/" + path, null, null).toString()
    }
}
