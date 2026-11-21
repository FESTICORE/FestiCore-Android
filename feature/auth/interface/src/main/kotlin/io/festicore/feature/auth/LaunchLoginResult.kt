package io.festicore.feature.auth

/** 첫 진입 로그인 흐름이 상위에 알리는 결과. iOS `LoginDelegate` · `SignUpDelegate` + 로그인 직후 계정 확인 401. */
sealed interface LaunchLoginResult {
    /** 로그인과 계정 확인(필요하면 가입)까지 마쳤다. 계정 확인이 오프라인 · 서버 오류로 실패해도 iOS와 같이 덮개를 닫는다. */
    data object Completed : LaunchLoginResult

    /** "지금은 건너뛸게요"를 눌렀다. 저장하지 않아 다음 실행에 다시 뜬다. */
    data object Skipped : LaunchLoginResult

    /** 재확인으로 뜬 가입 정보를 닫았다(뒤로, 또는 가입 중 이 세션을 잃었다). 세션은 지우지 않고 재확인도 하지 않는다. */
    data object SignUpCancelled : LaunchLoginResult

    /**
     * 로그인 직후 계정 확인이 401이었다. [sessionId]는 이 로그인이 저장한 세션이다.
     * [expired]는 이 세션의 토큰 갱신이 401로 끝나 만료 이벤트가 있었는지다(있으면 새 launchLogin, 없으면 덮개를 닫는다).
     * 상위는 [sessionId]가 현재 세션과 같거나 현재 세션이 없을 때만 덮개를 바꾼다.
     */
    data class SessionLost(
        val sessionId: String,
        val expired: Boolean,
    ) : LaunchLoginResult
}
