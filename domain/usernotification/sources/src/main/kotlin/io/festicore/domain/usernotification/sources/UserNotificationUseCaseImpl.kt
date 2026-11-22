package io.festicore.domain.usernotification.sources

import io.festicore.domain.usernotification.UserNotificationRepository
import io.festicore.domain.usernotification.UserNotificationUseCase

/** 저장소를 그대로 부른다. iOS `UserNotificationUseCaseImpl`. */
internal class UserNotificationUseCaseImpl(private val repository: UserNotificationRepository) : UserNotificationUseCase {
    override suspend fun notifications(before: String?) = repository.notifications(before)

    override suspend fun unreadCount() = repository.unreadCount()

    override suspend fun markRead(ids: List<String>, sessionId: String) = repository.markRead(ids, sessionId)

    override suspend fun delete(ids: List<String>, sessionId: String) = repository.delete(ids, sessionId)
}
