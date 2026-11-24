package io.festicore.service.config

/** Firebase 앱 설정 값. app이 flavor `BuildConfig`(local.properties · CI 비밀, 없으면 빈 값)에서 넘긴다. */
data class FirebaseValues(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val senderId: String,
) {
    /** 네 값이 모두 있어야 Firebase를 초기화한다. 공백만 있는 값은 없는 값으로 본다. */
    val isComplete: Boolean
        get() = listOf(apiKey, appId, projectId, senderId).none { it.isBlank() }
}
