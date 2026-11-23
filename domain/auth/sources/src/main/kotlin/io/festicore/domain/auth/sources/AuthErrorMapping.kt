package io.festicore.domain.auth.sources

import io.festicore.core.network.NetworkError
import io.festicore.domain.auth.AuthDomainError
import io.festicore.service.api.base.AuthSessionError
import kotlin.coroutines.cancellation.CancellationException

/** 네트워크 · 세션 오류를 [AuthDomainError]로 바꾼다. 취소는 그대로 둔다. iOS `AuthDomainError+Network.swift`. */
internal fun Throwable.toAuthDomainError(): Throwable = when (this) {
    is CancellationException, is AuthDomainError -> this

    is NetworkError.Offline, is NetworkError.Timeout -> AuthDomainError.Offline()

    is NetworkError.Http -> when {
        isUnauthorized -> AuthDomainError.NotAuthenticated()
        status == 422 && code == TERMS_VERSION_OUTDATED -> AuthDomainError.TermsVersionOutdated()
        else -> AuthDomainError.Unavailable()
    }

    is AuthSessionError.NotAuthenticated -> AuthDomainError.NotAuthenticated()

    else -> AuthDomainError.Unavailable()
}

internal inline fun <T> mapAuthErrors(block: () -> T): T = try {
    block()
} catch (e: Exception) {
    throw e.toAuthDomainError()
}

private const val TERMS_VERSION_OUTDATED = "TERMS_VERSION_OUTDATED"
