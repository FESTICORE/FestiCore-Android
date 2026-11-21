package io.festicore.feature.auth

import android.net.Uri

/** 가입 화면에서 상세를 여는 필수 약관. 경로는 서버 `GET api/v1/legal/{path}`. iOS `LegalDocument`. */
enum class LegalDocument(
    val path: String,
    val title: String,
) {
    Terms("terms", "서비스 이용약관"),
    Privacy("privacy", "개인정보 수집 · 이용"),
    Location("location", "위치기반서비스 이용약관"),
}

/** 약관 상세 주소를 만든다. [baseUrl]은 app이 `BuildConfig.LEGAL_BASE_URL`(끝이 `/`)로 넘긴다. iOS `LegalDocumentURL`. */
class LegalDocumentUrls(private val baseUrl: String) {
    fun url(document: LegalDocument): Uri = Uri.parse(baseUrl).buildUpon().appendPath(document.path).build()
}
