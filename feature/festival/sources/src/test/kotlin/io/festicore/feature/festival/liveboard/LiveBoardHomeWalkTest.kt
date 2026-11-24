package io.festicore.feature.festival.liveboard

import io.festicore.core.location.entity.LocationAuthorization
import io.festicore.core.location.entity.LocationClientError
import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingDuration
import io.festicore.core.location.entity.WalkingPlace
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.feature.festival.RecordingAnalyticsUseCase
import io.festicore.feature.festival.festivallist.FakeCatalogUseCase
import io.festicore.feature.festival.festivallist.FakeCurrentUserProvider
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.date
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** 귀가 카드의 위치 권한 · 도보 시간 · "가는 길" 연결. iOS `FestivalHomeWalkTests`. */
class LiveBoardHomeWalkTest {
    private val clock = MutableClock(at("23:00"))

    private fun viewModel(
        location: FakeLocationClient = FakeLocationClient(),
        maps: FakeMapsClient = FakeMapsClient(),
        packs: FakeBoardPackUseCase = FakeBoardPackUseCase(),
    ) = LiveBoardViewModel(
        festivalId = FESTIVAL_ID,
        initialCatalog = listOf(item),
        liveBoardUseCase = FakeLiveBoardUseCase(),
        packUseCase = packs,
        catalogUseCase = FakeCatalogUseCase(),
        scheduleUseCase = FakeScheduleUseCase(),
        preferenceUseCase = FakePreferenceUseCase(),
        currentUserProvider = FakeCurrentUserProvider(),
        notificationBadgeUseCase = FakeNotificationBadgeUseCase(),
        boardSelectionUseCase = FakeBoardSelectionUseCase(),
        analyticsUseCase = RecordingAnalyticsUseCase(),
        locationClient = location,
        mapsClient = maps,
        clock = clock,
        sleep = TestSleeper()::sleep,
    )

    // MARK: - 권한 상태별 도보 시간 계산

