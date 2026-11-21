package io.festicore.feature.auth

import androidx.navigation3.runtime.NavKey
import io.festicore.feature.web.WebPage
import kotlinx.serialization.Serializable

/** 첫 진입 로그인 흐름(Home 위 덮개)의 화면 경로. iOS `AuthFlowState.Presentation`의 `launchLogin` · `onboarding`(가입 정보 · 약관 상세). */
@Serializable
sealed interface AuthRoute : NavKey {
    /** 첫 진입 로그인 화면(iOS S14 A `LoginView`). */
    @Serializable
    data object LaunchLogin : AuthRoute

    /**
     * 가입 정보 화면(iOS S14 B `SignUpView`). 가입 조건을 채우지 못한 프로필로 연다.
     *
     * @param nickname 서버 닉네임. 있으면 처음 한 번 미리 채운다.
     * @param currentTermsVersion 서버 현재 약관 버전.
     * @param sessionId 가입 요청을 묶을 세션. 가입 완료([LaunchLoginResult.Completed])도 이 세션을 싣는다.
     * @param isReconsent 예전 약관에 동의한 기존 회원인지(iOS `termsAcceptedAt != nil`). 참이면 처음부터 재동의 문구를 보인다.
     */
    @Serializable
    data class SignUp(
        val userId: String,
        val nickname: String?,
        val currentTermsVersion: String,
        val sessionId: String,
        val isReconsent: Boolean = false,
    ) : AuthRoute

    /** 가입 정보 위에 여는 약관 상세(iOS `AuthScreen.legalDocument`, 앱 안 웹 화면). */
    @Serializable
    data class LegalDocument(val page: WebPage) : AuthRoute
}
