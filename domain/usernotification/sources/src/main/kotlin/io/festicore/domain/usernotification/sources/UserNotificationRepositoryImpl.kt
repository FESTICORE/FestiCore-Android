package io.festicore.domain.usernotification.sources

import io.festicore.domain.usernotification.UserNotificationError
import io.festicore.domain.usernotification.UserNotificationPage
import io.festicore.domain.usernotification.UserNotificationRepository
import io.festicore.service.api.UserNotificationApi
import io.festicore.service.api.UserNotificationIdsBody

/**
 * 내 알림 저장소(iOS `UserNotificationRepositoryImpl`). 요청마다 현재 세션을 확인하고 그 세션에 묶어 보낸다.
 * 세션이 없으면 보내지 않고 [UserNotificationError.Unauthenticated]. 로컬 저장은 없다.
 */
internal class UserNotificationRepositoryImpl(
    private val api: UserNotificationApi,
    private val currentSessionId: suspend () -> String?,
) : UserNotificationRepository {
    override suspend fun notifications(before: String?): UserNotificationPage = mapErrors {
        api.list(before, requireSession()).toDomain()
    }

    override suspend fun unreadCount(): Int = mapErrors {
        val count = api.unreadCount(requireSession()).unreadCount
        if (count < 0) throw UserNotificationError.InvalidResponse()
        count
    }

    override suspend fun markRead(ids: List<String>) = sendInBatches(ids) { body, session -> api.read(body, session) }

    override suspend fun delete(ids: List<String>) = sendInBatches(ids) { body, session -> api.delete(body, session) }

    private suspend fun sendInBatches(ids: List<String>, send: suspend (UserNotificationIdsBody, String) -> Unit) {
        if (ids.isEmpty()) return
        mapErrors {
            val session = requireSession()
            ids.chunked(MAX_IDS_PER_REQUEST).forEach { send(UserNotificationIdsBody(it), session) }
        }
    }

    private suspend fun requireSession(): String = currentSessionId() ?: throw UserNotificationError.Unauthenticated()

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw e.toUserNotificationError()
    }

    companion object {
        /** 서버가 한 번에 받는 ID 수. iOS `chunked(maxCount: 100)`. */
        const val MAX_IDS_PER_REQUEST = 100
    }
}
