package io.festicore.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException

/**
 * 요청을 보내고 [BaseResponse]의 `data`를 돌려준다. 실패는 [NetworkError]로 던진다.
 * - 2xx + `code == "SUCCESS"` → `data`(T가 null을 허용하지 않는데 `data`가 없으면 [NetworkError.Decoding])
 * - 2xx + 다른 `code` → [NetworkError.InvalidResponse]
 * - 2xx가 아님 → 본문을 [ErrorBody]로 읽어 [NetworkError.Http]
 */
suspend inline fun <reified T> HttpClient.festiCoreRequest(noinline block: HttpRequestBuilder.() -> Unit): T = mapNetworkErrors {
    val response = request(block)
    if (!response.status.isSuccess()) throw response.toHttpError()
    val body = response.body<BaseResponse<T>>()
    if (body.code != SUCCESS_CODE) throw NetworkError.InvalidResponse(body.code, body.message)
    val data = body.data
    if (data == null && null !is T) throw NetworkError.Decoding(SerializationException("data가 없습니다"))
    data as T
}

/**
 * 상태 코드 · 헤더를 직접 봐야 하는 요청(ETag · 304 · 429 등). 2xx가 아닌 응답도 [handle]에 넘긴다.
 * 전송 · 해석 실패는 [festiCoreRequest]와 같은 [NetworkError]로 바꾼다.
 */
suspend inline fun <T> HttpClient.festiCoreExchange(
    noinline block: HttpRequestBuilder.() -> Unit,
    crossinline handle: suspend (HttpResponse) -> T,
): T = mapNetworkErrors { handle(request(block)) }

@PublishedApi
internal const val SUCCESS_CODE = "SUCCESS"

@PublishedApi
internal suspend fun HttpResponse.toHttpError(): NetworkError.Http {
    val error = try {
        body<ErrorBody>()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    return NetworkError.Http(status.value, error?.code, error?.message)
}

@PublishedApi
internal suspend fun <T> mapNetworkErrors(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: NetworkError) {
    throw e
} catch (e: HttpRequestTimeoutException) {
    throw NetworkError.Timeout(e)
} catch (e: ConnectTimeoutException) {
    throw NetworkError.Timeout(e)
} catch (e: SocketTimeoutException) {
    throw NetworkError.Timeout(e)
} catch (e: java.net.SocketTimeoutException) {
    throw NetworkError.Timeout(e)
} catch (e: ContentConvertException) {
    throw NetworkError.Decoding(e)
} catch (e: NoTransformationFoundException) {
    throw NetworkError.Decoding(e)
} catch (e: SerializationException) {
    throw NetworkError.Decoding(e)
} catch (e: IOException) {
    throw NetworkError.Offline(e)
} catch (e: Exception) {
    throw NetworkError.Unknown(e)
}
