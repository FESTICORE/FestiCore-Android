package io.festicore.domain.notice.sources.repository

import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.entity.NoticeSummary
import io.festicore.domain.notice.notice.NoticeRepository
import io.festicore.domain.notice.sources.model.dto.NoticeDetailDto
import io.festicore.domain.notice.sources.model.dto.NoticeListDto
import io.festicore.domain.notice.sources.model.mapper.toDomain
import io.festicore.domain.notice.sources.model.mapper.toNoticeError
import io.festicore.service.apiendpoint.notice.NoticeRequest

/** 공지 저장소(iOS `NoticeRepositoryImpl`). 인증 없이 읽고 로컬 저장은 없다. 한 항목이라도 시각이 틀리면 목록 전체를 실패로 본다. */
internal class NoticeRepositoryImpl(private val api: NoticeRequest) : NoticeRepository {
    override suspend fun notices(): List<NoticeSummary> = mapErrors { api.list<NoticeListDto>().items.map { it.toDomain() } }

    override suspend fun notice(id: String): NoticeDetail = mapErrors { api.detail<NoticeDetailDto>(id).toDomain() }

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw e.toNoticeError()
    }
}
