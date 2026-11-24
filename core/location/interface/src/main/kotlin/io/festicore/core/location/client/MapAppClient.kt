package io.festicore.core.location.client

/**
 * 가는 길에서 여는 외부 지도 앱. iOS `MapApp`(URL 스킴 `nmap` · `kakaomap`) 대신 Android 패키지 이름으로 설치 여부를 본다(설계 쟁점 M10).
 * 패키지 이름은 앱 매니페스트 `<queries>`에도 적는다(Android 11+ 패키지 가시성).
 */
enum class MapApp(val packageName: String) {
    // 네이버 지도: NAVER Cloud Maps URL Scheme 문서의 인텐트 예제 `package=com.nhn.android.nmap`
    Naver("com.nhn.android.nmap"),

    // 카카오맵: Google Play 카카오맵 등록 id
    Kakao("net.daum.android.map"),
}

/** 외부 지도 앱 설치 여부 경계. iOS `MapAppClient`. */
fun interface MapAppClient {
    suspend fun isInstalled(app: MapApp): Boolean
}
