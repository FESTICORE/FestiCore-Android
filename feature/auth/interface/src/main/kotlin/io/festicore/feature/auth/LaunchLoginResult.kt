package io.festicore.feature.auth

/** 첫 진입 로그인 흐름이 상위에 알리는 결과. 두 결과 모두 덮개를 닫는다. iOS `LoginDelegate` · `SignUpDelegate`. */
sealed interface LaunchLoginResult {
    /** 로그인과 계정 확인(필요하면 가입)까지 마쳤다. 계정 확인이 오프라인 · 서버 오류로 실패해도 iOS와 같이 덮개를 닫는다. */
    data object Completed : LaunchLoginResult

    /** "지금은 건너뛸게요"를 눌렀다. 저장하지 않아 다음 실행에 다시 뜬다. */
    data object Skipped : LaunchLoginResult
}
