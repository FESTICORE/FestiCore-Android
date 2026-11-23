package io.festicore.service.auth
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.auth.refresh.RefreshResult
import io.festicore.service.auth.refresh.TokenRefresher
import io.festicore.service.auth.store.CredentialStore
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Bearer 표시([BearerAuthorizationKey])가 있는 요청에만 현재 세션 토큰을 붙인다. iOS `AuthorizingInterceptor`.
 * - 세션이 없거나 기대 sessionId와 다르면 보내지 않고 [AuthSessionError.NotAuthenticated]
 * - 401이면 보낼 때의 (sessionId, revision)으로 [TokenRefresher]에 묻고, 재요청은 한 번만 한다
 */
internal fun festiCoreAuthPlugin(store: CredentialStore, refresher: TokenRefresher): ClientPlugin<Unit> =
    createClientPlugin("FestiCoreAuth") {
        on(Send) { request ->
            val authorization = request.attributes.getOrNull(BearerAuthorizationKey) ?: return@on proceed(request)
            val session = store.current()
            if (session == null || (authorization.expectedSessionId != null && authorization.expectedSessionId != session.sessionId)) {
                throw AuthSessionError.NotAuthenticated()
            }
            request.headers[HttpHeaders.Authorization] = "Bearer ${session.accessToken}"
            val call = proceed(request)
            if (call.response.status != HttpStatusCode.Unauthorized) return@on call

            when (val result = refresher.onUnauthorized(session.sessionId, session.revision)) {
                RefreshResult.NotAuthenticated -> throw AuthSessionError.NotAuthenticated()

                is RefreshResult.Retry -> {
                    request.headers[HttpHeaders.Authorization] = "Bearer ${result.accessToken}"
                    proceed(request)
                }
            }
        }
    }
