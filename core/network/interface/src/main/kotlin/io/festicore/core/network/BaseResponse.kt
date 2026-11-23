package io.festicore.core.network

import kotlinx.serialization.Serializable

/** 서버 공통 응답 래퍼. 성공은 `code == "SUCCESS"`. iOS `BaseResponseDTO`. */
@Serializable
data class BaseResponse<T>(
    val code: String,
    val message: String,
    val data: T? = null,
)

/** 2xx가 아닌 응답의 본문. 필드는 없을 수 있다. iOS `ErrorBody`. */
@Serializable
data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
)
