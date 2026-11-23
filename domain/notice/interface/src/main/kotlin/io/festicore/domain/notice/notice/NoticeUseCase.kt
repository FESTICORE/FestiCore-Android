package io.festicore.domain.notice.notice

import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.entity.NoticeSummary

/** 공지 목록 · 상세. 로그인 없이 읽는다. iOS `NoticeUseCaseInterface`. */
interface NoticeUseCase {
    suspend fun notices(): List<NoticeSummary>

    suspend fun notice(id: String): NoticeDetail
}
