package io.festicore.feature.festival.festivallist

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackDownloadProgress
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.pack.FestivalPackUseCase
import io.festicore.feature.festival.RecordingAnalyticsUseCase
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsPackSyncResult
import io.festicore.service.analytics.event.PackSyncData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `FestivalPackDownloadFeatureTests` 11건의 목록 부분. 팩 감시(WebSocket) · 선택 팩 · 분석 이벤트는 보드(L4)와 분석 모듈 몫이라
 * 목록이 보이는 결과(카드 문구 · 받는 중 · 토스트)만 확인한다.
 */
class FestivalPackDownloadViewModelTest {
    private val analytics = RecordingAnalyticsUseCase()

    private val festival = FestivalCatalogItem(id = "festival", name = "축제", firstDate = "2099-11-21", packVersion = 2)

    private val started =
        FestivalListState(
            viewState = FestivalListViewState.Loaded,
            hasFinishedFirstCatalogRefresh = true,
            hasStarted = true,
            catalog = listOf(festival),
        )

    private fun pack(version: Int, name: String = "축제") = CachedFestivalPack(
        festivalId = "festival",
        packVersion = version,
        etag = "v$version",
        checkedAt = null,
        document = FestivalPackDocument(id = "festival", name = name),
    )

    private fun viewModel(packs: FakePackUseCase, currentUser: FakeCurrentUserProvider = FakeCurrentUserProvider()) =
        FestivalListViewModel(FakeCatalogUseCase(), FakeLibraryUseCase(), currentUser, packs, analytics)

    private fun FestivalListState.packCopy() = FestivalListPresentation.packCopy(festivals.first())

    @Test
    fun firstDownloadWithSessionRetainsOriginalPrepVisitToken() = runTest {
        val received = pack(2)
        val packs = FakePackUseCase(download = { flowOf(PackDownloadEvent.Finished(PackSyncResult.Updated(received))) })
        viewModel(packs, FakeCurrentUserProvider("user")).test(this, initialState = started) {
            containerHost.onDownloadPack("festival", prepToken = 42)
            expectState { copy(packDownloads = mapOf("festival" to null)) }
            expectState { copy(packDownloads = emptyMap(), cachedPacks = mapOf("festival" to received)) }
            expectSideEffect(FestivalListSideEffect.PrepRequested("festival", 42))
        }
    }

    @Test
    fun failedFirstDownloadDoesNotRequestPrep() = runTest {
        val packs = FakePackUseCase(download = { flow { throw FestivalPackError.Offline() } })
        viewModel(packs, FakeCurrentUserProvider("user")).test(this, initialState = started) {
            containerHost.onDownloadPack("festival", prepToken = 42)
            expectState { copy(packDownloads = mapOf("festival" to null)) }
            expectState { copy(packDownloads = emptyMap(), toast = FestivalListToast.PackDownloadFailed, toastRevision = 1) }
            expectNoItems()
        }
    }

    @Test
    fun loggedOutFirstDownloadDoesNotRequestPrep() = runTest {
        val received = pack(2)
        val packs = FakePackUseCase(download = { flowOf(PackDownloadEvent.Finished(PackSyncResult.Updated(received))) })
        viewModel(packs, FakeCurrentUserProvider()).test(this, initialState = started) {
            containerHost.onDownloadPack("festival", prepToken = 42)
            expectState { copy(packDownloads = mapOf("festival" to null)) }
            expectState { copy(packDownloads = emptyMap(), cachedPacks = mapOf("festival" to received)) }
            expectNoItems()
        }
    }

    @Test
    fun updatingExistingPackDoesNotStartPrep() = runTest {
        val received = pack(2)
        val packs = FakePackUseCase(download = { flowOf(PackDownloadEvent.Finished(PackSyncResult.Updated(received))) })
        viewModel(
            packs,
            FakeCurrentUserProvider("user"),
        ).test(this, initialState = started.copy(cachedPacks = mapOf("festival" to pack(1)))) {
            containerHost.onDownloadPack("festival", prepToken = 42)
            awaitState()
            awaitState()
            expectNoItems()
        }
    }

    @Test
    fun `지운 팩을 다시 읽으면 팩 받기로 돌아가고 감시를 시작하지 않는다`() = runTest {
        val packs = FakePackUseCase(cached = { emptyList() })
        val vm = viewModel(packs)
        vm.test(this, initialState = started.copy(cachedPacks = mapOf("festival" to pack(1)))) {
            containerHost.onReloadCachedPacks()
            expectState { copy(cachedPacks = emptyMap()) }
        }
        assertEquals(FestivalListPresentation.PackCopy.Ready("팩 받기"), vm.container.stateFlow.value.packCopy())
        assertEquals(0, packs.watched)
    }

