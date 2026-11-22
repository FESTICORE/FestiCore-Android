package io.festicore.domain.festival

import java.time.Instant

/** 축제 공식 인스타그램 최근 게시물. 서버가 5분마다 받아 둔 것. iOS `FestivalOfficialNews`. */
data class FestivalOfficialNews(
    /** 공식 계정이 없거나 서버가 아직 한 번도 받지 못했으면 false. */
    val isAvailable: Boolean,
    val account: Account?,
    /** 최신 게시물이 먼저 온다. */
    val posts: List<Post>,
) {
    /** 라이브 보드에 섹션을 보일지. 게시물이 하나라도 있어야 보인다. */
    val isVisible: Boolean get() = isAvailable && posts.isNotEmpty()

    data class Account(
        val username: String,
        val profileUrl: String,
    )

    data class Post(
        val id: String,
        val caption: String?,
        /** https 이미지 주소. 없으면 캡션만 보인다. */
        val imageUrl: String?,
        /** 인스타그램 원본 게시물(https, `instagram.com` · `www.instagram.com`). */
        val permalink: String,
        val postedAt: Instant,
    )

    companion object {
        /** 공식 계정이 없거나 아직 받지 못한 상태. */
        val Unavailable = FestivalOfficialNews(isAvailable = false, account = null, posts = emptyList())
    }
}
