package io.festicore.domain.auth.sources

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.festicore.domain.auth.AuthDomainError
import io.festicore.domain.auth.GoogleSignInClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Credential Manager로 Google ID 토큰을 받는다. [serverClientId]는 서버가 검증하는 웹 클라이언트 ID다.
 * 로그인 뒤 Credential Manager 상태를 지워 iOS Google `signOut()`과 맞춘다. iOS `GoogleOAuthRepositoryImpl`.
 */
class CredentialManagerGoogleSignInClient(
    private val activityProvider: ActivityProvider,
    private val serverClientId: String,
) : GoogleSignInClient {
    private val inProgress = AtomicBoolean(false)

    override suspend fun signIn(): String {
        if (!inProgress.compareAndSet(false, true)) throw AuthDomainError.RequestInProgress()
        try {
            val activity = activityProvider.currentActivity() ?: throw AuthDomainError.Unavailable()
            val manager = CredentialManager.create(activity)
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val idToken = try {
                idToken(manager.getCredential(activity, request).credential)
            } catch (e: Exception) {
                throw e.toGoogleSignInError()
            }
            clearCredentialState(manager)
            return idToken
        } finally {
            inProgress.set(false)
        }
    }

    private fun idToken(credential: androidx.credentials.Credential): String {
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AuthDomainError.Unavailable()
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken.ifEmpty { throw AuthDomainError.Unavailable() }
    }

    // 지우지 못해도 받은 토큰은 쓸 수 있으므로 로그인을 실패시키지 않는다.
    private suspend fun clearCredentialState(manager: CredentialManager) {
        try {
            manager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

/** 취소는 그대로, 계정 선택 닫기는 [AuthDomainError.UserCancelled], 그 외는 [AuthDomainError.Unavailable]. */
internal fun Throwable.toGoogleSignInError(): Throwable = when (this) {
    is CancellationException, is AuthDomainError -> this
    is GetCredentialCancellationException -> AuthDomainError.UserCancelled()
    else -> AuthDomainError.Unavailable()
}
