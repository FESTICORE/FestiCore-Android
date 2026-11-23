package io.festicore.core.network

/** [festiCoreRequest]가 던지는 오류. 원인 예외는 [cause]에 남긴다. */
sealed class NetworkError(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** 연결 실패 등 입출력 오류 */
    class Offline(cause: Throwable) : NetworkError(cause = cause)

    class Timeout(cause: Throwable) : NetworkError(cause = cause)

    /** 2xx가 아닌 응답. [code] · [message]는 [ErrorBody]에서 읽고, 없으면 null. iOS `ResponseError`. */
    class Http(
        val status: Int,
        val code: String?,
        override val message: String?,
    ) : NetworkError() {
        val isUnauthorized: Boolean get() = status == 401
    }

    /** 2xx지만 `code != "SUCCESS"`인 응답 */
    class InvalidResponse(
        val code: String,
        override val message: String,
    ) : NetworkError()

    class Decoding(cause: Throwable) : NetworkError(cause = cause)

    class Unknown(cause: Throwable) : NetworkError(cause = cause)
}
