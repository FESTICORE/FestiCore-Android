package io.festicore.service.festivalcatalog.storage

import io.festicore.core.network.FestiCoreJson
import io.festicore.domain.festival.sources.model.dto.FestivalItemDto
import io.festicore.domain.festival.sources.model.dto.FestivalTicketLinkDto
import io.festicore.domain.festival.sources.service.FestivalCatalogServiceError
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FestivalCatalogCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "festicore/festival-catalog.json")
    private val cache by lazy { FestivalCatalogCache(file, FestiCoreJson) }

    @Test
    fun `파일이 없으면 빈 목록이다`() = runTest {
        assertTrue(cache.load().isEmpty())
    }

    @Test
    fun `저장한 목록을 그대로 읽는다`() = runTest {
        val items = listOf(
            FestivalItemDto(
                id = "fst_a",
                name = "가짜 축제",
                thumbUrl = "/a.webp",
                packVersion = 3,
                ticketLinks = listOf(FestivalTicketLinkDto("가", "https://a.test")),
            ),
            FestivalItemDto(id = "fst_b", name = "가짜 축제 2", packVersion = 0),
        )

        cache.save(items)

        assertEquals(items, cache.load())
    }

    @Test
    fun `다시 저장하면 이전 목록을 통째로 바꾼다`() = runTest {
        cache.save(listOf(FestivalItemDto(id = "fst_a", name = "가", packVersion = 1)))
        val next = listOf(FestivalItemDto(id = "fst_b", name = "나", packVersion = 2))

        cache.save(next)

        assertEquals(next, cache.load())
    }

    @Test
    fun `손상된 파일은 CacheUnreadable이다`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText("{not json")

        val error = runCatching { cache.load() }.exceptionOrNull()

        assertTrue(error is FestivalCatalogServiceError.CacheUnreadable)
    }
}
