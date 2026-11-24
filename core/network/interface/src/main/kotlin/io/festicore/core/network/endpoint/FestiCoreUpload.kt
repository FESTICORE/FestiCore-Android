package io.festicore.core.network.endpoint

import io.festicore.core.network.error.NetworkError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

/** multipart 파일 파트 하나(iOS `MultipartFormDataPart` 메모리 파트). */
class FestiCoreUploadPart(
    val name: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

/** 업로드 요청 전체 제한 시간. 셀룰러에서 5MB를 올릴 수 있게 iOS와 같은 60초. */
const val UPLOAD_TIMEOUT_MILLIS: Long = 60_000

/**
 * [path]에 [part] 하나를 multipart로 `POST`하고 [festiCoreRequest]와 같이 `data`를 돌려준다. 실패도 같은 [NetworkError].
 * [onProgress]는 보낸 비율(0~1)을 알린다. 전체 길이를 모르면 알리지 않는다.
 * 제한 시간은 요청 단위 [timeoutMillis]라 클라이언트에 `HttpTimeout`이 설치돼 있어야 한다. 인증 등은 [block]으로 붙인다.
 */
suspend inline fun <reified T> HttpClient.festiCoreUpload(
    path: String,
    part: FestiCoreUploadPart,
    noinline onProgress: (Double) -> Unit,
    timeoutMillis: Long = UPLOAD_TIMEOUT_MILLIS,
    noinline block: HttpRequestBuilder.() -> Unit = {},
): T = festiCoreRequest {
    method = HttpMethod.Post
    url(path)
    setBody(MultiPartFormDataContent(formData { append(part.name, part.bytes, part.headers()) }))
    onUpload { sent, total -> if (total != null && total > 0) onProgress(sent.toDouble() / total) }
    timeout { requestTimeoutMillis = timeoutMillis }
    block()
}

@PublishedApi
internal fun FestiCoreUploadPart.headers(): Headers = Headers.build {
    append(HttpHeaders.ContentType, contentType)
    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
}
