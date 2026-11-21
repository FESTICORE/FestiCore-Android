package io.festicore.domain.auth.sources

import io.festicore.domain.auth.AuthDomainError
import io.festicore.domain.auth.AuthLoginResult
import io.festicore.domain.auth.AuthNicknameRule
import io.festicore.domain.auth.AuthProfile
import io.festicore.domain.auth.AuthRepository
import io.festicore.domain.auth.AuthUser
import io.festicore.service.api.AuthApi
import io.festicore.service.api.AuthLoginBody
import io.festicore.service.api.AuthUserDto
import io.festicore.service.api.MeResponse
import io.festicore.service.api.SignUpBody
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.SessionExpiryEvents
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** iOS `AuthRepositoryImpl`. 401 뒤 세션 정리와 만료 알림은 service/auth의 토큰 갱신이 맡는다. */
internal class AuthRepositoryImpl(
    private val api: AuthApi,
    private val session: AuthSession,
    private val sessionExpiryEvents: SessionExpiryEvents,
) : AuthRepository {
    override val sessionExpired: Flow<Unit> = sessionExpiryEvents.events.map { }

    override fun hasSessionExpired(sessionId: String): Boolean = sessionExpiryEvents.expired.value.sessionId == sessionId

    override suspend fun login(idToken: String): AuthLoginResult {
        if (idToken.isBlank()) throw AuthDomainError.Unavailable()
        val response = mapAuthErrors { api.login(AuthLoginBody(provider = GOOGLE, token = idToken)) }
        if (response.accessToken.isEmpty() || response.refreshToken.isEmpty()) throw AuthDomainError.Unavailable()
        val sessionId = try {
            session.saveLogin(response.accessToken, response.refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw AuthDomainError.CredentialSaveFailed()
        }
        return AuthLoginResult(newUser = response.newUser, user = response.user.toDomain(), sessionId = sessionId)
    }

    override suspend fun fetchProfile(expectedSessionId: String?): AuthProfile {
        val currentSessionId = session.currentSessionId() ?: throw AuthDomainError.NotAuthenticated()
        if (expectedSessionId != null && expectedSessionId != currentSessionId) throw AuthDomainError.NotAuthenticated()
        val sessionId = expectedSessionId ?: currentSessionId
        val response = mapAuthErrors { api.me(expectedSessionId = sessionId) }
        if (response.currentTermsVersion.isEmpty()) throw AuthDomainError.Unavailable()
        return response.toDomain()
    }

    override suspend fun signUp(
        nickname: String,
        termsVersion: String,
        expectedUserId: String,
        expectedSessionId: String?,
    ): AuthUser {
        val sessionId = expectedSessionId ?: session.currentSessionId() ?: throw AuthDomainError.NotAuthenticated()
        val trimmed = AuthNicknameRule.trimmed(nickname)
        if (!AuthNicknameRule.isValid(trimmed) || termsVersion.isEmpty()) throw AuthDomainError.Unavailable()
        val body = SignUpBody(nickname = trimmed, acceptTerms = SignUpBody.AcceptTerms(termsVersion), pushOptIn = true)
        val user = mapAuthErrors { api.patchMe(body, expectedSessionId = sessionId) }
        // 요청 사이 계정이 바뀌었으면 다른 계정의 가입 결과다.
        if (user.id != expectedUserId) throw AuthDomainError.Unavailable()
        return user.toDomain()
    }

    override suspend fun isLoggedIn(): Boolean = session.isLoggedIn()

    override suspend fun clearSession() = session.clear()

    override suspend fun clearSessionIf(sessionId: String): Boolean = session.clearIfCurrent(sessionId)

    private companion object {
        const val GOOGLE = "google"
    }
}

internal fun AuthUserDto.toDomain() = AuthUser(
    id = id,
    provider = provider,
    email = email,
    nickname = nickname,
    nicknameConfirmed = nicknameConfirmed,
    termsAcceptedAt = termsAcceptedAt,
    termsVersion = termsVersion,
    pushOptIn = pushOptIn,
    profileImageUrl = profileImageUrl,
)

internal fun MeResponse.toDomain() = AuthProfile(
    user = AuthUser(
        id = id,
        provider = provider,
        email = email,
        nickname = nickname,
        nicknameConfirmed = nicknameConfirmed,
        termsAcceptedAt = termsAcceptedAt,
        termsVersion = termsVersion,
        pushOptIn = pushOptIn,
        profileImageUrl = profileImageUrl,
    ),
    currentTermsVersion = currentTermsVersion,
)
