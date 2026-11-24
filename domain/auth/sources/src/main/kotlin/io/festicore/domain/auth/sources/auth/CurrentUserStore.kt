package io.festicore.domain.auth.sources.auth

import io.festicore.domain.auth.auth.CurrentUserProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 메모리에만 두는 [CurrentUserProvider]. 앱을 다시 시작하면 진입 프로필 재확인이 다시 채운다. 쓰기는 app이 연결한다. */
class CurrentUserStore : CurrentUserProvider {
    private val userId = MutableStateFlow<String?>(null)

    override val currentUserId: StateFlow<String?> = userId.asStateFlow()

    fun update(userId: String?) {
        this.userId.value = userId
    }
}
