package io.festicore.domain.notice.sources

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.core.network.response.BaseResponse
import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.entity.NoticeError
import io.festicore.domain.notice.entity.NoticeSummary
import io.festicore.domain.notice.notice.NoticeRepository
import io.festicore.domain.notice.notice.NoticeUseCase
import io.festicore.domain.notice.sources.di.domainNoticeModule
import io.festicore.domain.notice.sources.model.dto.NoticeListDto
import io.festicore.domain.notice.sources.notice.NoticeUseCaseImpl
import io.festicore.domain.notice.sources.repository.NoticeRepositoryImpl
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.apiendpoint.di.serviceApiEndpointModule
import io.festicore.service.apiendpoint.notice.NoticeRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** iOS `NoticeDomainTests` 4건(같은 이름). 응답 본문은 백엔드 Swagger 예시 형태의 가짜 값이다. */
class NoticeDomainTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(json: String) = createFestiCoreHttpClient(
        baseUrl = "https://festicore.test/",
        engine = MockEngine { request ->
            requests += request
            respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private fun repository(json: String) = NoticeRepositoryImpl(NoticeRequest(client(json)))

    private fun assertPublicGet(url: String) {
        val sent = requests.single()
        assertEquals(url, sent.url.toString())
        assertEquals(HttpMethod.Get, sent.method)
        assertNull(sent.attributes.getOrNull(BearerAuthorizationKey))
        assertTrue(sent.body is OutgoingContent.NoContent)
    }

    @Test
    fun listMapsPublishedItemsAndSendsPublicGet() = runTest {
        val notices = repository(
            """{"code":"SUCCESS","message":"OK","data":{"items":[
            {"id":"2c75c8de-517d-4611-9c51-b4ff804dd597",
             "title":"서비스 안내","publishedAt":"2026-11-04T00:00:00.000Z"}
            ]}}""",
        ).notices()

        assertPublicGet("https://festicore.test/api/v1/notices")
        assertEquals(
            listOf(NoticeSummary("2c75c8de-517d-4611-9c51-b4ff804dd597", "서비스 안내", Instant.parse("2026-11-04T00:00:00Z"))),
            notices,
        )
    }

    @Test
    fun detailMapsContentAndSendsPublicGet() = runTest {
        val id = "2c75c8de-517d-4611-9c51-b4ff804dd597"
        val detail = repository(
            """{"code":"SUCCESS","message":"OK","data":{
            "id":"2c75c8de-517d-4611-9c51-b4ff804dd597",
            "title":"서비스 안내","content":"공지 본문",
            "publishedAt":"2026-11-04T00:00:00.000Z"
            }}""",
        ).notice(id)

        assertPublicGet("https://festicore.test/api/v1/notices/$id")
        assertEquals(NoticeDetail(id, "서비스 안내", "공지 본문", Instant.parse("2026-11-04T00:00:00Z")), detail)
    }

    @Test
    fun invalidEnvelopeAndNullableFieldsDoNotProduceDomainContent() = runTest {
        assertInvalid { repository("""{"code":"ERROR","message":"failure","data":{"items":[]}}""").notices() }

        try {
            FestiCoreJson.decodeFromString(
                BaseResponse.serializer(NoticeListDto.serializer()),
                """{"code":"SUCCESS","message":"OK","data":{"items":[
                {"id":"x","title":null,"publishedAt":"2026-11-04T00:00:00.000Z"}
                ]}}""",
            )
            fail("제목이 없으면 해석에 실패해야 한다")
        } catch (_: SerializationException) {
        }

        assertInvalid {
            repository(
                """{"code":"SUCCESS","message":"OK","data":{"items":[
                {"id":"x","title":"공지","publishedAt":"invalid"}
                ]}}""",
            ).notices()
        }
    }

    @Test
    fun useCaseUsesRepositoryAndLiveDependenciesResolve() = runTest {
        val expected = NoticeSummary("notice-id", "공지", Instant.EPOCH)
        val repository = object : NoticeRepository {
            override suspend fun notices() = listOf(expected)

            override suspend fun notice(id: String): NoticeDetail = throw NoticeError.Unavailable()
        }
        assertEquals(listOf(expected), NoticeUseCaseImpl(repository).notices())

        val koin = koinApplication {
            modules(
                module { single<HttpClient> { client("{}") } },
                serviceApiEndpointModule,
                domainNoticeModule,
            )
        }.koin
        assertTrue(koin.get<NoticeRepository>() is NoticeRepositoryImpl)
        assertTrue(koin.get<NoticeUseCase>() is NoticeUseCaseImpl)
    }

    private suspend fun assertInvalid(block: suspend () -> Unit) {
        try {
            block()
            fail("InvalidResponse를 기대했다")
        } catch (_: NoticeError.InvalidResponse) {
        }
    }
}
