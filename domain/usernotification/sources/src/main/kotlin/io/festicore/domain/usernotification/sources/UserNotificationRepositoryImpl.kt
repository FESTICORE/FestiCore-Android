package io.festicore.domain.usernotification.sources

import io.festicore.domain.usernotification.UserNotificationError
import io.festicore.domain.usernotification.UserNotificationPage
import io.festicore.domain.usernotification.UserNotificationRepository
import io.festicore.domain.usernotification.sources.model.dto.UserNotificationCountDto
import io.festicore.domain.usernotification.sources.model.dto.UserNotificationListDto
import io.festicore.domain.usernotification.sources.model.mapper.toDomain
import io.festicore.domain.usernotification.sources.model.mapper.toUserNotificationError
import io.festicore.service.apiendpoint.usernotification.UserNotificationIdsBody
import io.festicore.service.apiendpoint.usernotification.UserNotificationRequest

/**
 * 내 알림 저장소(iOS `UserNotificationRepositoryImpl`). 조회는 현재 세션을 확인하고 그 세션에 묶어 보낸다.
 * 읽음 · 삭제는 넘겨받은 세션(그 알림을 받은 쪽의 세션)에만 묶고 현재 세션을 다시 읽지 않는다.
 * 세션이 없거나 바뀌었으면 보내지 않고 [UserNotificationError.Unauthenticated]. 로컬 저장은 없다.
 */
internal class UserNotificationRepositoryImpl(
    private val api: UserNotificationRequest,
    private val currentSessionId: suspend () -> String?,
) : UserNotificationRepository {
    override suspend fun notifications(before: String?): UserNotificationPage = mapErrors {
        val session = requireSession()
        api.list<UserNotificationListDto>(before, session).toDomain(session)
    }

    override suspend fun unreadCount(): Int = mapErrors {
        val count = api.unreadCount<UserNotificationCountDto>(requireSession()).unreadCount
        if (count < 0) throw UserNotificationError.InvalidResponse()
        count
    }

    override suspend fun markRead(ids: List<String>, sessionId: String) = sendInBatches(ids) { body -> api.read(body, sessionId) }

    override suspend fun delete(ids: List<String>, sessionId: String) = sendInBatches(ids) { body -> api.delete(body, sessionId) }

    private suspend fun sendInBatches(ids: List<String>, send: suspend (UserNotificationIdsBody) -> Unit) {
        if (ids.isEmpty()) return
        mapErrors { ids.chunked(MAX_IDS_PER_REQUEST).forEach { send(UserNotificationIdsBody(it)) } }
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
