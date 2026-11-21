package io.festicore.service.auth

/** 로그인 세션 공개 API. 토큰 값은 밖으로 내보내지 않는다. */
class AuthSession internal constructor(private val store: CredentialStore) {
    suspend fun isLoggedIn(): Boolean = store.current() != null

    /** 세션에 묶인 요청(`expectedSessionId`)에 넘길 현재 세션 ID. */
    suspend fun currentSessionId(): String? = store.current()?.sessionId

    /** 로그인 토큰을 새 세션으로 저장하고 그 sessionId를 돌려준다. 저장 실패는 `SecureStorageException`. */
    suspend fun saveLogin(accessToken: String, refreshToken: String): String = store.saveNewSession(accessToken, refreshToken).sessionId

    suspend fun clear() = store.clear()
}
