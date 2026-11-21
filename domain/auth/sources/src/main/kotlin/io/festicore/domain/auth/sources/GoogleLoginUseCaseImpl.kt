package io.festicore.domain.auth.sources

import io.festicore.domain.auth.AuthLoginResult
import io.festicore.domain.auth.AuthRepository
import io.festicore.domain.auth.GoogleLoginUseCase
import io.festicore.domain.auth.GoogleSignInClient

/** iOS `OAuthUseCaseImpl`(Google만). */
internal class GoogleLoginUseCaseImpl(
    private val googleSignInClient: GoogleSignInClient,
    private val repository: AuthRepository,
) : GoogleLoginUseCase {
    override suspend fun invoke(): AuthLoginResult = repository.login(googleSignInClient.signIn())
}
