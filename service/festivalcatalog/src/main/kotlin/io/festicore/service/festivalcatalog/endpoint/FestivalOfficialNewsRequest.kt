package io.festicore.service.festivalcatalog.endpoint

import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.core.network.error.NetworkError
import io.festicore.service.api.festival.FestivalApi
import io.ktor.client.HttpClient
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** 축제 공식 소식 요청. 실패는 [NetworkError]. 재시도하지 않는다. iOS `FestivalOfficialNewsRequest`. */
class FestivalOfficialNewsRequest(@PublishedApi internal val client: HttpClient) {
    /** `GET api/v1/festivals/{id}/instagram`. 인증 없음. */
    suspend inline fun <reified T> instagram(festivalId: String): T = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApi.instagram(festivalId))
    }
}
