package io.festicore.domain.festival.liveboard
import io.festicore.domain.festival.entity.FestivalLiveBoardError
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.entity.FestivalPackDocument
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 라이브 보드 화면이 쓰는 조회. 실패는 [FestivalLiveBoardError]. iOS `FestivalLiveBoardUseCaseInterface` + 상세 조회.
 * 폴링 간격 · 서버 시각 보정 규칙은 [Companion]에 둔다(iOS `FestivalLiveBoardFeature` · `FestivalLiveBoardPresentation`).
 */
interface FestivalLiveBoardUseCase {
    /** 축제의 현재 공개 깃발 상태 · 내 팔로우 · 크루를 다시 조회한다. */
    suspend fun liveBoard(festivalId: String): FestivalLiveBoardSnapshot

    /** 마지막으로 받은 라이브 상태(현재 세션 것만). 존 지도 첫 화면을 채운다(설계 E3). */
    suspend fun cachedLiveBoard(festivalId: String): FestivalLiveBoardSnapshot?

    /** 축제 공식 인스타그램 최근 게시물. */
    suspend fun officialNews(festivalId: String): FestivalOfficialNews

    /** 축제 상세(온라인 팩 문서). 팩이 없는 축제를 그릴 때 쓴다. */
    suspend fun festivalDetail(festivalId: String): FestivalPackDocument

    companion object {
        /** 라이브 상태 조회 간격. */
        val POLL_INTERVAL: Duration = 35.seconds

        /** 공식 소식 조회 간격. */
        val OFFICIAL_NEWS_INTERVAL: Duration = 300.seconds

        /** 화면 시각을 다시 계산하는 간격. */
        val CLOCK_REFRESH_INTERVAL: Duration = 30.seconds

        /** 이번 조회 결과([failure]가 null이면 성공) 뒤 다음 조회까지 기다릴 시간. 429면 `max(35초, retryAfter)`. */
        fun nextPollDelay(failure: Throwable?): Duration =
            (failure as? FestivalLiveBoardError.RateLimited)?.let { maxOf(POLL_INTERVAL, it.retryAfter) } ?: POLL_INTERVAL

        /**
         * 새 스냅샷을 받았을 때의 시각 기준점. `serverAt`이 바뀌었거나 기준점이 없으면 [deviceNow],
         * 같은 `serverAt`(304 재사용 등)이면 기존 기준점을 유지한다.
         */
        fun clockAnchor(
            previousServerAt: Instant?,
            currentAnchor: Instant?,
            loadedServerAt: Instant?,
            deviceNow: Instant,
        ): Instant = if (previousServerAt != loadedServerAt || currentAnchor == null) deviceNow else currentAnchor

        /** 서버 시각으로 보정한 지금 = `serverAt + (deviceNow − clockAnchorAt)`. 둘 중 하나라도 없으면 [deviceNow]. */
        fun boardNow(
            serverAt: Instant?,
            clockAnchorAt: Instant?,
            deviceNow: Instant,
        ): Instant {
            if (serverAt == null || clockAnchorAt == null) return deviceNow
            return serverAt.plus(java.time.Duration.between(clockAnchorAt, deviceNow))
        }
    }
}