    @Test
    fun `다시 읽은 최신 팩은 받음으로 보인다`() = runTest {
        val packs = FakePackUseCase(cached = { listOf(pack(2)) })
        val vm = viewModel(packs)
        vm.test(this, initialState = started) {
            containerHost.onReloadCachedPacks()
            expectState { copy(cachedPacks = mapOf("festival" to pack(2))) }
        }
        assertEquals(FestivalListPresentation.PackCopy.Received("팩 받음 · 최신 버전"), vm.container.stateFlow.value.packCopy())
    }

    @Test
    fun `선택 팩을 지운 뒤 다시 받으면 받은 팩이 목록에 합쳐진다`() = runTest {
        val downloaded = pack(2, name = "다시 받은 축제")
        val packs =
            FakePackUseCase(
                cached = { emptyList() },
                download = { flowOf(PackDownloadEvent.Finished(PackSyncResult.Updated(downloaded))) },
            )
        val vm = viewModel(packs)
        vm.test(this, initialState = started.copy(cachedPacks = mapOf("festival" to pack(1)))) {
            containerHost.onReloadCachedPacks()
            expectState { copy(cachedPacks = emptyMap()) }

            containerHost.onDownloadPack("festival")
            expectState { copy(packDownloads = mapOf("festival" to null)) }
            expectState { copy(packDownloads = emptyMap(), cachedPacks = mapOf("festival" to downloaded)) }
        }
        assertEquals(FestivalListPresentation.PackCopy.Received("팩 받음 · 최신 버전"), vm.container.stateFlow.value.packCopy())
    }

    @Test
    fun `받기 버튼은 실측 진행률을 보이고 받은 팩을 남긴다`() = runTest {
        val progress = PackDownloadProgress(receivedBytes = 62, totalBytes = 100)
        val packs =
            FakePackUseCase(
                download = {
                    flowOf(PackDownloadEvent.Progress(progress), PackDownloadEvent.Finished(PackSyncResult.Updated(pack(2))))
                },
            )
        viewModel(packs).test(this, initialState = started) {
            containerHost.onDownloadPack("festival")
            expectState { copy(packDownloads = mapOf("festival" to null)) }
            expectState { copy(packDownloads = mapOf("festival" to progress)) }
            assertEquals(
                FestivalListPresentation.PackDownloadDisplay(progress = 0.62, text = "팩 받는 중 · 62%"),
                FestivalListPresentation.packDownloadDisplay(mapOf("festival" to progress), "festival"),
            )
            expectState { copy(packDownloads = emptyMap(), cachedPacks = mapOf("festival" to pack(2))) }
        }
        assertEquals(listOf("festival"), packs.downloaded)
        assertEquals(listOf(AnalyticsEvent.PackSync(PackSyncData("festival", AnalyticsPackSyncResult.Updated))), analytics.events)
    }

    @Test
    fun `발행된 팩은 일정 상태 · 예매 여부와 상관없이 받는다`() = runTest {
        for (status in listOf("ended", "openingSoon", "discovered")) {
            for (isBooked in listOf(false, true)) {
                val item =
                    festival.copy(
                        firstDate = if (status == "ended") "2000-01-01" else "2099-11-21",
                        lastDate = if (status == "ended") "2000-01-02" else "2099-11-22",
                        status = status,
                    )
                val booked = if (isBooked) setOf("festival") else emptySet()
                val packs = FakePackUseCase(download = { flowOf(PackDownloadEvent.Finished(PackSyncResult.Updated(pack(2)))) })
                viewModel(packs).test(this, initialState = started.copy(catalog = listOf(item), bookedFestivalIds = booked)) {
                    containerHost.onDownloadPack("festival")
                    expectState { copy(packDownloads = mapOf("festival" to null)) }
                    expectState { copy(packDownloads = emptyMap(), cachedPacks = mapOf("festival" to pack(2))) }
                }
                assertEquals(listOf("festival"), packs.downloaded)
            }
        }
    }

    @Test
    fun `받은 버전이 발행 버전 이상이면 받지 않는다`() = runTest {
        for (cachedVersion in listOf(2, 3)) {
            val packs = FakePackUseCase()
            val item = festival.copy(firstDate = "2000-01-01", status = "ended")
            viewModel(
                packs,
            ).test(this, initialState = started.copy(catalog = listOf(item), cachedPacks = mapOf("festival" to pack(cachedVersion)))) {
                containerHost.onDownloadPack("festival")
            }
            assertTrue(packs.downloaded.isEmpty())
        }
    }

    @Test
    fun `발행 전 축제는 받지 않는다`() = runTest {
        val packs = FakePackUseCase()
        viewModel(packs).test(this, initialState = started.copy(catalog = listOf(festival.copy(packVersion = 0)))) {
            containerHost.onDownloadPack("festival")
        }
        assertTrue(packs.downloaded.isEmpty())
    }

