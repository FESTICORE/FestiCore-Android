package io.festicore.service.festivalcatalog.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FestivalImageUrlResolverTest {
    private val resolver = FestivalImageUrlResolver("https://stage.example.test/")

    @Test
    fun `상대 경로는 BASE_URL과 합친다`() {
        assertEquals(
            "https://stage.example.test/api/v1/festivals/images/a.webp",
            resolver.resolve("/api/v1/festivals/images/a.webp"),
        )
    }

    @Test
    fun `https 주소는 그대로 쓴다`() {
        assertEquals("https://cdn.example.test/a.webp", resolver.resolve("https://cdn.example.test/a.webp"))
        assertEquals("HTTPS://cdn.example.test/a.webp", resolver.resolve("HTTPS://cdn.example.test/a.webp"))
    }

    @Test
    fun `http 주소는 버린다`() {
        assertNull(resolver.resolve("http://cdn.example.test/a.webp"))
    }

    @Test
    fun `슬래시 두 개로 시작하면 버린다`() {
        assertNull(resolver.resolve("//cdn.example.test/a.webp"))
    }

    @Test
    fun `앞 슬래시가 없는 상대 경로는 버린다`() {
        assertNull(resolver.resolve("images/a.webp"))
    }

    @Test
    fun `빈 값 · null · 잘못된 주소는 버린다`() {
        assertNull(resolver.resolve(""))
        assertNull(resolver.resolve(null))
        assertNull(resolver.resolve("/a b|c"))
    }

    @Test
    fun `BASE_URL이 https가 아니거나 없으면 상대 경로를 버린다`() {
        assertNull(FestivalImageUrlResolver("http://stage.example.test/").resolve("/a.webp"))
        assertNull(FestivalImageUrlResolver(null).resolve("/a.webp"))
    }
}
