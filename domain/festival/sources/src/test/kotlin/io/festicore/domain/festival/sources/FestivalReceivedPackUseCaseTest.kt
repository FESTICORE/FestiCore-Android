package io.festicore.domain.festival.sources

import io.festicore.core.network.error.NetworkError
import io.festicore.domain.festival.receivedpack.FestivalWiFiStatus
import io.festicore.domain.festival.sources.receivedpack.FestivalReceivedPackUseCaseImpl
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalReceivedPackUseCaseTests` 8개(같은 이름 뜻). 매개변수 테스트 `refreshKeepsUpdatesOffWiFiOrWhenAutoDownloadIsOff`는 두 개로 나눴다. */
class FestivalReceivedPackUseCaseTest {
    private fun useCase(
        service: MutablePackService,
        catalog: FakeCatalogRepository,
        autoDownload: Boolean = false,
        onWiFi: Boolean = false,
    ) = FestivalReceivedPackUseCaseImpl(service.repository(), catalog, FakePackLocalStore(autoDownload), FestivalWiFiStatus { onWiFi })

    @Test
    fun `받은 팩에 저장된 목록을 합치고 최근 확인 순으로 둔다`() = runTest {
        val service = MutablePackService(
            listOf(
                namedPack("fst_a", 12, "팩 이름 A", checkedAtMillis = 100),
                namedPack("fst_b", 3, "팩 이름 B", checkedAtMillis = 200),
            ),
        )
        val packs = useCase(service, FakeCatalogRepository(cached = listOf(catalogItem("fst_a", "원더리벳 2026", 13)))).receivedPacks()

        assertEquals(listOf("fst_b", "fst_a"), packs.map { it.id })
        assertEquals("팩 이름 B", packs[0].name)
        assertNull(packs[0].latestPackVersion)
        assertFalse(packs[0].hasUpdate)
        assertEquals("원더리벳 2026", packs[1].name)
        assertEquals(12, packs[1].packVersion)
        assertTrue(packs[1].hasUpdate)
        assertEquals(Instant.ofEpochMilli(100), packs[1].checkedAt)
        assertTrue(packs[1].sizeBytes > 0)
    }

    @Test
    fun `갱신은 서버 목록으로 새 버전을 판단한다`() = runTest {
        val service = MutablePackService(listOf(namedPack("fst_a", 13, "A")))
        val catalog = FakeCatalogRepository(
            cached = listOf(catalogItem("fst_a", "A", 13)),
            refreshed = listOf(catalogItem("fst_a", "A", 14)),
        )
        val useCase = useCase(service, catalog)

        assertEquals(false, useCase.receivedPacks().first().hasUpdate)
        assertEquals(14, useCase.refreshReceivedPacks().first().latestPackVersion)
    }

    @Test
    fun `저장 목록이 깨져도 받은 팩은 보인다`() = runTest {
        val service = MutablePackService(listOf(namedPack("fst_a", 1, null)))

        val packs = useCase(service, FakeCatalogRepository(cached = null)).receivedPacks()

        assertEquals(listOf("fst_a"), packs.map { it.name })
    }

    @Test
    fun `자동 받기가 켜져 있고 와이파이면 새 버전을 받는다`() = runTest {
        val service = MutablePackService(listOf(namedPack("fst_a", 1, "A"))).apply {
            onSync = { id -> listOf(namedPack(id, 2, "A")) }
        }
        val catalog = FakeCatalogRepository(refreshed = listOf(catalogItem("fst_a", "A", 2)))

        val packs = useCase(service, catalog, autoDownload = true, onWiFi = true).refreshReceivedPacks()

        assertEquals(listOf("fst_a"), service.synced)
        assertEquals(2, packs.first().packVersion)
        assertFalse(packs.first().hasUpdate)
    }

    @Test
    fun `받지 못한 팩은 자동 받기 실패로 표시한다`() = runTest {
        val service = MutablePackService(listOf(namedPack("fst_a", 1, "A"))).apply {
            syncError = NetworkError.Offline(IOException("연결 없음"))
        }
        val catalog = FakeCatalogRepository(refreshed = listOf(catalogItem("fst_a", "A", 2)))

        val packs = useCase(service, catalog, autoDownload = true, onWiFi = true).refreshReceivedPacks()

        assertEquals(listOf("fst_a"), service.synced)
        assertTrue(packs.first().hasUpdate)
        assertTrue(packs.first().autoUpdateFailed)
    }

    @Test
    fun `목록을 읽은 뒤 지운 팩은 다시 받지 않는다`() = runTest {
        val service = MutablePackService(listOf(namedPack("fst_a", 1, "A"))).apply { deletedBeforeSync = setOf("fst_a") }
        val catalog = FakeCatalogRepository(refreshed = listOf(catalogItem("fst_a", "A", 2)))

        useCase(service, catalog, autoDownload = true, onWiFi = true).refreshReceivedPacks()

        assertTrue(service.synced.isEmpty())
    }

    @Test
    fun `자동 받기 중 취소는 그대로 전한다`() = runTest {
        val service = MutablePackService(listOf(namedPack("fst_a", 1, "A"), namedPack("fst_b", 1, "B"))).apply {
            syncError = CancellationException("취소")
        }
        val catalog = FakeCatalogRepository(refreshed = listOf(catalogItem("fst_a", "A", 2), catalogItem("fst_b", "B", 2)))

        val error = try {
            useCase(service, catalog, autoDownload = true, onWiFi = true).refreshReceivedPacks()
            null
        } catch (e: CancellationException) {
            e
        }

        assertTrue(error is CancellationException)
        assertEquals(1, service.synced.size)
    }

    @Test
    fun `와이파이가 아니면 받지 않는다`() = runTest {
        assertNoAutoDownload(autoDownload = true, onWiFi = false)
    }

    @Test
    fun `자동 받기가 꺼져 있으면 받지 않는다`() = runTest {
        assertNoAutoDownload(autoDownload = false, onWiFi = true)
    }

    private suspend fun assertNoAutoDownload(autoDownload: Boolean, onWiFi: Boolean) {
        val service = MutablePackService(listOf(namedPack("fst_a", 1, "A")))
        val catalog = FakeCatalogRepository(refreshed = listOf(catalogItem("fst_a", "A", 2)))

        val packs = useCase(service, catalog, autoDownload, onWiFi).refreshReceivedPacks()

        assertTrue(packs.first().hasUpdate)
        assertTrue(service.synced.isEmpty())
    }
}