    @Test
    fun `갱신 알림은 진행률을 지우고 사용자가 바뀐 뒤 이전 받기 이벤트는 버린다`() = runTest {
        val events = MutableSharedFlow<PackDownloadEvent>()
        val currentUser = FakeCurrentUserProvider()
        val packs = FakePackUseCase(download = { events })
        viewModel(packs, currentUser).test(this, initialState = started) {
            val subscription = runOnCreate()
            containerHost.onDownloadPack("festival")
            expectState { copy(packDownloads = mapOf("festival" to null)) }

            events.subscriptionCount.first { it > 0 }
            events.emit(PackDownloadEvent.Progress(PackDownloadProgress(80, 100)))
            expectState { copy(packDownloads = mapOf("festival" to PackDownloadProgress(80, 100))) }
            events.emit(PackDownloadEvent.Reset)
            expectState { copy(packDownloads = mapOf("festival" to null)) }

            currentUser.currentUserId.value = "user-a"
            expectState { copy(userId = "user-a", packDownloads = emptyMap(), accountGeneration = 1) }
            events.emit(PackDownloadEvent.Progress(PackDownloadProgress(90, 100)))
            subscription.cancel()
        }
        assertEquals(0, events.subscriptionCount.value)
    }

    @Test
    fun `포그라운드를 잃기만 하면 받기를 유지하고 배경으로 가면 취소한다`() = runTest {
        val progress = PackDownloadProgress(40, 100)
        viewModel(FakePackUseCase()).test(this, initialState = started.copy(packDownloads = mapOf("festival" to progress))) {
            containerHost.onForegroundChanged(false)
            expectState { copy(isForeground = false) }

            containerHost.onBackgrounded()
            expectState { copy(packDownloads = emptyMap()) }
        }
    }

    @Test
    fun `사용자가 바뀌면 받는 중인 팩을 취소한다`() = runTest {
        val currentUser = FakeCurrentUserProvider("user-a")
        val progress = PackDownloadProgress(40, 100)
        viewModel(FakePackUseCase(), currentUser).test(
            this,
            initialState = started.copy(userId = "user-a", packDownloads = mapOf("festival" to progress)),
        ) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = null
            expectState { copy(userId = null, packDownloads = emptyMap(), accountGeneration = 1) }
            subscription.cancel()
        }
    }

    @Test
    fun `같은 사용자로 최근 검색을 다시 읽어도 받기를 취소하지 않는다`() = runTest {
        val progress = PackDownloadProgress(40, 100)
        val vm = viewModel(FakePackUseCase(), FakeCurrentUserProvider("user-a"))
        vm.test(this, initialState = started.copy(userId = "user-a", packDownloads = mapOf("festival" to progress))) {
            val subscription = runOnCreate()
            containerHost.onAppear()
            subscription.cancel()
        }
        assertEquals(mapOf("festival" to progress), vm.container.stateFlow.value.packDownloads)
    }

    @Test
    fun `받기가 실패하면 실패 토스트를 보인다`() = runTest {
        val packs = FakePackUseCase(download = { flow { throw FestivalPackError.Offline() } })
        viewModel(packs).test(this, initialState = started) {
            containerHost.onDownloadPack("festival")
            expectState { copy(packDownloads = mapOf("festival" to null)) }
            expectState { copy(packDownloads = emptyMap(), toast = FestivalListToast.PackDownloadFailed, toastRevision = 1) }
        }
        assertEquals(listOf(AnalyticsEvent.PackSync(PackSyncData("festival", AnalyticsPackSyncResult.Failed))), analytics.events)
    }

    @Test
    fun `카탈로그에 없는 받은 팩은 이름순으로 뒤에 붙는다`() {
        val other = CachedFestivalPack("b", 1, "v1", null, FestivalPackDocument(id = "b", name = "나 축제"))
        val another = CachedFestivalPack("a", 1, "v1", null, FestivalPackDocument(id = "a", name = "가 축제"))
        val broken = CachedFestivalPack("c", 1, "v1", null, null)
        val festivals =
            FestivalListPresentation.festivals(
                listOf(festival),
                mapOf("festival" to pack(1), "b" to other, "a" to another, "c" to broken),
            )
        assertEquals(listOf("festival", "a", "b"), festivals.map { it.id })
        assertEquals(1, festivals.first().cachedPackVersion)
        assertEquals(FestivalListPresentation.PackCopy.Ready("팩 업데이트"), FestivalListPresentation.packCopy(festivals.first()))
    }
}

internal class FakePackUseCase(
    private val cached: suspend () -> List<CachedFestivalPack> = { emptyList() },
    private val download: (String) -> Flow<PackDownloadEvent> = { emptyFlow() },
) : FestivalPackUseCase {
    override val state = MutableStateFlow(FestivalPackState())
    val downloaded = mutableListOf<String>()
    var watched = 0

    override suspend fun reloadCachedPacks(): List<CachedFestivalPack> = cached()

    override fun packUpdates(festivalId: String): Flow<PackSyncResult> {
        watched += 1
        return emptyFlow()
    }

    override fun downloadPack(festivalId: String): Flow<PackDownloadEvent> {
        downloaded += festivalId
        return download(festivalId)
    }

    override suspend fun syncPack(festivalId: String): PackSyncResult = error("사용하지 않음")

    override suspend fun deleteExpiredPacks(): List<String> = error("사용하지 않음")
}
