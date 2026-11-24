package io.festicore.domain.bearer.sources.outbox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 보드 밖에서 대기열을 이어 보내는 시점(iOS에 없음, 설계 쟁점 B4). iOS는 보드 표시 · 다시 보내기에서만 재개한다.
 * - 현재 사용자가 바뀌면(로그아웃 · 탈퇴 · 다른 계정 · 세션 만료) 보내던 작업을 취소한다.
 * - 현재 사용자가 생기면(앱 시작 뒤 `AppCoordinator`가 확인 · 로그인) 멈춤을 풀고 재개한다.
 * - 연결이 돌아오면 사용자가 있을 때만 자동 재개한다. 멈춘 대기열은 풀지 않는다.
 */
internal class BearerStatusOutboxResumer(
    private val outbox: BearerStatusOutboxImpl,
    private val currentUserId: StateFlow<String?>,
    private val reconnected: Flow<Unit>,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var previous: String? = null
            currentUserId.collect { userId ->
                if (userId != previous) outbox.cancelWork()
                previous = userId
                if (userId != null) outbox.resume()
            }
        }
        scope.launch {
            reconnected.collect { if (currentUserId.value != null) outbox.resumeAutomatically() }
        }
    }
}
