package io.festicore.feature.auth

import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthLoginResult
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.domain.auth.google.GoogleLoginUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal const val TERMS_V1 = "2026-10"
internal const val TERMS_V2 = "2026-11"

internal fun user(
    id: String = "u1",
    nickname: String? = "페스티",
    nicknameConfirmed: Boolean = true,
    termsAcceptedAt: String? = "2026-11-21T00:00:00Z",
    termsVersion: String? = TERMS_V1,
) = AuthUser(
    id = id,
    provider = "google",
    email = null,
    nickname = nickname,
    nicknameConfirmed = nicknameConfirmed,
    termsAcceptedAt = termsAcceptedAt,
    termsVersion = termsVersion,
    pushOptIn = true,
    profileImageUrl = null,
)

internal val eligibleProfile = AuthProfile(user(), TERMS_V1)
internal val newUserProfile = AuthProfile(user(nicknameConfirmed = false, termsAcceptedAt = null, termsVersion = null), TERMS_V1)

internal class FakeGoogleLoginUseCase(private val block: suspend () -> AuthLoginResult) : GoogleLoginUseCase {
    var callCount = 0

    override suspend fun invoke(): AuthLoginResult {
        callCount++
        return block()
    }
}

internal fun loginResult(sessionId: String = "s1") = AuthLoginResult(newUser = true, user = user(), sessionId = sessionId)

/** 실서버 대신 쓰는 가짜 저장소. 호출 인자를 기록한다. */
internal class FakeAuthRepository(
    var profile: suspend () -> AuthProfile = { eligibleProfile },
    var signUpResult: suspend () -> AuthUser = { user() },
) : AuthRepository {
    val signUpCalls = mutableListOf<List<String?>>()
    var clearCount = 0
    var fetchCount = 0
    val fetchSessionIds = mutableListOf<String?>()

    /** 현재 세션. [fetchProfile]이 다른 세션을 기대하면 NotAuthenticated. */
    var currentSessionId: String? = "s1"

    /** 마지막 만료 이벤트의 세션. */
    var expiredSessionId: String? = null
    val clearIfSessionIds = mutableListOf<String>()

    override val sessionExpired: Flow<Unit> = emptyFlow()

    override suspend fun login(idToken: String): AuthLoginResult = throw AuthDomainError.Unavailable()

    override suspend fun fetchProfile(expectedSessionId: String?): AuthProfile {
        fetchCount++
        fetchSessionIds += expectedSessionId
        if (expectedSessionId != null && expectedSessionId != currentSessionId) throw AuthDomainError.NotAuthenticated()
        return profile()
    }

    override suspend fun signUp(
        nickname: String,
        termsVersion: String,
        expectedUserId: String,
        expectedSessionId: String?,
    ): AuthUser {
        signUpCalls += listOf(nickname, termsVersion, expectedUserId, expectedSessionId)
        return signUpResult()
    }

    override suspend fun isLoggedIn(): Boolean = true

    override fun hasSessionExpired(sessionId: String): Boolean = expiredSessionId == sessionId

    override suspend fun clearSession() {
        clearCount++
    }

    override suspend fun clearSessionIf(sessionId: String): Boolean {
        clearIfSessionIds += sessionId
        if (currentSessionId != sessionId) return false
        currentSessionId = null
        return true
    }
}
