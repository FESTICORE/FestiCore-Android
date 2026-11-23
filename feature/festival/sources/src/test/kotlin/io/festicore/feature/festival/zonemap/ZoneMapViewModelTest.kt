package io.festicore.feature.festival.zonemap

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalLiveBoardError
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalZoneMap
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import io.festicore.domain.festival.zonemap.FestivalZoneMapUseCase
import io.festicore.feature.festival.festivallist.FakeCurrentUserProvider
import io.festicore.feature.festival.liveboard.FakeBoardPackUseCase
import io.festicore.feature.festival.liveboard.FakeLiveBoardUseCase
import io.festicore.feature.festival.liveboard.MutableClock
import io.festicore.feature.festival.liveboard.TestSleeper
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.Item
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

/** iOS `FestivalZoneMapFeatureTests` 6건(같은 이름) + 보드 마지막 스냅샷으로 첫 화면 채우기(설계 E3). */
class ZoneMapViewModelTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val clock = MutableClock(now)
    private val sleeper = TestSleeper()

    private fun viewModel(
        zoneMap: FakeZoneMapUseCase = FakeZoneMapUseCase(),
        liveBoard: FakeLiveBoardUseCase = FakeLiveBoardUseCase(),
        packs: FakeBoardPackUseCase = FakeBoardPackUseCase(),
        viewerUserId: String? = null,
        selectedZoneId: String? = null,
    ) = ZoneMapViewModel(
        festivalId = "fst_a",
        selectedZoneId = selectedZoneId,
        zoneMapUseCase = zoneMap,
        liveBoardUseCase = liveBoard,
        packUseCase = packs,
        currentUserProvider = FakeCurrentUserProvider(viewerUserId),
        clock = clock,
        sleep = sleeper::sleep,
    )

    private fun emptySnapshot(receivedAt: Instant? = now) = FestivalLiveBoardSnapshot(
        festivalId = "fst_a",
        activeFlags = emptyList(),
        followedFlags = emptyList(),
        crew = null,
        receivedAt = receivedAt,
    )

    @Test
    fun appearLoadsMapAndLiveBoardThenFlagsChangedMap() = runTest {
        val map = sampleMap(version = "zm-old")
        val snapshot = emptySnapshot()
        val zoneMap = FakeZoneMapUseCase(cachedMap = { map }, isMapChanged = { it.version != "zm-new" })
        viewModel(zoneMap, FakeLiveBoardUseCase(liveBoard = { snapshot }), viewerUserId = "user_a", selectedZoneId = "zon_a").test(this) {
            containerHost.onAppear()
            val loaded = awaitZoneMap { it.loadedMap == map && it.snapshot == snapshot && it.isMapChanged }
            assertEquals(now, loaded.receivedAt)
            assertEquals("zon_a", loaded.selectedZoneId)
            assertEquals("user_a", loaded.viewerUserId)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun watchingTheLiveBoardRefreshesPositionsEveryInterval() = runTest {
        var received = now
        val liveBoard =
            FakeLiveBoardUseCase(
                liveBoard = {
                    received = received.plusSeconds(35)
                    emptySnapshot(receivedAt = received)
                },
            )
        viewModel(liveBoard = liveBoard).test(this) {
            containerHost.onAppear()
            awaitZoneMap { it.snapshot != null }
            assertEquals(1, liveBoard.liveBoardCalls.size)
            assertEquals(listOf(FestivalLiveBoardUseCase.POLL_INTERVAL), sleeper.requested)

            sleeper.release(FestivalLiveBoardUseCase.POLL_INTERVAL)
            awaitZoneMap { liveBoard.liveBoardCalls.size == 2 }
            // 화면이 빠지면 감시를 멈춘다.
            containerHost.onDisappear()
            awaitZoneMap { !it.isVisible }
            sleeper.release(FestivalLiveBoardUseCase.POLL_INTERVAL)
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(2, liveBoard.liveBoardCalls.size)
    }

    @Test
    fun tappingZonesTogglesTheCardAndBackgroundClearsIt() = runTest {
        viewModel().test(this) {
            containerHost.onZoneTapped("zon_a")
            expectState { copy(selectedZoneId = "zon_a") }
            containerHost.onZoneTapped("zon_b")
            expectState { copy(selectedZoneId = "zon_b") }
            containerHost.onZoneTapped("zon_b")
            expectState { copy(selectedZoneId = null) }
            containerHost.onZoneTapped("zon_a")
            expectState { copy(selectedZoneId = "zon_a") }
            containerHost.onMapBackgroundTapped()
            expectState { copy(selectedZoneId = null) }
        }
    }

    @Test
    fun liveBoardFailureShowsOfflineAndKeepsLastSnapshot() = runTest {
        val snapshot = emptySnapshot()
        val answers =
            ArrayDeque<suspend () -> FestivalLiveBoardSnapshot>(listOf({ snapshot }, { throw FestivalLiveBoardError.InvalidResponse() }))
        val liveBoard = FakeLiveBoardUseCase(liveBoard = { answers.removeFirst()() })
        viewModel(liveBoard = liveBoard).test(this) {
            containerHost.onAppear()
            awaitZoneMap { it.snapshot == snapshot }
            sleeper.release(FestivalLiveBoardUseCase.POLL_INTERVAL)
            val offline = awaitZoneMap { it.isOffline }
            assertEquals(snapshot, offline.snapshot)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun updatingThePackReloadsTheMapAndClearsTheBanner() = runTest {
        val updated = mutableListOf<String>()
        val packs =
            FakeBoardPackUseCase(
                sync = {
                    updated += it
                    PackSyncResult.Updated(CachedFestivalPack("fst_a", 10, "\"v10\"", null, null))
                },
            )
        val zoneMap = FakeZoneMapUseCase(cachedMap = { sampleMap(version = "zm-new") }, isMapChanged = { false })
        val initial =
            ZoneMapState(
                festivalId = "fst_a",
                viewerUserId = null,
                map = ZoneMapLoad.Loaded(sampleMap(version = "zm-old")),
                isMapChanged = true,
            )
        viewModel(zoneMap, packs = packs).test(this, initialState = initial) {
            containerHost.onUpdatePackTapped()
            expectState { copy(isUpdatingPack = true) }
            expectState { copy(isUpdatingPack = false, isMapChanged = false) }
            expectState { copy(map = ZoneMapLoad.Loaded(sampleMap(version = "zm-new"))) }
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(listOf("fst_a"), updated)
    }

    @Test
    fun modelPlacesFollowedFlagsCrewAndOtherFlagsAndBuildsTheCard() {
        val map = sampleMap(version = "zm-1")
        val followed = flag("flg_followed", "호락호락", "active", "slam", now.minusSeconds(240))
        val snapshot =
            FestivalLiveBoardSnapshot(
                festivalId = "fst_a",
                activeFlags = listOf(followed, flag("flg_other", "빨간바지", "resting", "calm", now.minusSeconds(60))),
                followedFlags = listOf(followed.copy(updatedAt = null)),
                crew =
                    FestivalLiveBoardSnapshot.Crew(
                        id = "crew",
                        name = "크루",
                        members =
                            listOf(
                                FestivalLiveBoardSnapshot.Crew.Member("user_a", "로이", "here", "zon_a", now),
                                FestivalLiveBoardSnapshot.Crew.Member("user_b", "한솔", "leaving", "zon_b", now.minusSeconds(3_600)),
                            ),
                    ),
            )

        val model = ZoneMapModel.of(map, snapshot, now)
        assertEquals(listOf("flg_followed", "user_a", "flg_other"), model.markers["zon_a"]?.map { it.id })
        assertTrue(model.markers["zon_b"]?.first()?.isStale == true)
        assertNull(model.bottomBanner)
        assertEquals("깃발 2 · 크루 1", model.countText("zon_a"))
        assertEquals("–", model.countText("zon_c"))

        val card = ZoneMapModel.card(map.zones[0], map, snapshot, "user_a", now)
        assertEquals("무대 앞 · 깃발 2 · 크루 1", card.meta)
        assertEquals(listOf("팔로우 중 · 슬램", "차분"), card.flags.map { it.meta })
        assertEquals(listOf(FCStatusChipStatus.Active, FCStatusChipStatus.Resting), card.flags.map { it.status })
        assertTrue(card.crew.first().isMe)

        val empty = ZoneMapModel.card(map.zones[2], map, snapshot, "user_a", now)
        assertTrue(empty.isEmpty)
        assertEquals("무대 앞 · 지금 아무도 없어요", empty.meta)

        val quiet = ZoneMapModel.of(map, null, now)
        assertEquals(ZoneMapModel.BottomBanner.FollowGuide, quiet.bottomBanner)
        assertEquals(ZoneMapModel.BottomBanner.FlagsDisabled, ZoneMapModel.of(map.copy(flagsEnabled = false), snapshot, now).bottomBanner)
    }

    @Test
    fun `보드의 마지막 라이브 상태로 먼저 그리고 새 조회로 바꾼다`() = runTest {
        val cached = emptySnapshot(receivedAt = now.minusSeconds(20))
        val fresh = emptySnapshot(receivedAt = now)
        val liveBoard = FakeLiveBoardUseCase(liveBoard = { fresh }, cached = { cached })
        viewModel(liveBoard = liveBoard).test(this) {
            containerHost.onAppear()
            val first = awaitZoneMap { it.snapshot != null }
            assertEquals(cached, first.snapshot)
            assertEquals(now.minusSeconds(20), first.receivedAt)
            awaitZoneMap { it.snapshot == fresh && it.receivedAt == now }
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `보드 캐시가 없으면 마커 없이 그리고 조회 결과로 채운다 · 팩이 없으면 안내`() = runTest {
        val fresh = emptySnapshot()
        val zoneMap = FakeZoneMapUseCase(cachedMap = { null })
        viewModel(zoneMap, FakeLiveBoardUseCase(liveBoard = { fresh })).test(this) {
            containerHost.onAppear()
            val missing = awaitZoneMap { it.map == ZoneMapLoad.Missing }
            assertTrue(missing.snapshot == null || missing.snapshot == fresh)
            awaitZoneMap { it.snapshot == fresh }
            cancelAndIgnoreRemainingItems()
        }
        assertFalse(zoneMap.versionChecked)
    }

    @Test
    fun `429면 보이던 상태를 두고 35초와 Retry-After 중 긴 쪽을 기다린다 · 지도 실패는 다시 시도로 다시 읽는다`() = runTest {
        val liveBoard = FakeLiveBoardUseCase(liveBoard = { throw FestivalLiveBoardError.RateLimited(90.seconds) })
        var fail = true
        val zoneMap = FakeZoneMapUseCase(cachedMap = { if (fail) error("읽기 실패") else sampleMap("zm-1") })
        viewModel(zoneMap, liveBoard).test(this) {
            containerHost.onAppear()
            val failed = awaitZoneMap { it.map == ZoneMapLoad.Failed }
            assertFalse(failed.isOffline)
            fail = false
            containerHost.onRetryTapped()
            awaitZoneMap { it.loadedMap != null }
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(listOf(90.seconds), sleeper.requested)
    }
}

private typealias ZoneMapTest = OrbitTestContext<ZoneMapState, ZoneMapSideEffect, ZoneMapViewModel>

/** [predicate]를 만족하는 State가 올 때까지 앞선 항목을 건너뛴다. */
private suspend fun ZoneMapTest.awaitZoneMap(predicate: (ZoneMapState) -> Boolean): ZoneMapState {
    while (true) {
        val item = awaitItem()
        if (item is Item.StateItem && predicate(item.value)) return item.value
    }
}

internal class FakeZoneMapUseCase(
    var cachedMap: suspend (String) -> FestivalZoneMap? = { sampleMap("zm-1") },
    var isMapChanged: suspend (FestivalZoneMap) -> Boolean = { false },
) : FestivalZoneMapUseCase {
    var versionChecked = false

    override suspend fun cachedMap(festivalId: String, now: Instant): FestivalZoneMap? = cachedMap.invoke(festivalId)

    override suspend fun isMapChanged(map: FestivalZoneMap): Boolean {
        versionChecked = true
        return isMapChanged.invoke(map)
    }
}

private fun flag(
    id: String,
    name: String,
    status: String,
    activity: String,
    updatedAt: Instant,
) = FestivalLiveBoardSnapshot.Flag(
    id = id,
    name = name,
    status = status,
    zoneId = "zon_a",
    activity = activity,
    thumbnailUrl = null,
    updatedAt = updatedAt,
)

internal fun sampleMap(version: String): FestivalZoneMap {
    val area = FestivalZoneMap.Area(0.1, 0.1, 0.4, 0.2)
    return FestivalZoneMap(
        festivalId = "fst_a",
        version = version,
        imageUrl = null,
        aspect = 0.655,
        flagsEnabled = true,
        zones =
            listOf(
                FestivalZoneMap.Zone("zon_a", "리벳 스테이지 앞 왼쪽", "무대 앞", area, null),
                FestivalZoneMap.Zone("zon_b", "정문", "출입구", area, "GATE"),
                FestivalZoneMap.Zone("zon_c", "TENT 앞", "무대 앞", area, "TENT 무대"),
            ),
        stageHeaders = emptyList(),
    )
}
