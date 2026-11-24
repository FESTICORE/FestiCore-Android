package io.festicore.feature.auth.login

import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult

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

    /**
     * 로그인이 실패했다(iOS `inner(.signInFailed)`). [showsMessage]는 화면에 오류 문구가 보이는지다(취소 · 중복 요청이면 `false`).
     * 마이 Google 바로 로그인이 취소면 조용히 끝내고 실패면 시트를 띄우는 데 쓴다(설계 쟁점 M12).
     */
    data class SignInFailed(val showsMessage: Boolean) : LaunchLoginSideEffect
}
