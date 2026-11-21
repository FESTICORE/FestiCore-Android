package io.festicore.service.auth

import io.festicore.core.network.NetworkError
import io.festicore.service.api.RefreshResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 401을 받은 요청을 다시 보낼지에 대한 판단. */
internal sealed interface RefreshResult {
    /** 같은 세션의 [accessToken]으로 한 번 재요청한다. */
    data class Retry(val accessToken: String) : RefreshResult {
        override fun toString(): String = "Retry"
    }

    /** 세션이 없거나 바뀌었다. 재전송하지 않는다. */
    data object NotAuthenticated : RefreshResult
}

/**
 * 토큰 갱신의 유일한 경로. 서버가 refresh 토큰을 회전 · 폐기하므로 [Mutex]로 직렬화한다. iOS `TokenRefresher` · `FestiCoreAuthenticator`.
 * [refreshTokens]는 인증 플러그인을 거치지 않는 `POST api/v1/auth/refresh` 호출이다.
 */
internal class TokenRefresher(
    private val store: CredentialStore,
    private val expiryEvents: SessionExpiryEvents,
    private val refreshTokens: suspend (refreshToken: String) -> RefreshResponse,
) {
    private val mutex = Mutex()

    /**
     * ([sessionId], [revision])로 보낸 요청이 401을 받았을 때 부른다.
     * refresh가 오프라인 · 5xx 등으로 실패하면 세션을 유지하고 그 [NetworkError]를 던진다.
     */
    suspend fun onUnauthorized(sessionId: String, revision: Long): RefreshResult = mutex.withLock {
        val current = store.current()
        if (current == null || current.sessionId != sessionId) return@withLock RefreshResult.NotAuthenticated
        if (current.revision != revision) return@withLock RefreshResult.Retry(current.accessToken)

        val renewed = try {
            refreshTokens(current.refreshToken)
        } catch (e: NetworkError.Http) {
            if (!e.isUnauthorized) throw e
            if (store.clearIf(sessionId, revision)) {
                expiryEvents.emit(sessionId)
                return@withLock RefreshResult.NotAuthenticated
            }
            return@withLock latest(sessionId)
        }
        if (store.compareAndSetTokens(sessionId, revision, renewed.accessToken, renewed.refreshToken)) {
            RefreshResult.Retry(renewed.accessToken)
        } else {
            // 그 사이 로그인 교체 · 삭제가 있었다. 갱신 결과는 버린다.
            latest(sessionId)
        }
    }

    private suspend fun latest(sessionId: String): RefreshResult {
        val latest = store.current()?.takeIf { it.sessionId == sessionId } ?: return RefreshResult.NotAuthenticated
        return RefreshResult.Retry(latest.accessToken)
    }
}
