package io.festicore.feature.auth

/**
 * iOS `LoginFeature.State`(시트 문맥 · 흐름 문구 제외).
 *
 * @param isSigningIn 로그인부터 계정 확인까지 진행 중. 이 동안 화면 전체가 비활성이다.
 * @param attemptId 로그인 시도 번호. 현재 시도가 아닌 결과는 버린다.
 */
data class LaunchLoginState(
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
    val attemptId: Int = 0,
)

/** iOS `LoginDelegate`(+ 계정 확인 결과). */
sealed interface LaunchLoginSideEffect {
    data class Finished(val result: LaunchLoginResult) : LaunchLoginSideEffect

    /** 가입 조건을 채우지 못했다. 덮개 안에서 가입 정보로 간다. */
    data class SignUpRequired(val route: AuthRoute.SignUp) : LaunchLoginSideEffect
}
