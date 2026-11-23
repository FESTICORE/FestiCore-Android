package io.festicore.domain.notice.sources.model.dto

import kotlinx.serialization.Serializable

/** `GET api/v1/notices` 응답 `data`. iOS `NoticeListDataDTO`. */
@Serializable
data class NoticeListDto(val items: List<NoticeSummaryDto>)

/** 공지 목록 한 줄. 시각은 ISO-8601 문자열이고 매퍼가 해석한다. iOS `NoticeSummaryDTO`. */
@Serializable
data class NoticeSummaryDto(
    val id: String,
    val title: String,
    val publishedAt: String,
)

/** `GET api/v1/notices/{id}` 응답 `data`. iOS `NoticeDetailDataDTO`. */
@Serializable
data class NoticeDetailDto(
    val id: String,
    val title: String,
    val content: String,
    val publishedAt: String,
)
