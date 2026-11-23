package io.festicore.feature.festival.notificationinbox

import io.festicore.domain.usernotification.entity.UserNotificationItem
import io.festicore.domain.usernotification.entity.UserNotificationPage
import io.festicore.domain.usernotification.usernotification.UserNotificationUseCase
import io.festicore.feature.festival.liveboard.FakeNotificationBadgeUseCase
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `NotificationInboxFeatureTests` 11건(같은 이름). 알림 값은 테스트 가짜 값이다. */
class NotificationInboxViewModelTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    private fun item(
        id: String,
        createdAt: Instant = Instant.EPOCH,
        readAt: Instant? = null,
        route: String? = null,
    ) = UserNotificationItem(
        id = id,
        type = "festival.start",
        festivalId = "fst",
        title = "공연 시작",
        body = "곧 시작합니다",
        createdAt = createdAt,
        readAt = readAt,
        route = route,
    )

    private class FakeNotificationUseCase(
        var notifications: suspend (String?) -> UserNotificationPage = { UserNotificationPage(emptyList(), null, SESSION) },
        var delete: suspend (List<String>) -> Unit = {},
    ) : UserNotificationUseCase {
        val requestedCursors = mutableListOf<String?>()
        val deleteSessions = mutableListOf<String>()

        override suspend fun notifications(before: String?): UserNotificationPage {
            requestedCursors += before
            return notifications.invoke(before)
        }

        override suspend fun unreadCount() = 0

        override suspend fun markRead(ids: List<String>, sessionId: String) = Unit

        override suspend fun delete(ids: List<String>, sessionId: String) {
            deleteSessions += sessionId
            delete.invoke(ids)
        }
    }

    private fun viewModel(
        notifications: FakeNotificationUseCase = FakeNotificationUseCase(),
        badge: FakeNotificationBadgeUseCase = FakeNotificationBadgeUseCase(),
    ) = NotificationInboxViewModel(notifications, badge)

    private fun loaded(vararg items: UserNotificationItem) =
        NotificationInboxState(load = NotificationInboxLoad.Loaded, items = items.toList(), sessionId = SESSION)

    @Test
    fun sectionsUseSeoulCalendar() {
        val now = LocalDateTime.of(2026, 11, 4, 0, 10).atZone(seoul).toInstant()
        val today = LocalDateTime.of(2026, 11, 4, 0, 5).atZone(seoul).toInstant()
        val yesterday = LocalDateTime.of(2026, 11, 3, 23, 50).atZone(seoul).toInstant()
        val sections = NotificationInboxPresentation.sections(listOf(item("today", today), item("yesterday", yesterday)), now)
        assertEquals(listOf("오늘", "어제"), sections.map { it.title })
        assertEquals("5분 전", NotificationInboxPresentation.time(sections[0].items[0], now))
        assertEquals("어제 23:50", NotificationInboxPresentation.time(sections[1].items[0], now))
        // 그 밖의 날짜는 yyyy-MM-dd 구역, MM/dd HH:mm 시각.
        val older = LocalDateTime.of(2026, 10, 31, 9, 5).atZone(seoul).toInstant()
        assertEquals("2026-10-31", NotificationInboxPresentation.sections(listOf(item("old", older)), now).single().title)
        assertEquals("10/31 09:05", NotificationInboxPresentation.time(item("old", older), now))
    }

    @Test
    fun readItemOpensMatchingFestival() = runTest {
        val badge = FakeNotificationBadgeUseCase()
        viewModel(badge = badge).test(this, initialState = loaded(item("read", readAt = Instant.MIN, route = "/festivals/fst/live"))) {
            containerHost.onRowTapped("read")
            expectSideEffect(NotificationInboxSideEffect.FestivalOpened("fst"))
        }
        assertEquals(emptyList<String>(), badge.markedRead)
    }

    @Test
    fun unsupportedRouteStaysInInbox() = runTest {
        viewModel().test(this, initialState = loaded(item("other", readAt = Instant.MIN, route = "/festivals/other/live"))) {
            containerHost.onRowTapped("other")
            expectState { copy(dialog = NotificationInboxDialog.DestinationUnavailable) }
            containerHost.onDialogDismissed()
            expectState { copy(dialog = null) }
        }
    }

    @Test
    fun unreadItemOpensImmediately() = runTest {
        val badge = FakeNotificationBadgeUseCase()
        viewModel(badge = badge).test(this, initialState = loaded(item("new", route = "/festivals/fst/live"))) {
            containerHost.onRowTapped("new")
            expectState { copy(locallyReadIds = setOf("new"), attemptedReadIds = setOf("new")) }
            expectSideEffect(NotificationInboxSideEffect.FestivalOpened("fst"))
            containerHost.onRowTapped("new")
            expectSideEffect(NotificationInboxSideEffect.FestivalOpened("fst"))
        }
        // 읽음 요청은 공유 배지 저장소가 앱 범위에서 끝까지 보낸다(설계 E7). 두 번째는 보내지 않는다.
        assertEquals(listOf("new"), badge.markedRead)
    }

    @Test
    fun deletingLoadedPageFetchesNextItems() = runTest {
        val notifications = FakeNotificationUseCase(notifications = { UserNotificationPage(listOf(item("remaining")), null, SESSION) })
        val badge = FakeNotificationBadgeUseCase()
        val initial =
            loaded(item("first")).copy(nextCursor = "next", isEditing = true, selectedIds = setOf("first"))
        viewModel(notifications, badge).test(this, initialState = initial) {
            containerHost.onDeleteTapped()
            expectState { copy(isDeleting = true) }
            expectState { copy(items = emptyList(), isEditing = false, isDeleting = false, selectedIds = emptySet()) }
            expectState { copy(load = NotificationInboxLoad.Loading, nextCursor = null) }
            expectState { copy(items = listOf(item("remaining")), load = NotificationInboxLoad.Loaded) }
            val state = containerHost.container.stateFlow.value
            assertEquals(listOf("remaining"), state.items.map { it.id })
            assertFalse(state.isEditing)
        }
        assertEquals(listOf<String?>(null), notifications.requestedCursors)
        assertEquals(1, badge.unreadCountRefreshCount)
    }

    @Test
    fun nextPageDeduplicatesItems() = runTest {
        val notifications =
            FakeNotificationUseCase(notifications = { UserNotificationPage(listOf(item("first"), item("second")), null, SESSION) })
        viewModel(notifications).test(this, initialState = loaded(item("first")).copy(nextCursor = "next")) {
            containerHost.onLoadMoreTapped()
            expectState { copy(isLoadingMore = true) }
            expectState { copy(items = listOf(item("first"), item("second")), nextCursor = null, isLoadingMore = false) }
        }
        assertEquals(listOf<String?>("next"), notifications.requestedCursors)
    }

    @Test
    fun hiddenItemsCannotBeEdited() = runTest {
        for (load in listOf(NotificationInboxLoad.Loading, NotificationInboxLoad.Failed)) {
            val notifications = FakeNotificationUseCase()
            val initial = NotificationInboxState(load = load, items = listOf(item("stale")), selectedIds = setOf("stale"))
            viewModel(notifications).test(this, initialState = initial) {
                containerHost.onEditTapped()
                containerHost.onSelectAllTapped()
                containerHost.onDeleteTapped()
            }
        }
    }

    @Test
    fun fullReloadPrunesSelection() = runTest {
        val notifications = FakeNotificationUseCase(notifications = { UserNotificationPage(listOf(item("new")), null, SESSION) })
        val initial =
            NotificationInboxState(
                load = NotificationInboxLoad.Failed,
                items = listOf(item("old")),
                isEditing = true,
                selectedIds = setOf("old"),
            )
        viewModel(notifications).test(this, initialState = initial) {
            containerHost.onRetryTapped()
            expectState { copy(load = NotificationInboxLoad.Loading) }
            expectState {
                copy(items = listOf(item("new")), selectedIds = emptySet(), load = NotificationInboxLoad.Loaded, sessionId = SESSION)
            }
            assertFalse(containerHost.container.stateFlow.value.allLoadedSelected)
        }
    }

    /** iOS는 `instanceID`로 이전 알림함 응답을 버린다. Android는 항목 범위 ViewModel이라 pop(정리) 뒤 응답이 반영되지 않는다. */
    @Test
    fun staleInstanceResponsesAreIgnored() = runTest {
        val late = CompletableDeferred<UserNotificationPage>()
        val inbox = viewModel(FakeNotificationUseCase(notifications = { late.await() }))
        inbox.test(this) {
            containerHost.onAppear()
            expectState { copy(load = NotificationInboxLoad.Loading) }
            // pop: 항목 ViewModel 범위가 끝나며 진행 중 요청이 취소된다.
            cancelAndIgnoreRemainingItems()
        }
        late.complete(UserNotificationPage(listOf(item("other-account")), null, SESSION))
        testScheduler.runCurrent()
        assertEquals(emptyList<UserNotificationItem>(), inbox.container.stateFlow.value.items)
    }

    @Test
    fun paginationPreventsEditing() = runTest {
        viewModel().test(this, initialState = loaded(item("first")).copy(isLoadingMore = true)) {
            containerHost.onEditTapped()
        }
    }

    @Test
    fun fullReloadDoesNotDuplicateReadRequest() = runTest {
        val badge = FakeNotificationBadgeUseCase()
        val unread = item("new", route = "/festivals/fst/live")
        val notifications =
            FakeNotificationUseCase(
                notifications = { UserNotificationPage(listOf(unread, item("x")), null, SESSION) },
                delete = { error("삭제 실패") },
            )
        viewModel(notifications, badge).test(this, initialState = loaded(unread, item("x"))) {
            containerHost.onRowTapped("new")
            // 삭제 실패는 첫 쪽을 다시 읽는다(전체 재조회). 서버는 여전히 안 읽음으로 보낸다.
            containerHost.onEditTapped()
            containerHost.onRowTapped("x")
            containerHost.onDeleteTapped()
            testScheduler.runCurrent()
            val reloaded = containerHost.container.stateFlow.value
            assertEquals(NotificationInboxLoad.Loaded, reloaded.load)
            assertEquals(emptySet<String>(), reloaded.locallyReadIds)
            assertEquals(NotificationInboxDialog.DeleteFailed, reloaded.dialog)
            containerHost.onDialogDismissed()
            containerHost.onRowTapped("new")
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(listOf("new"), badge.markedRead)
        assertEquals(1, badge.unreadCountRefreshCount)
    }

    /** 첫 쪽을 받은 뒤 계정이 바뀌어도 읽음 · 삭제는 첫 쪽 세션으로 묶여 새 세션으로 나가지 않는다. */
    @Test
    fun readAndDeleteStayBoundToLoadedSession() = runTest {
        val badge = FakeNotificationBadgeUseCase()
        val notifications =
            FakeNotificationUseCase(
                notifications = { before ->
                    if (before == null) {
                        UserNotificationPage(listOf(item("old")), "next", SESSION)
                    } else {
                        UserNotificationPage(listOf(item("more")), null, "s2") // 다음 쪽 전에 계정이 바뀌었다.
                    }
                },
            )
        viewModel(notifications, badge).test(this) {
            containerHost.onAppear()
            testScheduler.runCurrent()
            containerHost.onLoadMoreTapped()
            testScheduler.runCurrent()
            containerHost.onRowTapped("old")
            containerHost.onEditTapped()
            containerHost.onRowTapped("more")
            containerHost.onDeleteTapped()
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(listOf(SESSION), badge.markedReadSessions)
        assertEquals(listOf(SESSION), notifications.deleteSessions)
    }

    private companion object {
        const val SESSION = "s1"
    }
}
