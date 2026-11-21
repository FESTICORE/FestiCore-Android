package io.festicore.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 앱 서버용 [HttpClient]. [baseUrl]은 app이 `BuildConfig.BASE_URL`로 넘긴다.
 * 테스트는 [engine]에 MockEngine을 넣는다. 인증 등 추가 플러그인은 [configure]로 붙인다.
 */
fun createFestiCoreHttpClient(
    baseUrl: String,
    engine: HttpClientEngine = OkHttp.create(),
    json: Json = FestiCoreJson,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(json)
    }
    defaultRequest {
        url(baseUrl)
        headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
    configure()
}
