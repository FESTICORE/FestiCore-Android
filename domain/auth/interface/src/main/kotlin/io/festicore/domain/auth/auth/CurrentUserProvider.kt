package io.festicore.domain.auth.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * 서버가 확인한 현재 사용자 ID. 비로그인 · 세션을 잃었으면 null. iOS `AuthFlowState.verifiedUserID`.
 * 값은 app의 루트 Coordinator만 바꾼다. 화면은 구독해 사용자별 기록(최근 검색 등)의 키로 쓴다.
 */
interface CurrentUserProvider {
    val currentUserId: StateFlow<String?>
}
