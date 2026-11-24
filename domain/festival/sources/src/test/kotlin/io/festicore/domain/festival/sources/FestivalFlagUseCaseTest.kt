package io.festicore.domain.festival.sources
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.domain.festival.entity.FestivalFlagFollowError
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.sources.flag.FestivalFlagUseCaseImpl
import io.festicore.domain.festival.sources.repository.FestivalFlagRepositoryImpl
import io.festicore.service.apiendpoint.flag.FlagRequest
import io.festicore.service.festivalcatalog.NetworkFestivalFlagFollowService
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 깃발 팔로우 UseCase → Repository → 서비스 → API를 Ktor `MockEngine`으로 잇는다.
 * iOS `FestivalFlagUseCaseImpl` · `NetworkFestivalFlagFollowService.map`. 응답은 iOS DTO 형태의 가짜 값이다.
 */
class FestivalFlagUseCaseTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var response: Pair<HttpStatusCode, String> =
        HttpStatusCode.OK to """{"code":"SUCCESS","message":"OK","data":{"flagId":"flg_a","following":true,"followerCount":5}}"""
    private var sessionId: String? = "session-1"

    private val useCase = FestivalFlagUseCaseImpl(
        FestivalFlagRepositoryImpl(
            NetworkFestivalFlagFollowService(
                FlagRequest(
                    createFestiCoreHttpClient(
                        baseUrl = "https://example.test/",
                        engine = MockEngine { request ->
                            requests += request
                            respond(response.second, response.first, headersOf(HttpHeaders.ContentType, "application/json"))
                        },
                    ),
                ),
                currentSessionId = { sessionId },
            ),
        ),
    )

    private fun fail(status: HttpStatusCode, code: String) = status to """{"code":"$code","message":"실패"}"""

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `팔로우 결과를 도메인 값으로 돌려준다`() = runTest {
        assertEquals(FestivalFlagFollowResult("flg_a", true, 5), useCase.setFollowing(true, "flg_a"))
    }

    @Test
    fun `서버 오류를 로그인 · 약관 · 없는 깃발 · 한도 · 일시 오류로 나눈다`() = runTest {
        val cases = listOf(
            fail(HttpStatusCode.Unauthorized, "UNAUTHORIZED") to FestivalFlagFollowError.LoginRequired::class,
            fail(HttpStatusCode.Forbidden, "TERMS_REQUIRED") to FestivalFlagFollowError.TermsRequired::class,
            fail(HttpStatusCode.Forbidden, "FORBIDDEN") to FestivalFlagFollowError.Unavailable::class,
            fail(HttpStatusCode.NotFound, "FLAG_NOT_FOUND") to FestivalFlagFollowError.NotFound::class,
            fail(HttpStatusCode.Conflict, "FOLLOW_LIMIT") to FestivalFlagFollowError.LimitReached::class,
            fail(HttpStatusCode.Conflict, "IDEMPOTENCY_CONFLICT") to FestivalFlagFollowError.Unavailable::class,
            fail(HttpStatusCode.TooManyRequests, "RATE_LIMITED") to FestivalFlagFollowError.Unavailable::class,
            (HttpStatusCode.OK to """{"code":"SUCCESS","message":"OK","data":{"flagId":"flg_b","following":true,"followerCount":1}}""") to
                FestivalFlagFollowError.InvalidResponse::class,
        )
        for ((case, expected) in cases) {
            response = case
            assertEquals(expected, failure { useCase.setFollowing(true, "flg_a") }?.let { it::class })
        }
    }

    @Test
    fun `로그아웃 상태와 잘못된 ID는 요청하지 않는다`() = runTest {
        assertTrue(failure { useCase.setFollowing(true, "flg/a") } is FestivalFlagFollowError.InvalidResponse)

        sessionId = null
        assertTrue(failure { useCase.setFollowing(false, "flg_a") } is FestivalFlagFollowError.LoginRequired)
        assertTrue(requests.isEmpty())
    }
}
