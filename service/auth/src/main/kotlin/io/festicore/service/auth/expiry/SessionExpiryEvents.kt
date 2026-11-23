package io.festicore.service.auth.expiry

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * 세션 만료 한 건. [generation]은 만료 횟수(처음 값 0), [sessionId]는 그때 지운 세션이다(만료가 없었으면 `null`).
 * 로그인 시도는 자기가 저장한 sessionId와 같은 만료만 자기 만료로 본다.
 */
data class SessionExpiry(
    val generation: Long,
    val sessionId: String?,
)

/** 토큰 갱신이 401로 끝나 세션을 지웠을 때 한 번 알린다. 앱은 로그인 화면으로 돌아간다. iOS `AuthSessionExpiryClient`. */
class SessionExpiryEvents {
    private val flow = MutableSharedFlow<SessionExpiry>(extraBufferCapacity = 1)
    private val latest = MutableStateFlow(SessionExpiry(generation = 0, sessionId = null))

    val events: SharedFlow<SessionExpiry> = flow.asSharedFlow()

    /** 마지막 만료. 구독 전 만료도 잃지 않도록 상태로 둔다. 구독 시점 세대보다 커지면 그 뒤 만료가 있었다. */
    val expired: StateFlow<SessionExpiry> = latest.asStateFlow()

    internal suspend fun emit(sessionId: String) {
        val expiry = latest.updateAndGet { SessionExpiry(generation = it.generation + 1, sessionId = sessionId) }
        flow.emit(expiry)
    }
}
