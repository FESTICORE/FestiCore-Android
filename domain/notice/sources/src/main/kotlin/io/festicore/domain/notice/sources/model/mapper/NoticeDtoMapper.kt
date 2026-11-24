package io.festicore.domain.notice.sources.model.mapper

import io.festicore.core.network.error.NetworkError
import io.festicore.core.utility.date.AppDateFormat
import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.domain.notice.entity.NoticeError
import io.festicore.domain.notice.entity.NoticeSummary
import io.festicore.domain.notice.sources.model.dto.NoticeDetailDto
import io.festicore.domain.notice.sources.model.dto.NoticeSummaryDto
import kotlin.coroutines.cancellation.CancellationException

/** iOS `NoticeDTO+Mapper`. 게시 시각을 해석하지 못하면 [NoticeError.InvalidResponse]. */
internal fun NoticeSummaryDto.toDomain(): NoticeSummary = NoticeSummary(id, title, publishedAt.toInstant())

internal fun NoticeDetailDto.toDomain(): NoticeDetail = NoticeDetail(id, title, content, publishedAt.toInstant())

private fun String.toInstant() = AppDateFormat.isoInstant(this) ?: throw NoticeError.InvalidResponse()

/** 요청 오류를 [NoticeError]로 바꾼다. 취소는 그대로 둔다. */
internal fun Throwable.toNoticeError(): Throwable = when (this) {
    is CancellationException, is NoticeError -> this
    is NetworkError.InvalidResponse, is NetworkError.Decoding -> NoticeError.InvalidResponse()
    else -> NoticeError.Unavailable()
}
