package io.festicore.domain.festival.sources.model.mapper

import io.festicore.core.network.FestiCoreJson
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.sources.model.dto.FestivalOfficialNewsDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalOfficialNewsMapperTests`. */
class FestivalOfficialNewsDtoMapperTest {
    private fun news(json: String) = FestiCoreJson.decodeFromString(FestivalOfficialNewsDto.serializer(), json).toDomain()

    @Test
    fun `게시물은 인스타그램 링크와 올바른 시각만 남긴다`() {
        val result = news(
            """{"festivalId":"fst","available":true,
               "account":{"username":"fake_account","url":"https://www.instagram.com/fake_account/"},
               "fetchedAt":"2026-11-20T09:00:00.000Z",
               "posts":[
                {"id":"1","caption":"  타임테이블 안내  ","mediaType":"IMAGE","imageUrl":"https://cdn.example/1.jpg",
                 "permalink":"https://www.instagram.com/p/AAA/","postedAt":"2026-11-20T08:55:00.000Z"},
                {"id":"2","caption":"   ","mediaType":"VIDEO","imageUrl":"http://cdn.example/2.jpg",
                 "permalink":"https://instagram.com/p/BBB/","postedAt":"2026-11-20T08:00:00Z"},
                {"id":"3","caption":null,"mediaType":"IMAGE","imageUrl":null,
                 "permalink":"https://evil.example/p/CCC/","postedAt":"2026-11-20T07:00:00Z"},
                {"id":"4","caption":null,"mediaType":"IMAGE","imageUrl":null,
                 "permalink":"https://www.instagram.com/p/DDD/","postedAt":"not-a-date"},
                {"id":"5","caption":null,"mediaType":"IMAGE","imageUrl":null,
                 "permalink":"http://www.instagram.com/p/EEE/","postedAt":"2026-11-20T06:00:00Z"}
               ]}""",
        )

        assertTrue(result.isAvailable)
        assertTrue(result.isVisible)
        assertEquals("fake_account", result.account?.username)
        assertEquals(listOf("1", "2"), result.posts.map { it.id })
        assertEquals("타임테이블 안내", result.posts[0].caption)
        assertEquals("https://cdn.example/1.jpg", result.posts[0].imageUrl)
        assertEquals(Instant.parse("2026-11-20T08:55:00Z"), result.posts[0].postedAt)
        // http 이미지는 버리고 빈 캡션은 null.
        assertNull(result.posts[1].imageUrl)
        assertNull(result.posts[1].caption)
    }

    @Test
    fun `available이 false면 숨긴다`() {
        val result =
            news("""{"festivalId":"fst","available":false,"account":{"username":"a","url":"https://www.instagram.com/a/"},"posts":[]}""")

        assertEquals(FestivalOfficialNews.Unavailable, result)
        assertFalse(result.isVisible)
    }

    @Test
    fun `게시물이 없으면 받을 수 있어도 섹션이 보이지 않고 인스타그램이 아닌 계정 주소는 버린다`() {
        val result = news("""{"festivalId":"fst","available":true,"account":{"username":"a","url":"https://evil.example/a"},"posts":[]}""")

        assertTrue(result.isAvailable)
        assertFalse(result.isVisible)
        assertNull(result.account)
    }
}
