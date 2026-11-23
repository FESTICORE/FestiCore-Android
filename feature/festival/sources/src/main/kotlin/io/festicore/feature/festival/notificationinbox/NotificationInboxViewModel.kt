package io.festicore.feature.festival.notificationinbox

import androidx.lifecycle.ViewModel
import io.festicore.domain.usernotification.entity.UserNotificationBadgeUseCase
import io.festicore.domain.usernotification.entity.UserNotificationPage
import io.festicore.domain.usernotification.usernotification.UserNotificationUseCase
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 알림함(iOS `NotificationInboxFeature`). 함수 이름은 iOS `Action.View`와 같고 순서는 view → async → inner를 따른다.
 * 읽음 요청과 배지 갱신은 공유 배지 저장소가 맡아 화면이 닫혀도 끝까지 간다(설계 E7).
 * 읽음 · 삭제는 첫 쪽을 받은 세션에 묶는다(iOS `sessionBound`). 요청 때 현재 세션을 다시 읽지 않는다.
 * 쪽 조회 · 배지 조회는 각각 하나만 돈다(iOS `cancelInFlight`).
 */
class NotificationInboxViewModel(
    private val notificationUseCase: UserNotificationUseCase,
    private val badgeUseCase: UserNotificationBadgeUseCase,
) : ViewModel(),
    OrbitContainerHost<NotificationInboxState, NotificationInboxState, NotificationInboxSideEffect> {
    override val container = orbitContainer<NotificationInboxState, NotificationInboxSideEffect>(NotificationInboxState())

    private val pageJob = AtomicReference<Job?>(null)
    private val unreadCountJob = AtomicReference<Job?>(null)

    // MARK: - View

    fun onAppear() = intent {
        if (state.load != NotificationInboxLoad.Idle) return@intent
        reduce { state.copy(load = NotificationInboxLoad.Loading) }
        fetchPage(before = null)
    }

    fun onRetryTapped() = intent {
        if (state.load != NotificationInboxLoad.Failed) return@intent
        reduce { state.copy(load = NotificationInboxLoad.Loading) }
        fetchPage(before = null)
    }

    fun onEditTapped() = intent {
        if (state.load != NotificationInboxLoad.Loaded || state.items.isEmpty() || state.isLoadingMore) return@intent
        reduce { state.copy(isEditing = true) }
    }

    fun onCancelSelectionTapped() = intent { reduce { state.copy(isEditing = false, selectedIds = emptySet()) } }

    fun onSelectAllTapped() = intent {
        if (state.load != NotificationInboxLoad.Loaded || !state.isEditing || state.items.isEmpty()) return@intent
        reduce { state.copy(selectedIds = if (state.allLoadedSelected) emptySet() else state.items.map { it.id }.toSet()) }
    }

    fun onDeleteTapped() = intent {
        val current = state
        if (current.load != NotificationInboxLoad.Loaded || !current.isEditing || current.items.isEmpty()) return@intent
        if (current.selectedIds.isEmpty() || current.isDeleting) return@intent
        val ids = current.items.map { it.id }.filter { it in current.selectedIds }
        val sessionId = current.sessionId
        if (ids.isEmpty() || sessionId == null) return@intent
        reduce { state.copy(isDeleting = true) }
        delete(ids, sessionId)
    }

    /**
     * 선택 모드면 선택을 바꾼다. 아니면 안 읽은 알림을 처음 열 때만 읽음을 보내고, 라이브 보드 경로면 연다.
     * 다른 경로는 "아직 열 수 없는 알림이에요" 팝업.
     */
    fun onRowTapped(id: String) = intent {
        if (state.load != NotificationInboxLoad.Loaded) return@intent
        val item = state.items.firstOrNull { it.id == id } ?: return@intent
        if (state.isEditing) {
            reduce { state.copy(selectedIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id) }
            return@intent
        }
        val sessionId = state.sessionId
        val shouldRead = item.readAt == null && id !in state.attemptedReadIds && sessionId != null
        val festivalId = NotificationInboxPresentation.liveFestivalId(item)
        reduce {
            state.copy(
                attemptedReadIds = if (shouldRead) state.attemptedReadIds + id else state.attemptedReadIds,
                locallyReadIds = if (shouldRead) state.locallyReadIds + id else state.locallyReadIds,
                dialog = if (festivalId == null) NotificationInboxDialog.DestinationUnavailable else state.dialog,
            )
        }
        if (shouldRead && sessionId != null) badgeUseCase.markReadInBackground(id, sessionId)
        if (festivalId != null) postSideEffect(NotificationInboxSideEffect.FestivalOpened(festivalId))
    }

    fun onLoadMoreTapped() = intent {
        if (state.load != NotificationInboxLoad.Loaded || state.isEditing || state.isLoadingMore) return@intent
        val cursor = state.nextCursor ?: return@intent
        reduce { state.copy(isLoadingMore = true) }
        fetchPage(before = cursor)
    }

    /** 보드에 그 축제가 없어 열 수 없다(iOS `destinationUnavailable`). */
    fun onDestinationUnavailable() = intent { reduce { state.copy(dialog = NotificationInboxDialog.DestinationUnavailable) } }

    fun onDialogDismissed() = intent { reduce { state.copy(dialog = null) } }

    // MARK: - Async

    private fun fetchPage(before: String?) = replace(pageJob) {
        val page =
            try {
                notificationUseCase.notifications(before)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                onPageFailed(before)
                return@replace
            }
        onPageLoaded(page, before)
    }

    private suspend fun delete(ids: List<String>, sessionId: String) = subIntent {
        try {
            notificationUseCase.delete(ids, sessionId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            onDeleteFailed()
            return@subIntent
        }
        onDeleteSucceeded(ids)
    }

    /** 삭제 뒤 안 읽은 수를 공유 배지에 반영한다(iOS `refreshUnreadCount` → `unreadCountChanged`). */
    private fun refreshUnreadCount() = replace(unreadCountJob) { badgeUseCase.refreshUnreadCount() }

    // MARK: - Inner

    private suspend fun onPageLoaded(page: UserNotificationPage, requestedCursor: String?) = subIntent {
        reduce {
            val items =
                if (requestedCursor == null) {
                    page.items
                } else {
                    val known = state.items.map { it.id }.toSet()
                    state.items + page.items.filter { it.id !in known }
                }
            val ids = items.map { it.id }.toSet()
            state.copy(
                items = items,
                locallyReadIds = if (requestedCursor == null) emptySet() else state.locallyReadIds,
                selectedIds = state.selectedIds intersect ids,
                isEditing = state.isEditing && items.isNotEmpty(),
                nextCursor = page.nextCursor.takeIf { it != requestedCursor },
                sessionId = if (requestedCursor == null) page.sessionId else state.sessionId,
                load = NotificationInboxLoad.Loaded,
                isLoadingMore = false,
            )
        }
    }

    private suspend fun onPageFailed(requestedCursor: String?) = subIntent {
        reduce {
            if (requestedCursor == null) {
                state.copy(isLoadingMore = false, load = NotificationInboxLoad.Failed)
            } else {
                state.copy(isLoadingMore = false, dialog = NotificationInboxDialog.LoadMoreFailed)
            }
        }
    }

    private suspend fun onDeleteSucceeded(ids: List<String>) = subIntent {
        val removed = ids.toSet()
        reduce {
            state.copy(
                items = state.items.filterNot { it.id in removed },
                locallyReadIds = state.locallyReadIds - removed,
                attemptedReadIds = state.attemptedReadIds - removed,
                selectedIds = emptySet(),
                isEditing = false,
                isDeleting = false,
            )
        }
        if (state.items.isEmpty() && state.nextCursor != null) {
            reduce { state.copy(load = NotificationInboxLoad.Loading, nextCursor = null) }
            fetchPage(before = null)
        }
        refreshUnreadCount()
    }

    private suspend fun onDeleteFailed() = subIntent {
        reduce {
            state.copy(
                isDeleting = false,
                isEditing = false,
                selectedIds = emptySet(),
                load = NotificationInboxLoad.Loading,
                nextCursor = null,
                dialog = NotificationInboxDialog.DeleteFailed,
            )
        }
        fetchPage(before = null)
        refreshUnreadCount()
    }

    // MARK: - 도우미

    private fun replace(slot: AtomicReference<Job?>, block: suspend () -> Unit) {
        val job = intent { block() }
        slot.getAndSet(job)?.cancel()
    }
}
