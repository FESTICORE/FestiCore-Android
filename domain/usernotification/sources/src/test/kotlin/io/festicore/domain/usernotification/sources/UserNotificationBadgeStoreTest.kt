package io.festicore.domain.usernotification.sources

import io.festicore.domain.usernotification.UserNotificationBadge
import io.festicore.domain.usernotification.UserNotificationBadgeUseCase
import io.festicore.domain.usernotification.UserNotificationError
import io.festicore.domain.usernotification.UserNotificationItem
import io.festicore.domain.usernotification.UserNotificationPage
import io.festicore.domain.usernotification.UserNotificationRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * iOS `FestivalNotificationBadgeTests`의 저장소 부분 4건(같은 이름). 배지 · 배너 · 계정 세대 · 읽음 뒤 재조회는
 * Android에서 공유 저장소가 맡는다(설계 E7). 보드 진입 · 계정 변경 1건은 feature `LiveBoardNotificationBadgeTest`.
 */
class UserNotificationBadgeStoreTest {
    private fun item(
        id: String,
        createdAt: Instant,
        readAt: Instant? = null,
    ) = UserNotificationItem(
        id = id,
        type = "crew",
        festivalId = "festival-a",
        title = "모여! · 민지",
        body = "리벳 스테이지 앞으로 모여 주세요",
        createdAt = createdAt,
        readAt = readAt,
        route = null,
    )

    private class FakeRepository(
        var unreadCount: suspend () -> Int = { 0 },
        var notifications: suspend (String?) -> UserNotificationPage = { UserNotificationPage(emptyList(), null) },
        var markRead: suspend (List<String>) -> Unit = {},
    ) : UserNotificationRepository {
        override suspend fun notifications(before: String?) = notifications.invoke(before)

        override suspend fun unreadCount() = unreadCount.invoke()

        override suspend fun markRead(ids: List<String>) = markRead.invoke(ids)

        override suspend fun delete(ids: List<String>) = Unit
    }

    private fun TestScope.store(repository: FakeRepository, now: Instant) =
        UserNotificationBadgeStore(repository, this, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun staleUnreadCountIsIgnored() = runTest {
        val gate = CompletableDeferred<Int>()
        val repository = FakeRepository(unreadCount = { gate.await() })
        val store = store(repository, Instant.EPOCH)
        val pending = async { store.refresh() }
        runCurrent()
        store.clear() // 계정이 바뀌었다.
        gate.complete(5)
        pending.await()
        assertEquals(UserNotificationBadge(), store.badge.value)

        repository.unreadCount = { 2 }
        store.refreshUnreadCount()
        assertEquals(2, store.badge.value.unreadCount)
    }

    @Test
    fun latestUnreadBannerAndStaleAccount() = runTest {
        val latest = item("new", Instant.ofEpochSecond(100))
        val read = item("read", Instant.ofEpochSecond(200), readAt = Instant.ofEpochSecond(250))
        val repository =
            FakeRepository(unreadCount = { 1 }, notifications = { UserNotificationPage(listOf(read, latest), null) })
        val store = store(repository, Instant.ofEpochSecond(300))
        store.refresh()
        assertEquals(UserNotificationBadge(1, latest), store.badge.value)

        // 이전 계정의 늦은 배너 응답은 버린다.
        val gate = CompletableDeferred<UserNotificationPage>()
        repository.notifications = { gate.await() }
        val pending = async { store.refresh() }
        runCurrent()
        store.clear()
        gate.complete(UserNotificationPage(listOf(latest), null))
        pending.await()
        assertEquals(UserNotificationBadge(), store.badge.value)

        // 안 읽은 수가 0이 되면 배너도 지운다.
        repository.notifications = { UserNotificationPage(listOf(latest), null) }
        store.refresh()
        repository.unreadCount = { 0 }
        store.refreshUnreadCount()
        assertEquals(UserNotificationBadge(0, null), store.badge.value)
    }

    @Test
    fun oldUnreadNotificationIsNotBanner() = runTest {
        val old = item("old", Instant.EPOCH)
        val repository = FakeRepository(unreadCount = { 1 }, notifications = { UserNotificationPage(listOf(old), null) })
        val store = store(repository, Instant.EPOCH.plus(UserNotificationBadgeUseCase.BANNER_LIFETIME.toJavaDuration()))
        store.refresh()
        assertEquals(1, store.badge.value.unreadCount)
        assertNull(store.badge.value.banner)
    }

    @Test
    fun failedReadRefreshesUnreadCount() = runTest {
        val calls = mutableListOf<List<String>>()
        val repository =
            FakeRepository(
                unreadCount = { 3 },
                markRead = { ids ->
                    calls += ids
                    throw UserNotificationError.Unavailable()
                },
            )
        val store = store(repository, Instant.EPOCH)
        store.markReadInBackground("new")
        advanceUntilIdle()
        assertEquals(listOf(listOf("new")), calls)
        assertEquals(3, store.badge.value.unreadCount)
    }
}
