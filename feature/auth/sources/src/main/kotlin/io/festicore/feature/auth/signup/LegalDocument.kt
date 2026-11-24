package io.festicore.feature.auth.signup

/** 가입 화면에서 상세를 여는 필수 약관. 경로는 서버 `GET api/v1/legal/{path}`. iOS `LegalDocument`. */
enum class LegalDocument(
    val path: String,
    val title: String,
) {
    Terms("terms", "서비스 이용약관"),
    Privacy("privacy", "개인정보 수집 · 이용"),
    Location("location", "위치기반서비스 이용약관"),
}
