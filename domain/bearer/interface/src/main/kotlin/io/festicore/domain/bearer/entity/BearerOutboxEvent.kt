package io.festicore.domain.bearer.entity

/** 상태 변경 대기열이 구독 중인 기수 보드에 알리는 일. 대기열은 앱 안에서 하나다. iOS `BearerOutboxEvent`. */
sealed interface BearerOutboxEvent {
    /** 이 깃발의 보내지 못한 변경 목록(현재 사용자 건만)이 바뀌었다. */
    data class Pending(val requests: List<BearerStatusRequest>) : BearerOutboxEvent

    data class Sent(
        val id: String,
        val status: BearerFlagStatus,
    ) : BearerOutboxEvent

    /** 연결이 없어 5초 뒤 다시 보낸다. */
    data object Offline : BearerOutboxEvent

    /** 로그인이 없거나 401로 멈췄다. 대기열은 남고, 다시 로그인하거나 다시 보내기를 누르면 이어서 보낸다. */
    data object NeedsLogin : BearerOutboxEvent

    /** 서버 오류가 이어지거나 약관 동의가 필요해 멈췄다. 대기열은 남고 다시 보내기로 이어 간다. */
    data class GaveUp(val error: BearerError) : BearerOutboxEvent

    /**
     * 서버가 받지 않는 변경이거나 오래돼 버린 변경이라 이 깃발의 대기열을 비웠다. 최신 상태를 다시 읽어야 한다.
     * 만료(서울 날짜가 바뀜 · 24시간 지남)로 버렸으면 [error]는 [BearerError.Stale]이다.
     */
    data class Rejected(val error: BearerError) : BearerOutboxEvent
}
