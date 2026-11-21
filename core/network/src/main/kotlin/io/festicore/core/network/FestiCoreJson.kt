package io.festicore.core.network

import kotlinx.serialization.json.Json

/** 서버 JSON 설정. 이름은 camelCase 그대로, 모르는 키는 무시하고 null 필드는 보내지 않는다. */
val FestiCoreJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
