package io.festicore.service.apiendpoint.notice

import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.core.network.error.NetworkError
import io.festicore.service.api.notice.NoticeApi
import io.ktor.client.HttpClient
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/**
 * 공지 요청. 모두 인증 없는 GET이고 재시도하지 않는다. 실패는 [NetworkError]. iOS `NoticeRequest`.
 */
class NoticeRequest(@PublishedApi internal val client: HttpClient) {
    /** `GET api/v1/notices`. 서버가 최신 100건을 한 번에 준다(쪽 나눔 없음). */
    suspend inline fun <reified T> list(): T = client.festiCoreRequest {
        method = HttpMethod.Get
        url(NoticeApi.NOTICES)
    }

    /** `GET api/v1/notices/{id}`. 없거나 미게시 · UUID가 아닌 ID는 404. */
    suspend inline fun <reified T> detail(id: String): T = client.festiCoreRequest {
        method = HttpMethod.Get
        url(NoticeApi.detail(id))
    }
}
