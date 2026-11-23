package io.festicore.service.pack

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun `재접속 지연은 지수 증가해 30초에서 멈추고 성공 후 초기화된다`() {
        val backoff = ReconnectBackoff()
        assertEquals(listOf(1, 2, 4, 8, 16, 30, 30).map { it.seconds }, List(7) { backoff.nextDelay() })
        backoff.reset()
        assertEquals(1.seconds, backoff.nextDelay())
    }
}
