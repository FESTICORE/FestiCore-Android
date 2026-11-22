package io.festicore.domain.festival.sources

import io.festicore.core.network.NetworkError
import io.festicore.domain.festival.FestivalLiveBoardError
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import io.festicore.domain.festival.FestivalScheduleError
import io.festicore.service.api.AuthSessionError
import io.festicore.service.festivalcatalog.FestivalLiveBoardServiceError
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 폴링 간격 · 서버 시각 보정 · 오류 매핑. iOS `FestivalLiveBoardFeature.watchLiveBoard` · `Presentation.boardNow`. */
class FestivalLiveBoardUseCaseTest {
    private val t0 = Instant.parse("2026-11-22T00:00:00Z")

    @Test
    fun `조회 간격은 35초 · 공식 소식 300초 · 시각 갱신 30초다`() {
        assertEquals(35.seconds, FestivalLiveBoardUseCase.POLL_INTERVAL)
        assertEquals(300.seconds, FestivalLiveBoardUseCase.OFFICIAL_NEWS_INTERVAL)
        assertEquals(30.seconds, FestivalLiveBoardUseCase.CLOCK_REFRESH_INTERVAL)
    }

    @Test
    fun `429면 35초와 Retry-After 중 긴 쪽, 그 밖은 35초 뒤 다시 조회한다`() {
        assertEquals(35.seconds, FestivalLiveBoardUseCase.nextPollDelay(null))
        assertEquals(35.seconds, FestivalLiveBoardUseCase.nextPollDelay(FestivalLiveBoardError.RateLimited(10.seconds)))
        assertEquals(90.seconds, FestivalLiveBoardUseCase.nextPollDelay(FestivalLiveBoardError.RateLimited(90.seconds)))
        assertEquals(35.seconds, FestivalLiveBoardUseCase.nextPollDelay(FestivalLiveBoardError.Unavailable()))
        assertEquals(35.seconds, FestivalLiveBoardUseCase.nextPollDelay(FestivalLiveBoardError.FestivalNotFound()))
    }

    @Test
    fun `serverAt이 바뀌었거나 기준점이 없으면 기준점을 지금으로 둔다`() {
        val anchor = t0.plusSeconds(1)
        val later = t0.plusSeconds(40)

        assertEquals(later, FestivalLiveBoardUseCase.clockAnchor(null, null, t0, later))
        assertEquals(later, FestivalLiveBoardUseCase.clockAnchor(t0, anchor, t0.plusSeconds(35), later))
        assertEquals(anchor, FestivalLiveBoardUseCase.clockAnchor(t0, anchor, t0, later))
        assertEquals(later, FestivalLiveBoardUseCase.clockAnchor(t0, null, t0, later))
    }

    @Test
    fun `화면 시각은 serverAt에 첫 수신 뒤 흐른 기기 시간을 더한다`() {
        val serverAt = Instant.parse("2026-11-22T03:00:00Z")
        val anchor = t0
        val deviceNow = t0.plusSeconds(90)

        assertEquals(serverAt.plusSeconds(90), FestivalLiveBoardUseCase.boardNow(serverAt, anchor, deviceNow))
        assertEquals(deviceNow, FestivalLiveBoardUseCase.boardNow(null, anchor, deviceNow))
        assertEquals(deviceNow, FestivalLiveBoardUseCase.boardNow(serverAt, null, deviceNow))
    }

    @Test
    fun `서비스 · 네트워크 오류를 라이브 보드 오류로 바꾼다`() {
        val rateLimited = FestivalLiveBoardServiceError.RateLimited(20.seconds).toFestivalLiveBoardError()

        assertEquals(20.seconds, (rateLimited as FestivalLiveBoardError.RateLimited).retryAfter)
        assertTrue(FestivalLiveBoardServiceError.FestivalNotFound().toFestivalLiveBoardError() is FestivalLiveBoardError.FestivalNotFound)
        assertTrue(FestivalLiveBoardServiceError.InvalidFestivalId().toFestivalLiveBoardError() is FestivalLiveBoardError.InvalidFestivalId)
        assertTrue(FestivalLiveBoardServiceError.InvalidResponse().toFestivalLiveBoardError() is FestivalLiveBoardError.InvalidResponse)
        assertTrue(NetworkError.Http(404, "FESTIVAL_NOT_FOUND", null).toFestivalLiveBoardError() is FestivalLiveBoardError.FestivalNotFound)
        assertTrue(NetworkError.Http(401, null, null).toFestivalLiveBoardError() is FestivalLiveBoardError.Unavailable)
        assertTrue(NetworkError.Offline(IOException()).toFestivalLiveBoardError() is FestivalLiveBoardError.Unavailable)
        assertTrue(NetworkError.Decoding(IllegalStateException()).toFestivalLiveBoardError() is FestivalLiveBoardError.InvalidResponse)
        val cancel = CancellationException()
        assertSame(cancel, cancel.toFestivalLiveBoardError())
    }

    @Test
    fun `내 일정 오류는 401 · 세션 없음이면 Unauthorized, 400 · 잘못된 ID면 Rejected, 나머지는 Unavailable이다`() {
        assertTrue(NetworkError.Http(401, null, null).toFestivalScheduleError() is FestivalScheduleError.Unauthorized)
        assertTrue(AuthSessionError.NotAuthenticated().toFestivalScheduleError() is FestivalScheduleError.Unauthorized)
        assertTrue(NetworkError.Http(400, "VALIDATION", null).toFestivalScheduleError() is FestivalScheduleError.Rejected)
        assertTrue(FestivalLiveBoardServiceError.InvalidFestivalId().toFestivalScheduleError() is FestivalScheduleError.Rejected)
        assertTrue(NetworkError.Http(429, null, null).toFestivalScheduleError() is FestivalScheduleError.Unavailable)
        assertTrue(NetworkError.Offline(IOException()).toFestivalScheduleError() is FestivalScheduleError.Unavailable)
    }
}
