package io.festicore.service.auth

import io.festicore.service.auth.store.CredentialStore

/** 로그인 세션 공개 API. 토큰 값은 밖으로 내보내지 않는다. */
class AuthSessionImpl internal constructor(private val store: CredentialStore) : AuthSession {
    override suspend fun isLoggedIn(): Boolean = store.current() != null

    /** 세션에 묶인 요청(`expectedSessionId`)에 넘길 현재 세션 ID. */
    override suspend fun currentSessionId(): String? = store.current()?.sessionId

    /** 로그인 토큰을 새 세션으로 저장하고 그 sessionId를 돌려준다. 저장 실패는 `SecureStorageException`. */
    override suspend fun saveLogin(accessToken: String, refreshToken: String): String =
        store.saveNewSession(accessToken, refreshToken).sessionId

    override suspend fun clear() = store.clear()

    /** 현재 세션이 [sessionId]일 때만 지우고 지웠으면 `true`. 그 사이 다른 로그인이 저장한 세션은 지우지 않는다. */
    override suspend fun clearIfCurrent(sessionId: String): Boolean = store.clearIfSession(sessionId)
}
