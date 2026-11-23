package io.festicore.domain.notice.notice

import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.entity.NoticeError
import io.festicore.domain.notice.entity.NoticeSummary

/** 공지 서버 저장소. 로그인 없이 읽는다. 실패는 [NoticeError]. iOS `NoticeRepositoryInterface`. */
interface NoticeRepository {
    /** 게시된 공지 최신 100건. */
    suspend fun notices(): List<NoticeSummary>

    suspend fun notice(id: String): NoticeDetail
}
