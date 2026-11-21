package io.festicore.service.auth

import io.festicore.core.datastore.SecureStorage
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 저장된 로그인 세션. [sessionId]는 로그인 저장 때만 새로 발급하고, [revision]은 같은 세션의 토큰 갱신마다 1씩 오른다.
 * 토큰이 로그에 남지 않게 [toString]에서 뺀다.
 */
@Serializable
internal data class Session(
    val sessionId: String,
    val revision: Long,
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "Session(sessionId=$sessionId, revision=$revision)"
}

/**
 * 세션 한 값을 JSON 하나로 [SecureStorage] 한 키에 원자 저장한다(반쪽 쌍 없음). 프로세스 안 동시 접근은 [Mutex]로 막고 메모리에 캐시한다.
 * 손상 · 복호화 실패 값은 비로그인으로 보고 지운다. 쓰기 실패는 [io.festicore.core.datastore.SecureStorageException].
 */
internal class CredentialStore(private val storage: SecureStorage) {
    private val mutex = Mutex()
    private var loaded = false
    private var cached: Session? = null

    suspend fun current(): Session? = mutex.withLock { load() }

    suspend fun saveNewSession(accessToken: String, refreshToken: String): Session = mutex.withLock {
        Session(UUID.randomUUID().toString(), 0, accessToken, refreshToken).also { write(it) }
    }

    /** 현재 세션이 ([expectedSessionId], [expectedRevision])일 때만 토큰을 바꾸고 revision을 올린다. */
    suspend fun compareAndSetTokens(
        expectedSessionId: String,
        expectedRevision: Long,
        accessToken: String,
        refreshToken: String,
    ): Boolean = mutex.withLock {
        val current = load()
        if (current == null || current.sessionId != expectedSessionId || current.revision != expectedRevision) return@withLock false
        write(current.copy(revision = current.revision + 1, accessToken = accessToken, refreshToken = refreshToken))
        true
    }

    suspend fun clear() = mutex.withLock { remove() }

    /** 현재 세션이 ([expectedSessionId], [expectedRevision])일 때만 지운다. */
    suspend fun clearIf(expectedSessionId: String, expectedRevision: Long): Boolean = mutex.withLock {
        val current = load()
        if (current == null || current.sessionId != expectedSessionId || current.revision != expectedRevision) return@withLock false
        remove()
        true
    }

    /** 현재 세션이 [expectedSessionId]일 때만 지운다(revision 무관). 다른 세션이 저장됐거나 이미 비었으면 `false`. */
    suspend fun clearIfSession(expectedSessionId: String): Boolean = mutex.withLock {
        if (load()?.sessionId != expectedSessionId) return@withLock false
        remove()
        true
    }

    private suspend fun load(): Session? {
        if (loaded) return cached
        val raw = storage.read(KEY)
        val session = raw?.let {
            try {
                json.decodeFromString<Session>(it)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        if (raw != null && session == null) storage.remove(KEY)
        cached = session
        loaded = true
        return session
    }

    private suspend fun write(session: Session) {
        storage.write(KEY, json.encodeToString(session))
        cached = session
        loaded = true
    }

    private suspend fun remove() {
        storage.remove(KEY)
        cached = null
        loaded = true
    }

    private companion object {
        const val KEY = "festicore.auth.session"
        val json = Json
    }
}
