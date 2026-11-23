package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.FestivalIsoTime
import io.festicore.domain.festival.FestivalOfficialNews
import io.festicore.domain.festival.sources.model.dto.FestivalOfficialNewsDto
import java.net.URI
import java.net.URISyntaxException

private val instagramHosts = setOf("www.instagram.com", "instagram.com")

/**
 * iOS `FestivalOfficialNewsDataDTO.toDomain()`. `available == false`면 [FestivalOfficialNews.Unavailable].
 * 게시물 링크 · 계정 주소는 https + 인스타그램 호스트만, 이미지는 https 절대 주소만 받는다. 링크나 게시 시각이 틀린 게시물은 버린다.
 */
internal fun FestivalOfficialNewsDto.toDomain(): FestivalOfficialNews {
    if (!available) return FestivalOfficialNews.Unavailable
    val posts = posts.mapNotNull { post ->
        val permalink = instagramUrl(post.permalink) ?: return@mapNotNull null
        val postedAt = FestivalIsoTime.parse(post.postedAt) ?: return@mapNotNull null
        FestivalOfficialNews.Post(
            id = post.id,
            caption = post.caption?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = httpsUrl(post.imageUrl),
            permalink = permalink,
            postedAt = postedAt,
        )
    }
    val account = account?.let { account -> instagramUrl(account.url)?.let { FestivalOfficialNews.Account(account.username, it) } }
    return FestivalOfficialNews(isAvailable = true, account = account, posts = posts)
}

private fun httpsUrl(value: String?): String? {
    val uri = value?.toUriOrNull() ?: return null
    return value.takeIf { uri.scheme == "https" && !uri.host.isNullOrEmpty() }
}

private fun instagramUrl(value: String): String? = httpsUrl(value)?.takeIf { it.toUriOrNull()?.host in instagramHosts }

private fun String.toUriOrNull(): URI? = try {
    URI(this)
} catch (_: URISyntaxException) {
    null
}
