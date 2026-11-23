package io.festicore.feature.profile.notice

import androidx.lifecycle.ViewModel
import io.festicore.domain.notice.entity.NoticeSummary
import io.festicore.domain.notice.notice.NoticeUseCase
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** 공지 목록. iOS `NoticeListFeature`. 처음 나타날 때만 읽는다. */
class NoticeListViewModel(private val noticeUseCase: NoticeUseCase) :
    ViewModel(),
    OrbitContainerHost<NoticeListState, NoticeListState, NoticeListSideEffect> {
    override val container = orbitContainer<NoticeListState, NoticeListSideEffect>(NoticeListState())

    // view

    fun onAppear() = intent {
        if (state.load != NoticeLoad.Idle) return@intent
        reduce { state.copy(load = NoticeLoad.Loading) }
        fetchNotices()
    }

    fun onBackTapped() = intent { postSideEffect(NoticeListSideEffect.BackRequested) }

    fun onRetryTapped() = intent {
        if (state.load != NoticeLoad.Failed) return@intent
        reduce { state.copy(load = NoticeLoad.Loading) }
        fetchNotices()
    }

    fun onNoticeTapped(id: String) = intent {
        val notice = state.notices.firstOrNull { it.id == id } ?: return@intent
        postSideEffect(NoticeListSideEffect.NoticeDetailRequested(id = notice.id, title = notice.title))
    }

    // async

    private suspend fun fetchNotices() = subIntent {
        val notices =
            try {
                noticeUseCase.notices()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onNoticesFailed()
                return@subIntent
            }
        onNoticesLoaded(notices)
    }

    // inner

    private suspend fun onNoticesLoaded(notices: List<NoticeSummary>) = subIntent {
        reduce { state.copy(notices = notices, load = NoticeLoad.Loaded) }
    }

    private suspend fun onNoticesFailed() = subIntent {
        reduce { state.copy(load = NoticeLoad.Failed) }
    }
}
