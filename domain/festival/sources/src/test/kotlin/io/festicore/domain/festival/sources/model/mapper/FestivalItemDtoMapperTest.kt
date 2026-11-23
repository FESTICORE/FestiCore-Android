package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.FestivalCatalogArtist
import io.festicore.domain.festival.FestivalCatalogPerformance
import io.festicore.domain.festival.FestivalTicketLink
import io.festicore.domain.festival.sources.model.dto.FestivalCatalogArtistDto
import io.festicore.domain.festival.sources.model.dto.FestivalCatalogPerformanceDto
import io.festicore.domain.festival.sources.model.dto.FestivalItemDto
import io.festicore.domain.festival.sources.model.dto.FestivalTicketLinkDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FestivalItemDtoMapperTest {
    private fun item(ticketLinks: List<FestivalTicketLinkDto>? = null, artists: List<FestivalCatalogArtistDto>? = null) = FestivalItemDto(
        id = "fst_a",
        name = "가짜 축제",
        venue = "가짜 장소",
        firstDate = "2026-01-01",
        lastDate = "2026-01-02",
        thumbUrl = "/a.webp",
        packVersion = 3,
        status = "upcoming",
        ticketLinks = ticketLinks,
        artists = artists,
    )

    @Test
    fun `필드를 옮기고 썸네일은 주소 변환 결과를 담는다`() {
        val domain = item().toDomain { value -> "https://example.test$value" }

        assertEquals("fst_a", domain.id)
        assertEquals("가짜 장소", domain.venue)
        assertEquals("2026-01-01", domain.firstDate)
        assertEquals("https://example.test/a.webp", domain.thumbnailUrl)
        assertEquals(3, domain.packVersion)
        assertEquals("upcoming", domain.status)
        assertEquals(emptyList<FestivalTicketLink>(), domain.ticketLinks)
        assertNull(domain.artists)
    }

    @Test
    fun `이름이 비었거나 https가 아닌 예매처는 버리고 이름은 다듬는다`() {
        val domain = item(
            ticketLinks = listOf(
                FestivalTicketLinkDto(" 가 ", "https://a.test/x"),
                FestivalTicketLinkDto("  ", "https://b.test"),
                FestivalTicketLinkDto("다", "http://c.test"),
                FestivalTicketLinkDto("라", "https:///nohost"),
                FestivalTicketLinkDto("마", "not a url"),
            ),
        ).toDomain { null }

        assertEquals(listOf(FestivalTicketLink("가", "https://a.test/x")), domain.ticketLinks)
        assertNull(domain.thumbnailUrl)
    }

    @Test
    fun `아티스트 · 공연을 옮긴다`() {
        val domain = item(
            artists = listOf(FestivalCatalogArtistDto("A", listOf(FestivalCatalogPerformanceDto("s", "d", "무대")))),
        ).toDomain { null }

        assertEquals(listOf(FestivalCatalogArtist("A", listOf(FestivalCatalogPerformance("s", "d", "무대")))), domain.artists)
    }
}
