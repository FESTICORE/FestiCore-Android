package io.festicore.domain.auth

import kotlinx.coroutines.flow.Flow

/** 서버 로그인 · 프로필 · 가입과 로컬 세션. 실패는 [AuthDomainError]. iOS `AuthInterface`. */
interface AuthRepository {
    /** 토큰 갱신까지 실패해 세션이 지워질 때 한 번씩 알린다. */
    val sessionExpired: Flow<Unit>

    /** Google ID 토큰으로 서버에 로그인하고 받은 토큰을 새 세션으로 저장한다. */
    suspend fun login(idToken: String): AuthLoginResult

    suspend fun fetchProfile(): AuthProfile

    /**
     * 닉네임 · 약관 동의로 가입한다. 응답 사용자가 [expectedUserId]가 아니면 [AuthDomainError.Unavailable].
     * [expectedSessionId]가 있으면 그 세션일 때만 보내고, 없으면 현재 세션에 묶는다.
     */
    suspend fun signUp(
        nickname: String,
        termsVersion: String,
        expectedUserId: String,
        expectedSessionId: String? = null,
    ): AuthUser

    suspend fun isLoggedIn(): Boolean

    suspend fun clearSession()
}
