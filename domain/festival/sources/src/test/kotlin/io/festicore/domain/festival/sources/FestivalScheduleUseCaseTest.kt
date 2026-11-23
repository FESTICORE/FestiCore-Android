package io.festicore.domain.festival.sources

import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.domain.festival.FestivalBoardSelection
import io.festicore.domain.festival.FestivalSchedule
import io.festicore.domain.festival.FestivalScheduleError
import io.festicore.domain.festival.FestivalScheduleRepository
import io.festicore.domain.festival.FestivalScheduleSaved
import io.festicore.service.apiendpoint.festival.FestivalRequest
import io.festicore.service.festivalcatalog.NetworkFestivalScheduleService
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 내 일정 저장 UseCase → Repository → 서비스 → API를 Ktor `MockEngine`으로 잇는다(설계 PR D2 검증 행).
 * iOS `FestivalScheduleUseCaseTests` · `FestivalScheduleTests`. 로그인 응답은 stage에서 보지 못해 iOS DTO 형태의 가짜 값이다.
 */
class FestivalScheduleUseCaseTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var put: () -> Pair<HttpStatusCode, String> = { ok("""{"setIds":["lime","noise"]}""") }
    private var get: () -> Pair<HttpStatusCode, String> = { ok("""{"setIds":["encore"],"updatedAt":null}""") }
    private val events = FestivalScheduleSavedEvents()

    private val useCase =
        FestivalScheduleUseCaseImpl(
            repository = FestivalScheduleRepositoryImpl(
                NetworkFestivalScheduleService(
                    FestivalRequest(
                        createFestiCoreHttpClient(
                            baseUrl = "https://example.test/",
                            engine = MockEngine { request ->
                                requests += request
                                val (status, body) = if (request.method == HttpMethod.Put) put() else get()
                                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                        ),
                    ),
                ),
            ),
            events = events,
        )

    private fun ok(data: String) = HttpStatusCode.OK to """{"code":"SUCCESS","message":"OK","data":$data}"""

    private fun error(status: HttpStatusCode, code: String) = status to """{"code":"$code","data":null,"message":"x"}"""

    private suspend fun saveFailure(festivalId: String = "fst"): Throwable? = try {
        useCase.saveMySchedule(festivalId, listOf("lime"), Instant.parse("2026-08-07T01:00:00Z"))
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun saveMySchedulePassesSetIdsAndClientAt() = runTest {
        val clientAt = Instant.ofEpochMilli(1_786_028_400_001)

        val schedule = useCase.saveMySchedule("fst", listOf("lime", "noise"), clientAt)

        assertEquals(listOf("lime", "noise"), schedule.setIds)
        assertEquals(
            """{"setIds":["lime","noise"],"clientAt":"2026-08-07T00:00:00.001+09:00"}""",
            (requests.single().body as TextContent).text,
        )
    }

    @Test
    fun `409 STATUS_STALE이면 서버 일정을 다시 읽어 Stale로 돌려준다`() = runTest {
        put = { error(HttpStatusCode.Conflict, "STATUS_STALE") }

        val stale = saveFailure() as FestivalScheduleError.Stale

        assertEquals(listOf("encore"), stale.schedule.setIds)
        assertEquals(listOf("PUT", "GET"), requests.map { it.method.value })
    }

    @Test
    fun `저장 오류는 iOS와 같게 나눈다`() = runTest {
        put = { error(HttpStatusCode.BadRequest, "VALIDATION") }
        assertTrue(saveFailure() is FestivalScheduleError.Rejected)
        put = { error(HttpStatusCode.Unauthorized, "UNAUTHORIZED") }
        assertTrue(saveFailure() is FestivalScheduleError.Unauthorized)
        put = { error(HttpStatusCode.TooManyRequests, "RATE_LIMITED") }
        assertTrue(saveFailure() is FestivalScheduleError.Unavailable)
        put = { error(HttpStatusCode.ServiceUnavailable, "UNAVAILABLE") }
        assertTrue(saveFailure() is FestivalScheduleError.Unavailable)
        put = { HttpStatusCode.OK to """{"code":"UNAVAILABLE","message":"No schedule","data":{"setIds":["lime"]}}""" }
        assertTrue(saveFailure() is FestivalScheduleError.Unavailable)
        put = { throw IOException("끊김") }
        assertTrue(saveFailure() is FestivalScheduleError.Unavailable)
        // 충돌 뒤 다시 읽기가 401이면 그 오류를 따른다.
        put = { error(HttpStatusCode.Conflict, "STATUS_STALE") }
        get = { error(HttpStatusCode.Unauthorized, "UNAUTHORIZED") }
        assertTrue(saveFailure() is FestivalScheduleError.Unauthorized)
    }

    @Test
    fun `잘못된 축제 ID는 요청하지 않고 Rejected`() = runTest {
        assertTrue(saveFailure("../admin") is FestivalScheduleError.Rejected)
        assertTrue(saveFailure("") is FestivalScheduleError.Rejected)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `읽기 비성공 code는 Unavailable`() = runTest {
        get = { HttpStatusCode.OK to """{"code":"UNAVAILABLE","message":"No schedule","data":{"setIds":["set_1"]}}""" }

        val error = try {
            useCase.mySchedule("fst")
            null
        } catch (e: FestivalScheduleError) {
            e
        }

        assertTrue(error is FestivalScheduleError.Unavailable)
    }

    @Test
    fun `저장 알림은 구독 중인 쪽에 그대로 전달하고 구독 전 알림은 남기지 않는다`() = runTest {
        useCase.notifyMyScheduleSaved(FestivalScheduleSaved("fst", setOf("before")))
        val received = async { useCase.savedSchedules.first() }
        runCurrent()

        // UseCase는 factory라 화면마다 따로 만들어도 같은 통로로 보낸다.
        FestivalScheduleUseCaseImpl(UnusedRepository, events).notifyMyScheduleSaved(FestivalScheduleSaved("fst", setOf("lime")))

        assertEquals(FestivalScheduleSaved("fst", setOf("lime")), received.await())
    }

    @Test
    fun `보드 선택 공유 상태는 마지막 값을 두고 비우면 null이다`() {
        val store = FestivalBoardSelectionStore()
        val selection =
            FestivalBoardSelection(
                festivalId = "fst",
                document = null,
                isOnlineDetailLoading = true,
                isOnlineDetailFailed = false,
                isPackWatchFailed = false,
                connection = FestivalBoardSelection.Connection.Loading,
                packCheckedAt = null,
            )

        store.update(selection)
        assertEquals(selection, store.selection.value)
        store.clear()
        assertNull(store.selection.value)
    }
}

private object UnusedRepository : FestivalScheduleRepository {
    override suspend fun mySchedule(festivalId: String): FestivalSchedule = error("쓰지 않는다")

    override suspend fun replaceMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule = error("쓰지 않는다")
}
