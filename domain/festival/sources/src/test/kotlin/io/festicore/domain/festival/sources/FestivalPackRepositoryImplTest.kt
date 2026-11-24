package io.festicore.domain.festival.sources
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.error.NetworkError
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackDownloadProgress
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.sources.repository.FestivalPackRepositoryImpl
import io.festicore.domain.festival.sources.repository.toPackError
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import io.festicore.service.pack.PackDownloadEvent as ServiceEvent
import io.festicore.service.pack.PackDownloadProgress as ServiceProgress
import io.festicore.service.pack.PackSyncError
import io.festicore.service.pack.PackSyncResult as ServiceResult
import io.festicore.service.pack.PackSyncService
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalPackRepositoryImplTest {
    private val id = "fst_a"

    private fun pack(version: Int, body: String = """{"id":"fst_a","packVersion":$version,"name":"가짜 축제"}""") =
        ServicePack(id, version, "\"v$version\"", body.encodeToByteArray(), checkedAtMillis = 1_000L)

    @Test
    fun `저장 팩을 읽어 문서를 해석하고 공유 상태를 채운다`() = runTest {
        val repository = FestivalPackRepositoryImpl(FakePackService(cached = listOf(pack(2))), FestiCoreJson)

        val packs = repository.reloadCachedPacks()

        val cached = repository.state.value.packs.getValue(id)
        assertEquals(packs.single(), cached)
        assertEquals(2, cached.packVersion)
        assertEquals("가짜 축제", cached.document?.name)
        assertEquals(Instant.ofEpochMilli(1_000L), cached.checkedAt)
    }

    @Test
    fun `팩 JSON을 문서로 읽지 못하면 문서만 비운다`() = runTest {
        val broken = pack(1, body = """{"id":"fst_a","packVersion":1,"days":"oops"}""")
        val repository = FestivalPackRepositoryImpl(FakePackService(cached = listOf(broken)), FestiCoreJson)

        assertNull(repository.reloadCachedPacks().single().document)
    }

    @Test
    fun `받는 중 진행률을 공유하고 끝나면 받은 팩으로 바꾼다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val service = FakePackService(
            download = flow {
                emit(ServiceEvent.Progress(ServiceProgress(50, 100)))
                gate.await()
                emit(ServiceEvent.Finished(ServiceResult.Updated(pack(3))))
            },
        )
        val repository = FestivalPackRepositoryImpl(service, FestiCoreJson)

        val events = async { repository.downloadPack(id).toList() }
        runCurrent()
        assertEquals(PackDownloadProgress(50, 100), repository.state.value.downloads.getValue(id))
        assertEquals(0.5f, repository.state.value.downloads.getValue(id)?.fraction)

        gate.complete(Unit)
        val result = events.await()

        assertTrue((result.last() as PackDownloadEvent.Finished).result is PackSyncResult.Updated)
        assertTrue(repository.state.value.downloads.isEmpty())
        assertEquals(3, repository.state.value.packs.getValue(id).packVersion)
    }

    @Test
    fun `받기 실패는 도메인 오류로 바꿔 상태에 남기고 다시 받으면 지운다`() = runTest {
        val service = FakePackService(download = flow { throw PackSyncError.InvalidChunk() })
        val repository = FestivalPackRepositoryImpl(service, FestiCoreJson)

        val error = runCatching { repository.downloadPack(id).toList() }.exceptionOrNull()

        assertTrue(error is FestivalPackError.InvalidPack)
        assertTrue(repository.state.value.failures.getValue(id) is FestivalPackError.InvalidPack)
        assertTrue(repository.state.value.downloads.isEmpty())

        service.download = flow { emit(ServiceEvent.Finished(ServiceResult.NotModified(pack(1)))) }
        repository.downloadPack(id).toList()
        assertTrue(repository.state.value.failures.isEmpty())
    }

    @Test
    fun `감시 결과로 받은 팩을 반영하고 끊김은 오프라인으로 바꾼다`() = runTest {
        val service = FakePackService(
            updates = flow {
                emit(ServiceResult.Updated(pack(4)))
                emit(ServiceResult.Reconnecting)
                throw NetworkError.Offline(IOException())
            },
        )
        val repository = FestivalPackRepositoryImpl(service, FestiCoreJson)
        val results = mutableListOf<PackSyncResult>()

        val error = runCatching { repository.packUpdates(id).collect { results += it } }.exceptionOrNull()

        assertEquals(PackSyncResult.Reconnecting, results[1])
        assertTrue(error is FestivalPackError.Offline)
        assertEquals(4, repository.state.value.packs.getValue(id).packVersion)
    }

    @Test
    fun `오류 변환`() {
        assertTrue(PackSyncError.Timeout().toPackError() is FestivalPackError.Offline)
        assertTrue(PackSyncError.PackTooLarge().toPackError() is FestivalPackError.PackTooLarge)
        assertEquals("PACK_BUSY", (PackSyncError.Server("PACK_BUSY").toPackError() as FestivalPackError.Server).code)
        assertTrue(IOException().toPackError() is FestivalPackError.Unavailable)
        assertTrue(PackSyncError.Unconfigured().toPackError() is FestivalPackError.Unavailable)
    }

    @Test
    fun `팩을 지우면 공유 상태에서도 뺀다`() = runTest {
        val repository = FestivalPackRepositoryImpl(FakePackService(cached = listOf(pack(1))), FestiCoreJson)
        repository.reloadCachedPacks()

        repository.deletePack(id)

        assertTrue(repository.state.value.packs.isEmpty())
    }
}

private class FakePackService(
    var cached: List<ServicePack> = emptyList(),
    var download: Flow<ServiceEvent> = flow { },
    var updates: Flow<ServiceResult> = flow { },
) : PackSyncService {
    override suspend fun cachedPack(festivalId: String) = cached.firstOrNull { it.festivalId == festivalId }

    override suspend fun cachedPacks() = cached

    override suspend fun sync(festivalId: String): ServiceResult = error("사용하지 않음")

    override fun download(festivalId: String) = download

    override suspend fun deletePack(festivalId: String) {
        cached = cached.filterNot { it.festivalId == festivalId }
    }

    override fun updates(festivalId: String) = updates
}
