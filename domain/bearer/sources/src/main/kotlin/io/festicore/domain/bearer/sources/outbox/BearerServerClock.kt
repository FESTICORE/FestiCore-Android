package io.festicore.domain.bearer.sources.outbox

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 서버 시각 추정 시계. 오프셋 = 기수 응답 `Date` 헤더 − 그 응답을 받은 기기 시각. 메모리에만 두고, 모르면 0(기기 시각 그대로, iOS와 같음).
 * iOS에는 없다. 서버가 5분 넘게 미래인 `clientAt`을 400으로 거절하므로(`flag-rules.ts:171-176`) 누른 시각 · 만료 판단을 이 시계로 한다.
 */
internal class BearerServerClock(private val device: Clock = Clock.systemUTC()) : Clock() {
    @Volatile private var offset: Duration = Duration.ZERO

    /** 응답을 받은 지금 서버 시각이 [serverDate]였다. */
    fun record(serverDate: Instant) {
        offset = Duration.between(device.instant(), serverDate)
    }

    override fun instant(): Instant = device.instant().plus(offset)

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = throw UnsupportedOperationException("UTC 시계만 쓴다")
}
