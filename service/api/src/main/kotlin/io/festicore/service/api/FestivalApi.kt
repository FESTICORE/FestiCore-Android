package io.festicore.service.api

import io.festicore.core.network.NetworkError
import io.festicore.core.network.festiCoreRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** 축제 API 요청. 인증 없음(Bearer 표시 없음). 실패는 [NetworkError]. iOS `FestivalListRequest`. */
class FestivalApi(private val client: HttpClient) {
    /** `GET api/v1/festivals?after=&limit=`. [after]가 null이면 첫 쪽. 커서는 받은 그대로 보낸다(URL 인코딩은 Ktor). */
    suspend fun list(after: String?, limit: Int): FestivalListDto = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApiPath.FESTIVALS)
        after?.let { parameter("after", it) }
        parameter("limit", limit)
    }
}
