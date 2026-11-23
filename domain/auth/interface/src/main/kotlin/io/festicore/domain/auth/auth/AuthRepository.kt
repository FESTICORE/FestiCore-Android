package io.festicore.domain.auth.auth
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthLoginResult
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import kotlinx.coroutines.flow.Flow

/** 서버 로그인 · 프로필 · 가입과 로컬 세션. 실패는 [AuthDomainError]. iOS `AuthInterface`. */
interface AuthRepository {
    /** 토큰 갱신까지 실패해 세션이 지워질 때 한 번씩 알린다. */
    val sessionExpired: Flow<Unit>

    /** 마지막 세션 만료(토큰 갱신 401)가 [sessionId] 세션의 것이면 `true`. 다른 세션의 만료는 `false`. */
    fun hasSessionExpired(sessionId: String): Boolean

    /** Google ID 토큰으로 서버에 로그인하고 받은 토큰을 새 세션으로 저장한다. */
    suspend fun login(idToken: String): AuthLoginResult

    /**
     * 프로필을 읽는다. [expectedSessionId]가 있으면 그 세션에 묶어 요청하고,
     * 현재 세션이 다르면 [AuthDomainError.NotAuthenticated]. 없으면 현재 세션에 묶는다.
     */
    suspend fun fetchProfile(expectedSessionId: String? = null): AuthProfile

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

    /**
     * 현재 세션으로 닉네임만 바꾼다(앞뒤 공백 제거). 규칙을 어기면 요청하지 않고 [AuthDomainError.InvalidNickname].
     * 세션이 없으면 [AuthDomainError.NotAuthenticated]. 401이면 그 세션을 지우고 [AuthDomainError.NotAuthenticated].
     */
    suspend fun updateNickname(nickname: String): AuthUser

    /**
     * 서버에 로그아웃하고 로컬 세션을 지운다. 세션이 없으면 요청하지 않고 끝낸다.
     * 성공 · 401이면 요청한 세션만 지우고 끝낸다. 그 외 실패는 세션을 두고 오류를 던진다.
     * 단, 그 사이 토큰 갱신 실패로 세션이 이미 지워졌으면 로그아웃을 마친 것으로 본다.
     */
    suspend fun signOut()

    /**
     * 회원 탈퇴 뒤 로컬 세션을 지운다. 세션이 없으면 요청하지 않고 [AuthDomainError.NotAuthenticated].
     * 401이면 요청한 세션을 지우고 [AuthDomainError.NotAuthenticated]. 그 외 실패는 세션을 두고 오류를 던진다.
     */
    suspend fun withdraw()

    suspend fun isLoggedIn(): Boolean

    suspend fun clearSession()

    /** 현재 세션이 [sessionId]일 때만 원자적으로 지우고 지웠으면 `true`. 그 사이 저장된 다른 세션은 남긴다. */
    suspend fun clearSessionIf(sessionId: String): Boolean
}
