package io.festicore.service.festivalcatalog

import io.festicore.core.network.NetworkError
import io.festicore.service.api.FestivalFlagProfileDto
import io.festicore.service.api.FestivalLiveBoardDto
import io.festicore.service.apiendpoint.festival.FestivalLiveResponse
import io.festicore.service.apiendpoint.festival.FestivalRequest
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 라이브 상태 한 번 조회한 결과. [receivedAt]은 라이브 응답을 받은 시각(깃발 프로필 요청 전). */
data class FestivalLiveBoardFetch(
    val board: FestivalLiveBoardDto,
    val flags: List<FestivalFlagProfileDto>,
    val receivedAt: Instant,
)

/**
 * 라이브 상태 조회 + 공개 깃발 프로필 조회. iOS `NetworkFestivalLiveBoardService`.
 * - `X-Device-Id`는 [deviceId]. 로그인 세션이 있을 때만 Bearer와 세션별 ETag(`If-None-Match`)를 보내고 304면 저장한 응답을 다시 쓴다.
 * - 세션이 바뀌면 ETag 캐시 · 429 대기를 비운다. 응답 뒤 세션이 바뀌었으면 [FestivalLiveBoardServiceError.InvalidResponse].
 * - 429면 `Retry-After`(없거나 숫자가 아니면 35초, 최소 1초) 동안 요청하지 않고 [FestivalLiveBoardServiceError.RateLimited].
 * - 본문 `FESTIVAL_NOT_FOUND`면 그 축제 캐시를 지우고 [FestivalLiveBoardServiceError.FestivalNotFound].
 * - 마지막으로 성공한 조회 결과를 세션별로 들고 있다가 [cachedFetch]로 돌려준다(존 지도 첫 화면, 설계 E3).
 * 그 밖의 실패는 [NetworkError](401 포함, 토큰 갱신은 인증 플러그인).
 */
class FestivalLiveBoardService(
    private val api: FestivalRequest,
    private val deviceId: suspend () -> String,
    private val currentSessionId: suspend () -> String?,
    private val now: () -> Instant = Instant::now,
) {
    private class CachedBoard(
        val etag: String?,
        val board: FestivalLiveBoardDto,
    )

    private val lock = Mutex()
    private val cachedBoards = mutableMapOf<String, CachedBoard>()
    private val retryNotBefore = mutableMapOf<String, Instant>()

    /** 세션([cachedSessionId])의 마지막 성공 결과. 비로그인 조회도 남긴다(ETag 캐시와 달리 재요청에 쓰지 않는다). */
    private val lastFetches = mutableMapOf<String, FestivalLiveBoardFetch>()
    private var cachedSessionId: String? = null

    suspend fun refresh(festivalId: String): FestivalLiveBoardFetch {
        if (!festivalId.isValidFestivalId()) throw FestivalLiveBoardServiceError.InvalidFestivalId()
        val device = deviceId()
        val sessionId = currentSessionId()
        val etag = lock.withLock {
            if (cachedSessionId != sessionId) {
                cachedBoards.clear()
                retryNotBefore.clear()
                lastFetches.clear()
                cachedSessionId = sessionId
            }
            retryNotBefore[festivalId]?.let { retryAt ->
                val remaining = (retryAt.toEpochMilli() - now().toEpochMilli()).milliseconds
                if (remaining.isPositive()) throw FestivalLiveBoardServiceError.RateLimited(remaining)
            }
            if (sessionId == null) null else cachedBoards[festivalId]?.etag
        }

        val response = api.live(festivalId, device, etag, expectedSessionId = sessionId)
        val board = lock.withLock { response.toBoard(festivalId, sessionId) }
        // 라이브 상태를 확인한 시각이다. 깃발 프로필 요청이 오래 걸려도 그 뒤로 미루지 않는다.
        val receivedAt = now()

        val flags = try {
            api.flags(festivalId).items
        } catch (_: NetworkError.Decoding) {
            throw FestivalLiveBoardServiceError.InvalidResponse()
        } catch (_: NetworkError.InvalidResponse) {
            throw FestivalLiveBoardServiceError.InvalidResponse()
        }
        val latestSessionId = currentSessionId()
        return lock.withLock {
            if (cachedSessionId != sessionId || latestSessionId != sessionId) throw FestivalLiveBoardServiceError.InvalidResponse()
            if (sessionId != null) {
                val newEtag = when (response) {
                    is FestivalLiveResponse.Loaded -> response.etag
                    else -> cachedBoards[festivalId]?.etag
                }
                cachedBoards[festivalId] = CachedBoard(newEtag, board)
            }
            retryNotBefore.remove(festivalId)
            FestivalLiveBoardFetch(board, flags, receivedAt).also { lastFetches[festivalId] = it }
        }
    }

    /**
     * 이 축제를 마지막으로 [refresh]해 받은 결과(라이브 DTO · 깃발 프로필 · 수신 시각, [refresh]가 돌려준 값 그대로). 네트워크를 쓰지 않는다.
     * 받은 적이 없거나, 현재 세션이 그 결과를 받은 세션과 다르거나, 그 뒤 미공개 응답을 받았으면 null.
     */
    suspend fun cachedFetch(festivalId: String): FestivalLiveBoardFetch? {
        val sessionId = currentSessionId()
        return lock.withLock { if (cachedSessionId != sessionId) null else lastFetches[festivalId] }
    }

    /** [lock] 안에서 부른다. */
    private fun FestivalLiveResponse.toBoard(festivalId: String, sessionId: String?): FestivalLiveBoardDto = when (this) {
        is FestivalLiveResponse.FestivalNotFound -> {
            cachedBoards.remove(festivalId)
            lastFetches.remove(festivalId)
            throw FestivalLiveBoardServiceError.FestivalNotFound()
        }

        is FestivalLiveResponse.Loaded -> {
            if (board.festivalId != festivalId) throw FestivalLiveBoardServiceError.InvalidResponse()
            board
        }

        is FestivalLiveResponse.NotModified -> {
            val cached = cachedBoards[festivalId]
            if (sessionId == null || cached == null) throw FestivalLiveBoardServiceError.InvalidResponse()
            cached.board
        }

        is FestivalLiveResponse.RateLimited -> {
            val delay = retryAfter(retryAfter)
            retryNotBefore[festivalId] = now().plus(delay.toJavaDuration())
            throw FestivalLiveBoardServiceError.RateLimited(delay)
        }
    }

    companion object {
        /** `Retry-After`가 없거나 숫자가 아닐 때 기다리는 시간. iOS 35초. */
        val DEFAULT_RETRY_AFTER: Duration = 35.seconds

        /** `Retry-After` 초 값. 없거나 숫자가 아니면 [DEFAULT_RETRY_AFTER], 1초보다 작으면 1초. */
        fun retryAfter(value: String?): Duration {
            val seconds = value?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return DEFAULT_RETRY_AFTER
            return maxOf(seconds, 1.0).seconds
        }
    }
}