    @Test
    fun `위치 권한이 있으면 현재 위치에서 역 · 셔틀 정류장까지 도보 분을 계산한다`() = runTest {
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { origin })
        viewModel(location, routeClient()).test(this, initialState = homeState()) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            expectState { copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = 8, shuttleStop = 3))) }
        }
        assertTrue(location.unexpected.isEmpty())
    }

    @Test
    fun `권한을 아직 정하지 않았으면 사용 중 권한을 요청하고 허용되면 계산한다`() = runTest {
        val location =
            FakeLocationClient(
                authorization = { LocationAuthorization.NotDetermined },
                requestWhenInUseAuthorization = { LocationAuthorization.Authorized },
                currentLocation = { origin },
            )
        viewModel(location, routeClient()).test(this, initialState = homeState()) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            expectState { copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = 8, shuttleStop = 3))) }
        }
        assertEquals(1, location.requestCount)
    }

    @Test
    fun `권한 요청을 거부하면 위치를 읽지 않고 서버 값으로 둔다`() = runTest {
        // 현재 위치 · 지도 대역을 넘기지 않아 호출되면 unexpected에 남는다.
        val location =
            FakeLocationClient(
                authorization = { LocationAuthorization.NotDetermined },
                requestWhenInUseAuthorization = { LocationAuthorization.Denied },
            )
        val maps = FakeMapsClient()
        viewModel(location, maps).test(this, initialState = homeState()) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            expectState { copy(homeWalk = HomeWalkState.Unavailable(FESTIVAL_ID)) }
        }
        assertTrue(location.unexpected.isEmpty())
        assertTrue(maps.unexpected.isEmpty())
    }

    @Test
    fun `거부 · 제한 상태에서는 권한을 다시 묻지 않고 서버 값으로 둔다`() = runTest {
        listOf(LocationAuthorization.Denied, LocationAuthorization.Restricted).forEach { authorization ->
            val location = FakeLocationClient(authorization = { authorization })
            viewModel(location).test(this, initialState = homeState()) {
                containerHost.onHomeRideAppeared()
                expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
                expectState { copy(homeWalk = HomeWalkState.Unavailable(FESTIVAL_ID)) }
            }
            assertEquals(0, location.requestCount)
            assertTrue(location.unexpected.isEmpty())
        }
    }

    @Test
    fun `현재 위치를 잡지 못하면 서버 값으로 둔다`() = runTest {
        val location =
            FakeLocationClient(
                authorization = { LocationAuthorization.Authorized },
                currentLocation = { throw LocationClientError.LocationUnavailable },
            )
        viewModel(location).test(this, initialState = homeState()) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            expectState { copy(homeWalk = HomeWalkState.Unavailable(FESTIVAL_ID)) }
        }
    }

    @Test
    fun `역을 찾지 못해도 정류장 도보 분은 남기고 역은 서버 값으로 보인다`() = runTest {
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { origin })
        val maps =
            FakeMapsClient(walkingDuration = { _, place ->
                if (place.name != SHUTTLE_STOP_NAME) throw LocationClientError.PlaceNotFound
                WalkingDuration(150.0)
            })
        viewModel(location, maps).test(this, initialState = homeState()) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            expectState { copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = null, shuttleStop = 3))) }
            assertEquals(HomeWalkMinutes(station = 12, shuttleStop = 3), containerHost.container.stateFlow.value.homeWalkMinutes)
        }
    }

    @Test
    fun `도보 · 자동차 계산이 모두 실패하면 서버 역 도보 분을 보이고 정류장은 비운다`() = runTest {
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { origin })
        val maps = FakeMapsClient(walkingDuration = { _, _ -> throw LocationClientError.RouteUnavailable })
        viewModel(location, maps).test(this, initialState = homeState()) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            expectState { copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes())) }
            assertEquals(HomeWalkMinutes(station = 12, shuttleStop = null), containerHost.container.stateFlow.value.homeWalkMinutes)
        }
    }

    @Test
    fun `서버가 준 역 좌표가 있으면 그 좌표를 목적지로 넘기고 이름 검색 기준은 축제 장소다`() {
        val places = homeState(directions(stationLocation = FestivalPackDetail.Coordinate(37.3836, 127.1268))).homeWalkPlaces
        assertEquals(WalkingPlace(STATION_NAME, LocationCoordinate(37.3836, 127.1268), venueCoordinate), places.station)
        assertEquals(WalkingPlace(SHUTTLE_STOP_NAME, null, venueCoordinate), places.shuttleStop)
    }

    @Test
    fun `계산 중에 카드가 다시 보여도 새로 계산하지 않는다`() = runTest {
        val state = homeState().copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null))
        viewModel().test(this, initialState = state) {
            containerHost.onHomeRideAppeared()
            expectNoItems()
        }
    }

    @Test
    fun `다시 계산하는 동안에는 직전 도보 분을 계속 보인다`() = runTest {
        val last = HomeWalkMinutes(station = 5, shuttleStop = 2)
        val state = homeState().copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, last))
        val location = FakeLocationClient(authorization = { LocationAuthorization.Denied })
        viewModel(location).test(this, initialState = state) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = last)) }
            expectState { copy(homeWalk = HomeWalkState.Unavailable(FESTIVAL_ID)) }
        }
        assertEquals(last, state.copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last)).homeWalkMinutes)
    }

    @Test
    fun `라이브 보드를 떠나면 계산을 멈추고 직전 결과로 되돌려 다시 보일 때 새로 계산한다`() = runTest {
        val last = HomeWalkMinutes(station = 5, shuttleStop = 2)
        val state = homeState().copy(isVisible = true, homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last))
        viewModel().test(this, initialState = state) {
            containerHost.onDisappear()
            expectState { copy(isVisible = false, homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, last)) }
        }
    }

    @Test
    fun `직전 결과 없이 계산하다 떠나면 처음 상태로 둔다`() = runTest {
        val state = homeState().copy(isVisible = true, homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null))
        viewModel().test(this, initialState = state) {
            containerHost.onDisappear()
            expectState { copy(isVisible = false, homeWalk = HomeWalkState.Idle) }
        }
    }

    @Test
    fun `떠나면 진행 중인 위치 요청을 취소한다`() = runTest {
        val gate = CompletableDeferred<LocationCoordinate>()
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { gate.await() })
        viewModel(location, routeClient()).test(this, initialState = homeState().copy(isVisible = true)) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            containerHost.onDisappear()
            expectState { copy(isVisible = false, homeWalk = HomeWalkState.Idle) }
            gate.complete(origin)
            expectNoItems()
        }
    }

    @Test
    fun `역 · 정류장 이름이 없으면 위치 권한을 묻지 않는다`() = runTest {
        val location = FakeLocationClient()
        viewModel(location).test(this, initialState = homeState(FestivalPackDocument.Directions(lastTrainAt = "23:52"))) {
            containerHost.onHomeRideAppeared()
            expectNoItems()
        }
        assertTrue(location.unexpected.isEmpty())
    }

    @Test
    fun `이전 축제의 도보 계산 결과는 현재 카드를 바꾸지 않는다`() = runTest {
        val gate = CompletableDeferred<LocationCoordinate>()
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { gate.await() })
        val other = FestivalCatalogItem(id = "fst_b", name = "다른 축제", packVersion = 1)
        val packs = FakeBoardPackUseCase(
            FestivalPackState(
                packs = mapOf(
                    FESTIVAL_ID to pack(directions()),
                    "fst_b" to pack(directions(), "fst_b"),
                ),
            ),
        )
        val state = homeState().let { it.copy(catalog = it.catalog + other, cachedPacks = packs.state.value.packs) }
        viewModel(location, routeClient(), packs).test(this, initialState = state) {
            containerHost.onHomeRideAppeared()
            awaitBoard { it.homeWalk is HomeWalkState.Estimating }
            containerHost.onNotificationFestivalRequested("fst_b")
            awaitBoard { it.festivalId == "fst_b" && it.homeWalk == HomeWalkState.Idle }
            gate.complete(origin)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `자동차 시간도 계산되면 도보와 함께 둔다`() = runTest {
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { origin })
        val maps =
            FakeMapsClient(
                walkingDuration = { _, place -> if (place.name == STATION_NAME) WalkingDuration(430.0) else WalkingDuration(150.0) },
                drivingDuration = { _, place ->
                    if (place.name ==
                        STATION_NAME
                    ) {
                        WalkingDuration(200.0)
                    } else {
                        throw LocationClientError.RouteUnavailable
                    }
                },
            )
        viewModel(location, maps).test(this, initialState = homeState()) {
            containerHost.onHomeRideAppeared()
            expectState { copy(homeWalk = HomeWalkState.Estimating(FESTIVAL_ID, last = null)) }
            expectState {
                copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = 8, shuttleStop = 3, stationDriving = 4)))
            }
        }
    }

    @Test
    fun `같은 축제 팩이 바뀌어 목적지가 달라지면 이전 계산을 취소하고 새 목적지로 다시 계산한다`() = runTest {
        val oldPlaceGate = CompletableDeferred<WalkingDuration>()
        val asked = mutableListOf<String>()
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { origin })
        val maps =
            FakeMapsClient(walkingDuration = { _, place ->
                asked += place.name
                if (place.name == STATION_NAME) oldPlaceGate.await() else WalkingDuration(300.0)
            })
        val packs = FakeBoardPackUseCase(FestivalPackState(packs = mapOf(FESTIVAL_ID to pack(directions()))))
        val state = homeState().copy(isVisible = true, hasStarted = true)
        viewModel(location, maps, packs).test(this, initialState = state) {
            runOnCreate()
            containerHost.onHomeRideAppeared()
            awaitBoard { it.homeWalk == HomeWalkState.Estimating(FESTIVAL_ID, last = null) }
            testScheduler.runCurrent()
            assertTrue(STATION_NAME in asked)
            // 서버가 역 이름 · 좌표를 고친 새 팩(같은 축제)
            val moved = directions(stationLocation = FestivalPackDetail.Coordinate(37.3800, 127.1300)).copy(transitStationName = "야탑역")
            packs.state.value = FestivalPackState(packs = mapOf(FESTIVAL_ID to pack(moved).copy(etag = "v2")))
            val updated = awaitBoard { it.homeWalk is HomeWalkState.Estimated }
            assertEquals(HomeWalkMinutes(station = 5, shuttleStop = 5), (updated.homeWalk as HomeWalkState.Estimated).minutes)
            assertEquals("야탑역", updated.homeWalkPlaces.station?.name)
            // 취소된 옛 계산의 결과가 늦게 와도 바꾸지 않는다.
            oldPlaceGate.complete(WalkingDuration(60.0))
            cancelAndIgnoreRemainingItems()
            assertEquals(updated.homeWalk, containerHost.container.stateFlow.value.homeWalk)
        }
        assertTrue("야탑역" in asked)
    }

    @Test
    fun `팩이 바뀌어도 목적지가 같으면 다시 계산하지 않는다`() = runTest {
        val location = FakeLocationClient(authorization = { LocationAuthorization.Authorized }, currentLocation = { origin })
        var calls = 0
        val maps = FakeMapsClient(walkingDuration = { _, _ ->
            calls++
            WalkingDuration(300.0)
        })
        val packs = FakeBoardPackUseCase(FestivalPackState(packs = mapOf(FESTIVAL_ID to pack(directions()))))
        viewModel(location, maps, packs).test(this, initialState = homeState().copy(isVisible = true, hasStarted = true)) {
            runOnCreate()
            containerHost.onHomeRideAppeared()
            awaitBoard { it.homeWalk is HomeWalkState.Estimated }
            packs.state.value =
                FestivalPackState(
                    packs = mapOf(FESTIVAL_ID to pack(directions(lastShuttleAt = "2026-11-08T23:45:00+09:00")).copy(etag = "v2")),
                )
            awaitBoard { it.homeDirections?.lastShuttleAt == "2026-11-08T23:45:00+09:00" }
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(2, calls)
    }

    // MARK: - 카드 문구와 출발 판단

    @Test
    fun `자동차 분이 있으면 도보 옆에 차로 분을 붙이고 출발편 판단은 도보로 한다`() {
        val minutes = HomeWalkMinutes(station = 8, shuttleStop = 3, stationDriving = 4, shuttleStopDriving = 2)
        val ride = LiveBoardScreenModel(homeState().copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, minutes))).homeRide(at("23:00"))
        assertEquals("막차까지 52분", ride?.remain)
        assertEquals("마지막 셔틀 23:40 · 정문 셔틀 정류장 (도보 3분 · 차로 2분)", ride?.shuttle)
        assertEquals("막차 23:52 · 서현역 (도보 8분 · 차로 4분)", ride?.train)
    }

    @Test
    fun `도보 분 없이 자동차 분만 있으면 차로 분만 보인다`() {
        val minutes = HomeWalkMinutes(station = 8, shuttleStop = null, shuttleStopDriving = 2)
        val ride = LiveBoardScreenModel(homeState().copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, minutes))).homeRide(at("23:00"))
        assertEquals("마지막 셔틀 23:40 · 정문 셔틀 정류장 (차로 2분)", ride?.shuttle)
    }

    @Test
    fun `지도 계산값으로 역 · 정류장 도보 분을 보인다`() {
        val state = homeState().copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = 8, shuttleStop = 3)))
        val ride = LiveBoardScreenModel(state).homeRide(at("23:00"))
        assertEquals("막차까지 52분", ride?.remain)
        assertEquals("마지막 셔틀 23:40 · 정문 셔틀 정류장 (도보 3분)", ride?.shuttle)
        assertEquals("막차 23:52 · 서현역 (도보 8분)", ride?.train)
    }

    @Test
    fun `위치를 쓸 수 없으면 서버 도보 분을 보이고 정류장 도보 분은 비운다`() {
        val state = homeState().copy(homeWalk = HomeWalkState.Unavailable(FESTIVAL_ID))
        val ride = LiveBoardScreenModel(state).homeRide(at("23:00"))
        assertEquals("마지막 셔틀 23:40 · 정문 셔틀 정류장", ride?.shuttle)
        assertEquals("막차 23:52 · 서현역 (도보 12분)", ride?.train)
    }

    @Test
    fun `걸어서 막차에 늦으면 아직 탈 수 있는 셔틀을 안내한다`() {
        val state =
            homeState(directions(lastShuttleAt = "2026-11-08T23:58:00+09:00"))
                .copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = 8, shuttleStop = 3)))
        assertEquals("셔틀까지 13분", LiveBoardScreenModel(state).homeRide(at("23:45"))?.remain)
    }

    @Test
    fun `걸어서 닿는 편이 없으면 아직 떠나지 않은 막차를 그대로 안내한다`() {
        val state =
            homeState(directions(lastShuttleAt = "2026-11-08T23:58:00+09:00"))
                .copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = 10, shuttleStop = 20)))
        assertEquals("막차까지 7분", LiveBoardScreenModel(state).homeRide(at("23:45"))?.remain)
    }

    @Test
    fun `이전 축제의 계산값은 현재 축제 카드에 쓰지 않는다`() {
        val state = homeState().copy(homeWalk = HomeWalkState.Estimated("old", HomeWalkMinutes(station = 1)))
        assertEquals(HomeWalkMinutes(station = 12), state.homeWalkMinutes)
    }

    // MARK: - "가는 길"과 지도 앱

    @Test
    fun `가는 길은 카드가 안내하는 막차의 역으로 지도 도보 길찾기를 연다`() = runTest {
        val maps = FakeMapsClient(openWalkingDirections = { true })
        viewModel(maps = maps).test(this, initialState = homeState()) {
            containerHost.onHomeDirectionsTapped()
            expectNoItems()
        }
        assertEquals(listOf(STATION_NAME), maps.opened.map { it.name })
    }

    @Test
    fun `셔틀을 안내하는 중이면 가는 길은 셔틀 정류장으로 연다`() = runTest {
        clock.now = at("23:45")
        val maps = FakeMapsClient(openWalkingDirections = { true })
        val state =
            homeState(directions(lastShuttleAt = "2026-11-08T23:58:00+09:00"))
                .copy(homeWalk = HomeWalkState.Estimated(FESTIVAL_ID, HomeWalkMinutes(station = 8, shuttleStop = 3)))
        viewModel(maps = maps).test(this, initialState = state) {
            containerHost.onHomeDirectionsTapped()
            expectNoItems()
        }
        assertEquals(listOf(SHUTTLE_STOP_NAME), maps.opened.map { it.name })
    }

    @Test
    fun `지도 앱을 열지 못하면 기존 팩 상세 가는 길로 보낸다`() = runTest {
        val maps = FakeMapsClient(openWalkingDirections = { false })
        viewModel(maps = maps).test(this, initialState = homeState()) {
            containerHost.onHomeDirectionsTapped()
            expectSideEffect(LiveBoardSideEffect.Boundary(LiveBoardBoundary.PackDirections(FESTIVAL_ID, FESTIVAL_NAME)))
        }
    }

    @Test
    fun `안내할 역 · 정류장이 없으면 바로 팩 상세 가는 길로 보낸다`() = runTest {
        val maps = FakeMapsClient()
        viewModel(maps = maps).test(this, initialState = homeState(FestivalPackDocument.Directions(lastTrainAt = "23:52"))) {
            containerHost.onHomeDirectionsTapped()
            expectSideEffect(LiveBoardSideEffect.Boundary(LiveBoardBoundary.PackDirections(FESTIVAL_ID, FESTIVAL_NAME)))
        }
        assertTrue(maps.opened.isEmpty())
    }

    // MARK: - 고정 값

    private val item = FestivalCatalogItem(id = FESTIVAL_ID, name = FESTIVAL_NAME, packVersion = 1)
    private val origin = LocationCoordinate(37.3900, 127.1200)
    private val venueCoordinate = LocationCoordinate(37.3850, 127.1230)

    private fun directions(lastShuttleAt: String = "2026-11-08T23:40:00+09:00", stationLocation: FestivalPackDetail.Coordinate? = null) =
        FestivalPackDocument.Directions(
            lastShuttleAt = lastShuttleAt,
            lastTrainAt = "2026-11-08T23:52:00+09:00",
            shuttleStopName = SHUTTLE_STOP_NAME,
            transitStationName = STATION_NAME,
            transitWalkMinutes = 12,
            transitLocation = stationLocation,
            venueLocation = FestivalPackDetail.Coordinate(venueCoordinate.latitude, venueCoordinate.longitude),
        )

    private fun pack(directions: FestivalPackDocument.Directions, id: String = FESTIVAL_ID) = CachedFestivalPack(
        id,
        1,
        "v1",
        checkedAt = null,
        document = FestivalPackDocument(id = id, name = FESTIVAL_NAME, directions = directions),
    )

    private fun homeState(directions: FestivalPackDocument.Directions = directions()): LiveBoardState {
        val pack = pack(directions)
        return LiveBoardState(
            festivalId = FESTIVAL_ID,
            catalog = listOf(item),
            cachedPacks = mapOf(FESTIVAL_ID to pack),
            selectedPack = pack.document,
            connection = LiveBoardConnection.Cached,
        )
    }

    /** 역 7분 10초(8분), 정류장 2분 30초(3분) */
    private fun routeClient() = FakeMapsClient(walkingDuration = { from, place ->
        assertEquals(origin, from)
        assertEquals(venueCoordinate, place.searchCenter)
        if (place.name == STATION_NAME) WalkingDuration(430.0) else WalkingDuration(150.0)
    })

    private fun at(time: String): Instant = date("2026-11-08T$time:00+09:00")

    private companion object {
        const val FESTIVAL_ID = "fst"
        const val FESTIVAL_NAME = "원더리벳 2026"
        const val STATION_NAME = "서현역"
        const val SHUTTLE_STOP_NAME = "정문 셔틀 정류장"
    }
}
