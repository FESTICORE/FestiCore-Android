package io.festicore.domain.auth.google

import io.festicore.domain.auth.entity.AuthDomainError

/** Google 계정 선택. iOS `GoogleOAuthInterface`. */
interface GoogleSignInClient {
    /** Google ID 토큰을 돌려준다. 실패는 [AuthDomainError]([AuthDomainError.UserCancelled] · [AuthDomainError.RequestInProgress] · [AuthDomainError.Unavailable]). */
    suspend fun signIn(): String
}
