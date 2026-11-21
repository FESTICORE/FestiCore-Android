package io.festicore.service.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 토큰 갱신이 401로 끝나 세션을 지웠을 때 한 번 알린다. 앱은 로그인 화면으로 돌아간다. iOS `AuthSessionExpiryClient`. */
class SessionExpiryEvents {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val generation = MutableStateFlow(0L)

    val events: SharedFlow<Unit> = flow.asSharedFlow()

    /** 만료 횟수. 구독 전 만료도 잃지 않도록 상태로 둔다. 구독 시점 값보다 커지면 그 뒤 만료가 있었다. */
    val expiredGeneration: StateFlow<Long> = generation.asStateFlow()

    internal suspend fun emit() {
        generation.update { it + 1 }
        flow.emit(Unit)
    }
}
