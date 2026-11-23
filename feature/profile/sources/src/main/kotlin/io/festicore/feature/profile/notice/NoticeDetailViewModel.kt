package io.festicore.feature.profile.notice

import androidx.lifecycle.ViewModel
import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.notice.NoticeUseCase
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** 공지 상세. iOS `NoticeDetailFeature`. 처음 나타날 때만 읽는다. */
class NoticeDetailViewModel(
    id: String,
    title: String,
    private val noticeUseCase: NoticeUseCase,
) : ViewModel(),
    OrbitContainerHost<NoticeDetailState, NoticeDetailState, NoticeDetailSideEffect> {
    override val container = orbitContainer<NoticeDetailState, NoticeDetailSideEffect>(NoticeDetailState(id = id, title = title))

    // view

    fun onAppear() = intent {
        if (state.load != NoticeLoad.Idle) return@intent
        reduce { state.copy(load = NoticeLoad.Loading) }
        fetchNotice()
    }

    fun onBackTapped() = intent { postSideEffect(NoticeDetailSideEffect.BackRequested) }

    fun onRetryTapped() = intent {
        if (state.load != NoticeLoad.Failed) return@intent
        reduce { state.copy(load = NoticeLoad.Loading) }
        fetchNotice()
    }

    // async

    private suspend fun fetchNotice() = subIntent {
        val detail =
            try {
                noticeUseCase.notice(state.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onNoticeFailed()
                return@subIntent
            }
        onNoticeLoaded(detail)
    }

    // inner

    private suspend fun onNoticeLoaded(detail: NoticeDetail) = subIntent {
        reduce { state.copy(detail = detail, load = NoticeLoad.Loaded) }
    }

    private suspend fun onNoticeFailed() = subIntent {
        reduce { state.copy(load = NoticeLoad.Failed) }
    }
}
