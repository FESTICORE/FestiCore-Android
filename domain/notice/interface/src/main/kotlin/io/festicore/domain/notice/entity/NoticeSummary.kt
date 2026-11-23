package io.festicore.domain.notice.entity

import java.time.Instant

/** 공지 목록 한 줄. iOS `NoticeSummary`. */
data class NoticeSummary(
    val id: String,
    val title: String,
    val publishedAt: Instant,
)
