package io.festicore.service.pack

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 전송 오류 후 재접속 지연 `min(30, 2^n)`초. 성공하면 초기화한다. iOS `ReconnectBackoff`. */
internal class ReconnectBackoff {
    private var attempt = 0

    fun nextDelay(): Duration {
        val seconds = minOf(30, 1 shl minOf(attempt, 5))
        attempt += 1
        return seconds.seconds
    }

    fun reset() {
        attempt = 0
    }
}
