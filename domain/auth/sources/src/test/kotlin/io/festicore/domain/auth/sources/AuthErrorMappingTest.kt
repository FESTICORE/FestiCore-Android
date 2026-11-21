package io.festicore.domain.auth.sources

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import io.festicore.core.network.NetworkError
import io.festicore.domain.auth.AuthDomainError
import io.festicore.service.api.AuthSessionError
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AuthErrorMappingTest {
    @Test
    fun `네트워크 · 세션 오류를 도메인 오류로 바꾼다`() {
        val table = listOf(
            NetworkError.Offline(IOException()) to AuthDomainError.Offline::class,
            NetworkError.Timeout(IOException()) to AuthDomainError.Offline::class,
            NetworkError.Http(401, null, null) to AuthDomainError.NotAuthenticated::class,
            AuthSessionError.NotAuthenticated() to AuthDomainError.NotAuthenticated::class,
            NetworkError.Http(422, "TERMS_VERSION_OUTDATED", "약관") to AuthDomainError.TermsVersionOutdated::class,
            NetworkError.Http(422, "VALIDATION_FAILED", null) to AuthDomainError.Unavailable::class,
            NetworkError.Http(400, "TERMS_VERSION_OUTDATED", null) to AuthDomainError.Unavailable::class,
            NetworkError.Http(500, null, null) to AuthDomainError.Unavailable::class,
            NetworkError.InvalidResponse("FAIL", "x") to AuthDomainError.Unavailable::class,
            NetworkError.Decoding(IllegalArgumentException()) to AuthDomainError.Unavailable::class,
            NetworkError.Unknown(RuntimeException()) to AuthDomainError.Unavailable::class,
            IllegalStateException() to AuthDomainError.Unavailable::class,
        )

        table.forEach { (error, expected) -> assertEquals(error.toString(), expected, error.toAuthDomainError()::class) }
    }

    @Test
    fun `취소와 이미 도메인 오류인 값은 그대로 둔다`() {
        val cancellation = CancellationException()
        val domain = AuthDomainError.CredentialSaveFailed()

        assertSame(cancellation, cancellation.toAuthDomainError())
        assertSame(domain, domain.toAuthDomainError())
    }

    @Test
    fun `Google 계정 선택 오류를 도메인 오류로 바꾼다`() {
        val cancellation = CancellationException()
        val inProgress = AuthDomainError.RequestInProgress()

        assertEquals(AuthDomainError.UserCancelled::class, GetCredentialCancellationException().toGoogleSignInError()::class)
        assertEquals(AuthDomainError.Unavailable::class, NoCredentialException().toGoogleSignInError()::class)
        assertEquals(AuthDomainError.Unavailable::class, RuntimeException().toGoogleSignInError()::class)
        assertSame(cancellation, cancellation.toGoogleSignInError())
        assertSame(inProgress, inProgress.toGoogleSignInError())
    }
}
