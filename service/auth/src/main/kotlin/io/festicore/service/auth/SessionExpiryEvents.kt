package io.festicore.service.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 토큰 갱신이 401로 끝나 세션을 지웠을 때 한 번 알린다. 앱은 로그인 화면으로 돌아간다. iOS `AuthSessionExpiryClient`. */
class SessionExpiryEvents {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val events: SharedFlow<Unit> = flow.asSharedFlow()

    internal suspend fun emit() = flow.emit(Unit)
}
