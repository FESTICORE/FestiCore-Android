package io.festicore.service.festivalcatalog.endpoint

import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.core.network.error.NetworkError
import io.festicore.service.api.festival.FestivalApi
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** 축제 목록 요청. 실패는 [NetworkError]. 재시도하지 않는다. iOS `FestivalListRequest`. */
class FestivalListRequest(@PublishedApi internal val client: HttpClient) {
    /** `GET api/v1/festivals?after=&limit=`. [after]가 null이면 첫 쪽. 커서는 받은 그대로 보낸다(URL 인코딩은 Ktor). */
    suspend inline fun <reified T> list(after: String?, limit: Int): T = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApi.FESTIVALS)
        after?.let { parameter("after", it) }
        parameter("limit", limit)
    }
}
