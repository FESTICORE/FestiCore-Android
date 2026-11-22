package io.festicore.domain.usernotification.sources

import io.festicore.domain.usernotification.UserNotificationBadge
import io.festicore.domain.usernotification.UserNotificationBadgeUseCase
import io.festicore.domain.usernotification.UserNotificationRepository
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 배지 공유 저장소(설계 E7, Koin `single`). 보드 · 알림함 ViewModel은 서로 부를 수 없어 이 값을 함께 본다.
 * [generation]은 [clear]마다 올라 이전 계정의 늦은 응답을 버린다(iOS `accountGeneration`).
 * 읽음 요청은 [scope](앱 범위)에서 돌아 알림함이 닫혀도 끝까지 간다.
 */
internal class UserNotificationBadgeStore(
    private val repository: UserNotificationRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : UserNotificationBadgeUseCase {
    private val state = MutableStateFlow(UserNotificationBadge())
    private val generation = AtomicInteger(0)

    override val badge: StateFlow<UserNotificationBadge> = state.asStateFlow()

    override suspend fun refresh() {
        val requested = generation.get()
        val count = attempt { repository.unreadCount() } ?: return
        if (!applyCount(requested, count) || count <= 0) return
        val now = clock.instant()
        val page = attempt { repository.notifications(before = null) } ?: return
        val latest =
            page.items
                .filter { it.readAt == null && Duration.between(it.createdAt, now) < BANNER_LIFETIME }
                .maxByOrNull { it.createdAt }
        if (generation.get() != requested || state.value.unreadCount <= 0) return
        state.update { it.copy(banner = latest) }
    }

    override suspend fun refreshUnreadCount() {
        val requested = generation.get()
        val count = attempt { repository.unreadCount() } ?: return
        applyCount(requested, count)
    }

    override fun markReadInBackground(id: String) {
        val requested = generation.get()
        scope.launch {
            attempt { repository.markRead(listOf(id)) }
            if (generation.get() == requested) refresh()
        }
    }

    override fun clear() {
        generation.incrementAndGet()
        state.value = UserNotificationBadge()
    }

    /** 같은 계정 응답이면 반영하고 `true`. 0 이하면 배너도 지운다. */
    private fun applyCount(requested: Int, count: Int): Boolean {
        if (generation.get() != requested) return false
        state.update { badge -> UserNotificationBadge(maxOf(0, count), badge.banner.takeIf { count > 0 }) }
        return true
    }

    private companion object {
        val BANNER_LIFETIME: Duration = UserNotificationBadgeUseCase.BANNER_LIFETIME.toJavaDuration()

        /** 실패는 마지막 값을 두려고 null로 바꾼다. 취소는 그대로 올린다. */
        suspend fun <T> attempt(block: suspend () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
