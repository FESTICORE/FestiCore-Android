package io.festicore.service.api

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import java.util.UUID

const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

/** 새 멱등 키. iOS `UUID().uuidString`과 같은 UUID 문자열이다. */
fun newIdempotencyKey(): String = UUID.randomUUID().toString()

/** `Idempotency-Key` 헤더를 붙인다. 재시도하지 않으므로 요청마다 새 키를 쓴다. */
fun HttpRequestBuilder.idempotencyKey(key: String = newIdempotencyKey()) {
    header(IDEMPOTENCY_KEY_HEADER, key)
}
