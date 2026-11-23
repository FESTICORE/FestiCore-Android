package io.festicore.domain.auth.sources.google

import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthLoginResult
import io.festicore.domain.auth.google.GoogleLoginUseCase
import io.festicore.domain.auth.google.GoogleSignInClient
/** iOS `OAuthUseCaseImpl`(Google만). */
internal class GoogleLoginUseCaseImpl(
    private val googleSignInClient: GoogleSignInClient,
    private val repository: AuthRepository,
) : GoogleLoginUseCase {
    override suspend fun invoke(): AuthLoginResult = repository.login(googleSignInClient.signIn())
}
