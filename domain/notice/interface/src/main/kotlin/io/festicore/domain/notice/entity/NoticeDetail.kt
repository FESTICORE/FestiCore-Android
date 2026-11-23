package io.festicore.domain.notice.entity

import java.time.Instant

/** 공지 상세. iOS `NoticeDetail`. */
data class NoticeDetail(
    val id: String,
    val title: String,
    val content: String,
    val publishedAt: Instant,
)
