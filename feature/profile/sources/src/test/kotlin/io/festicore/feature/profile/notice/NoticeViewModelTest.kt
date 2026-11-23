package io.festicore.feature.profile.notice

import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.entity.NoticeError
import io.festicore.domain.notice.entity.NoticeSummary
import io.festicore.domain.notice.notice.NoticeUseCase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `NoticeFeatureTests` 5건(같은 이름). 상세 열기 · 닫기는 iOS `destination` 대신 SideEffect → Coordinator(T1)로 본다. */
class NoticeViewModelTest {
    private class FakeNoticeUseCase(
        private val list: () -> List<NoticeSummary> = { emptyList() },
        private val detail: (String) -> NoticeDetail = { throw NoticeError.Unavailable() },
    ) : NoticeUseCase {
        var listCalls = 0
        val detailRequests = mutableListOf<String>()

        override suspend fun notices(): List<NoticeSummary> {
            listCalls += 1
            return list()
        }

        override suspend fun notice(id: String): NoticeDetail {
            detailRequests += id
            return detail(id)
        }
    }

    private val id = "2c75c8de-517d-4611-9c51-b4ff804dd597"

    @Test
    fun noticeDateUsesSeoulDayAcrossUTCMidnightBoundary() {
        assertEquals("2026.11.04", NoticePresentation.day(Instant.parse("2026-11-03T15:30:00Z")))
    }

    @Test
    fun listLoadsPublishedNoticesAndOpensDetail() = runTest {
        val notices = listOf(NoticeSummary(id = id, title = "서비스 안내", publishedAt = Instant.EPOCH))
        NoticeListViewModel(FakeNoticeUseCase(list = { notices })).test(this) {
            containerHost.onAppear()
            expectState { copy(load = NoticeLoad.Loading) }
            expectState { copy(notices = notices, load = NoticeLoad.Loaded) }
            // 목록에 없는 공지는 열지 않는다.
            containerHost.onNoticeTapped("missing").join()
            containerHost.onNoticeTapped(id)
            expectSideEffect(NoticeListSideEffect.NoticeDetailRequested(id = id, title = "서비스 안내"))
            containerHost.onBackTapped()
            expectSideEffect(NoticeListSideEffect.BackRequested)
        }
    }

    @Test
    fun emptyListIsLoadedInsteadOfShowingSkeletonForever() = runTest {
        val useCase = FakeNoticeUseCase(list = { emptyList() })
        NoticeListViewModel(useCase).test(this) {
            containerHost.onAppear()
            expectState { copy(load = NoticeLoad.Loading) }
            expectState { copy(load = NoticeLoad.Loaded) }
            // 다시 나타나도 읽지 않는다.
            containerHost.onAppear().join()
        }
        assertEquals(1, useCase.listCalls)
    }

    @Test
    fun listFailureCanRetry() = runTest {
        var attempts = 0
        val useCase =
            FakeNoticeUseCase(list = {
                attempts += 1
                if (attempts == 1) throw NoticeError.Unavailable()
                emptyList()
            })
        NoticeListViewModel(useCase).test(this) {
            containerHost.onAppear()
            expectState { copy(load = NoticeLoad.Loading) }
            expectState { copy(load = NoticeLoad.Failed) }
            containerHost.onRetryTapped()
            expectState { copy(load = NoticeLoad.Loading) }
            expectState { copy(load = NoticeLoad.Loaded) }
        }
        assertEquals(2, attempts)
    }

    @Test
    fun detailLoadsContentAndCanRetryAfterFailure() = runTest {
        val detail = NoticeDetail(id = id, title = "서비스 안내", content = "공지 본문", publishedAt = Instant.EPOCH)
        var attempts = 0
        val useCase =
            FakeNoticeUseCase(detail = {
                attempts += 1
                if (attempts == 1) throw NoticeError.Unavailable()
                detail
            })
        NoticeDetailViewModel(id = id, title = detail.title, noticeUseCase = useCase).test(this) {
            containerHost.onAppear()
            expectState { copy(load = NoticeLoad.Loading) }
            expectState { copy(load = NoticeLoad.Failed) }
            containerHost.onRetryTapped()
            expectState { copy(load = NoticeLoad.Loading) }
            expectState { copy(detail = detail, load = NoticeLoad.Loaded) }
            containerHost.onBackTapped()
            expectSideEffect(NoticeDetailSideEffect.BackRequested)
        }
        assertEquals(listOf(id, id), useCase.detailRequests)
    }
}
