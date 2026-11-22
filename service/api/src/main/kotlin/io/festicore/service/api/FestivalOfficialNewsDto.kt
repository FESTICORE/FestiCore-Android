package io.festicore.service.api

import kotlinx.serialization.Serializable

/** `GET api/v1/festivals/{id}/instagram` 응답 `data`. iOS `FestivalOfficialNewsDataDTO`. */
@Serializable
data class FestivalOfficialNewsDto(
    val festivalId: String,
    val available: Boolean,
    val account: Account? = null,
    val fetchedAt: String? = null,
    val posts: List<Post>,
) {
    @Serializable
    data class Account(
        val username: String,
        val url: String,
    )

    @Serializable
    data class Post(
        val id: String,
        val caption: String? = null,
        val mediaType: String,
        val imageUrl: String? = null,
        val permalink: String,
        val postedAt: String,
    )
}
