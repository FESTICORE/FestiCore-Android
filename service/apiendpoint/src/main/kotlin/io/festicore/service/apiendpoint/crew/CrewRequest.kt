package io.festicore.service.apiendpoint.crew

import io.festicore.core.network.NetworkError
import io.festicore.core.network.festiCoreRequest
import io.festicore.service.api.CrewDto
import io.festicore.service.api.CrewGatherDto
import io.festicore.service.api.CrewGatherReplyDto
import io.festicore.service.api.CrewInviteCodeDto
import io.festicore.service.api.CrewInviteDto
import io.festicore.service.api.CrewStatusDto
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.bearerAuthorization
import io.festicore.service.api.crew.CrewApi
import io.festicore.service.apiendpoint.common.idempotencyKey
import io.festicore.service.apiendpoint.common.newIdempotencyKey
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement

/**
 * 크루 요청. 초대 미리보기만 인증 없이 보내고, 나머지는 [expectedSessionId] 세션의 Bearer를 붙인다. 모두 재시도하지 않는다.
 * 실패는 [NetworkError], Bearer 요청은 [AuthSessionError]도 던진다. iOS `CrewRequest` · `CrewGatherRequest` · `CrewGatherResponseRequest`.
 */
class CrewRequest(private val client: HttpClient) {
    /** `POST api/v1/crews` `{festivalId, name}`. */
    suspend fun create(body: CrewCreateBody, expectedSessionId: String? = null): CrewDto =
        send(HttpMethod.Post, CrewApi.CREWS, expectedSessionId) { jsonBody(body) }

    /** `GET api/v1/crews/{id}`. 크루원만 볼 수 있다. */
    suspend fun detail(crewId: String, expectedSessionId: String? = null): CrewDto =
        send(HttpMethod.Get, CrewApi.crew(crewId), expectedSessionId)

    /** `PATCH api/v1/crews/{id}` `{name}`. 크루장만. */
    suspend fun rename(
        crewId: String,
        body: CrewRenameBody,
        expectedSessionId: String? = null,
    ): CrewDto = send(HttpMethod.Patch, CrewApi.crew(crewId), expectedSessionId) { jsonBody(body) }

    /** `DELETE api/v1/crews/{id}`. 크루장만. */
    suspend fun dissolve(crewId: String, expectedSessionId: String? = null) {
        send<JsonElement?>(HttpMethod.Delete, CrewApi.crew(crewId), expectedSessionId)
    }

    /** `POST api/v1/crews/{id}/status` `{zoneId?, state, clientAt}` + `Idempotency-Key`. */
    suspend fun status(
        crewId: String,
        body: CrewStatusBody,
        expectedSessionId: String? = null,
        idempotencyKey: String = newIdempotencyKey(),
    ): CrewStatusDto = send(HttpMethod.Post, CrewApi.status(crewId), expectedSessionId) {
        jsonBody(body)
        idempotencyKey(idempotencyKey)
    }

    /** `POST api/v1/crews/{id}/invite/rotate`. 크루장만, 이전 코드는 바로 무효가 된다. */
    suspend fun rotateInvite(crewId: String, expectedSessionId: String? = null): CrewInviteCodeDto =
        send(HttpMethod.Post, CrewApi.rotateInvite(crewId), expectedSessionId)

    /** `POST api/v1/crews/{id}/transfer` `{toUserId}`. */
    suspend fun transfer(
        crewId: String,
        body: CrewTransferBody,
        expectedSessionId: String? = null,
    ): CrewDto = send(HttpMethod.Post, CrewApi.transfer(crewId), expectedSessionId) { jsonBody(body) }

    /** `DELETE api/v1/crews/{id}/members/{userId}`. 내보내기와 나가기(내 ID)가 같은 요청이다. */
    suspend fun removeMember(
        crewId: String,
        userId: String,
        expectedSessionId: String? = null,
    ) {
        send<JsonElement?>(HttpMethod.Delete, CrewApi.member(crewId, userId), expectedSessionId)
    }

    /** `GET api/v1/crews/invite/{code}`. 인증 없음. */
    suspend fun invite(code: String): CrewInviteDto = client.festiCoreRequest {
        method = HttpMethod.Get
        url(CrewApi.invite(code))
    }

    /** `POST api/v1/crews/join` `{inviteCode}`. */
    suspend fun join(body: CrewJoinBody, expectedSessionId: String? = null): CrewDto =
        send(HttpMethod.Post, CrewApi.JOIN, expectedSessionId) { jsonBody(body) }

    /** `POST api/v1/crews/{id}/gather` `{zoneId}`. 중복 알림을 막기 위해 재시도하지 않는다. */
    suspend fun gather(
        crewId: String,
        body: CrewGatherBody,
        expectedSessionId: String? = null,
    ): CrewGatherDto = send(HttpMethod.Post, CrewApi.gather(crewId), expectedSessionId) { jsonBody(body) }

    /** `POST api/v1/crews/{id}/gather/{gatherId}/response` `{response}`. */
    suspend fun respondToGather(
        crewId: String,
        gatherId: String,
        body: CrewGatherResponseBody,
        expectedSessionId: String? = null,
    ): CrewGatherReplyDto = send(HttpMethod.Post, CrewApi.gatherResponse(crewId, gatherId), expectedSessionId) { jsonBody(body) }

    // Bearer 요청 공통. 인증 플러그인이 던진 AuthSessionError는 festiCoreRequest가 Unknown으로 감싸므로 풀어서 던진다(FlagApi와 같음).
    private suspend inline fun <reified T> send(
        httpMethod: HttpMethod,
        path: String,
        expectedSessionId: String?,
        crossinline extra: HttpRequestBuilder.() -> Unit = {},
    ): T = try {
        client.festiCoreRequest {
            method = httpMethod
            url(path)
            bearerAuthorization(expectedSessionId)
            extra()
        }
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }

    private inline fun <reified B : Any> HttpRequestBuilder.jsonBody(body: B) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
