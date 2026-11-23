package io.festicore.feature.profile

import io.festicore.domain.auth.entity.AuthProfile

/**
 * 앱이 마이 탭에 알려 주는 계정 상태. iOS `ProfileFeature.View.accountChanged(profile:hasSession:isLoading:message:)`.
 * 값은 앱(`AppCoordinator`)이 소유하고 마이 · 설정 화면은 입력으로만 받는다(설계 쟁점 M13).
 *
 * @param profile 확인한 계정. 확인 전이거나 비로그인이면 null.
 * @param hasSession 저장된 로그인 세션이 있는지.
 * @param isLoading 계정을 확인 · 로그아웃 · 탈퇴하는 중인지.
 * @param message 계정 작업 결과 문구(예: 탈퇴 결과를 확인하지 못함). 없으면 null.
 */
data class AccountState(
    val profile: AuthProfile? = null,
    val hasSession: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
)
