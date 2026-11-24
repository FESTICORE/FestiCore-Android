package io.festicore.domain.bearer.bearer

import io.festicore.domain.bearer.entity.BearerOutboxEvent
import io.festicore.domain.bearer.entity.BearerStatusRequest
import kotlinx.coroutines.flow.Flow

/**
 * 기수 상태 변경 대기열. 보드를 닫거나 앱을 다시 켜도 남아 있고, 누른 순서대로 같은 멱등키로 보낸다. iOS `BearerStatusOutboxInterface`.
 * 건마다 누른 사용자를 남기고 그 사용자가 로그인해 있을 때만 그 사용자 세션으로 보낸다. 다른 계정 건은 주인이 돌아올 때까지 남긴다.
 */
interface BearerStatusOutbox {
    /** [ownerUserId]는 변경을 누른 사용자다. */
    suspend fun enqueue(
        flagId: String,
        ownerUserId: String,
        request: BearerStatusRequest,
    )

    /** 지금 로그인한 사용자의 보내지 못한 변경 */
    suspend fun pending(flagId: String): List<BearerStatusRequest>

    fun events(flagId: String): Flow<BearerOutboxEvent>

    /** 멈춤(`NeedsLogin` · `GaveUp`)을 풀고 남은 건을 이어 보낸다. 보드 표시 · 다시 보내기 · 로그인에서 부른다. */
    suspend fun resume()

    /** 계정이 바뀌었다. 보내던 작업을 취소하고(결과를 모르는 건은 남김) 다음 재개 때 새 사용자로 다시 고른다. */
    fun cancelWork()

    /** 탈퇴한 사용자의 건을 모두 지운다. */
    suspend fun removeAll(ownerUserId: String)
}
