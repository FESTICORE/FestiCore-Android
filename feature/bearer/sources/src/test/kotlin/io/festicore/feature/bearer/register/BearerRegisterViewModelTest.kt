package io.festicore.feature.bearer.register

import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFestivalOption
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPage
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.feature.bearer.claim.FakeBearerUseCase
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `BearerRegisterFeatureTests` 10건(함수 이름을 iOS와 같게 둔다) + Android 추가 `photoLoadFailed`. 서버에 쓰지 않는다. */
class BearerRegisterViewModelTest {
    private companion object {
        // 2026-11-13 00:00 UTC(서울 09:00)
        val now: Instant = Instant.ofEpochSecond(1_794_528_000)

        val catalog =
            listOf(
                FestivalCatalogItem(id = "fst_past", name = "지난 페스티벌", firstDate = "2026-10-01", lastDate = "2026-10-02", packVersion = 1),
                FestivalCatalogItem(
                    id = "fst_seoul",
                    name = "서울 파크 뮤직 페스티벌",
                    firstDate = "2026-12-19",
                    lastDate = "2026-12-20",
                    packVersion = 1,
                ),
                FestivalCatalogItem(
                    id = "fst_wonder",
                    name = "원더리벳 2026",
                    firstDate = "2026-11-20",
                    lastDate = "2026-11-22",
                    packVersion = 1,
                ),
                FestivalCatalogItem(
                    id = "fst_busan",
                    name = "부산국제록페스티벌 2026",
                    firstDate = "2026-11-30",
                    lastDate = "2026-12-01",
                    packVersion = 1,
                ),
                FestivalCatalogItem(id = "fst_late", name = "늦은 페스티벌", firstDate = "2027-01-10", lastDate = null, packVersion = 1),
            )

        val wonder = BearerFestivalOption(id = "fst_wonder", name = "원더리벳 2026", dateText = "11/20-22", hasPack = false)

        val change =
            BearerProfileChange(name = "호락호락", bio = "", tags = listOf(BearerFlagTag.Newbie), planVisible = true, homeZoneId = null)

        val request = BearerRegisterRequest(festivalId = "fst_wonder", change = change, idempotencyKey = "register-1")

        val claim =
            BearerClaim(
                flagId = "flg_9",
                profile = BearerFlagProfile(id = "flg_9", name = "호락호락", tags = listOf(BearerFlagTag.Newbie)),
                isNew = true,
                coBearerIds = emptyList(),
            )
    }

    private fun loadedState(): BearerRegisterState = BearerRegisterState(
        festivals = BearerFestivalList(load = BearerFestivalLoad.Loaded, upcoming = listOf(wonder)),
        selectedFestivalId = "fst_wonder",
    )

    private fun viewModel(
        preferredFestivalId: String? = null,
        packs: List<FestivalReceivedPack> = emptyList(),
        useCase: FakeBearerUseCase = FakeBearerUseCase(),
        catalogUseCase: FestivalCatalogUseCase = FakeCatalogUseCase(refresh = { catalog }),
    ) = BearerRegisterViewModel(
        preferredFestivalId = preferredFestivalId,
        bearerUseCase = useCase,
        catalogUseCase = catalogUseCase,
        receivedPackUseCase = FakeReceivedPackUseCase(packs),
        clock = Clock.fixed(now, ZoneId.of("Asia/Seoul")),
    )

    // 받은 팩을 먼저, 끝나지 않은 서버 페스티벌을 시작 순으로 고를 수 있게 한다
    @Test
    fun festivalOptions() {
        val options =
            BearerRegisterViewModel.options(
                packs = listOf(FestivalReceivedPack(id = "fst_busan", name = "부산국제록페스티벌 2026", packVersion = 3)),
                catalog = catalog,
                today = "2026-11-12",
            )

        assertEquals(
            listOf(BearerFestivalOption(id = "fst_busan", name = "부산국제록페스티벌 2026", dateText = "11/30-12/1", hasPack = true)),
            options.received,
        )
        assertEquals(listOf("fst_wonder", "fst_seoul", "fst_late"), options.upcoming.map { it.id })
        assertEquals("원더리벳 2026 · 11/20-22", options.upcoming.first().title)
        assertEquals("1/10", options.upcoming.last().dateText)
    }

    // 케이스 1: 받은 팩이 없으면 앞으로 열리는 페스티벌 3개를 보이고 라이브에서 연 페스티벌을 먼저 고른다
    @Test
    fun loadsUpcomingAndSelectsPreferred() = runTest {
        viewModel(preferredFestivalId = "fst_wonder").test(this) {
            containerHost.onAppear()
            expectState {
                copy(
                    festivals =
                        festivals.copy(
                            load = BearerFestivalLoad.Loaded,
                            upcoming = BearerRegisterViewModel.options(
                                packs = emptyList(),
                                catalog = catalog,
                                today = "2026-11-12",
                            ).upcoming,
                        ),
                    selectedFestivalId = "fst_wonder",
                )
            }
            val festivals = containerHost.container.stateFlow.value.festivals
            assertEquals(listOf("fst_wonder", "fst_busan", "fst_seoul"), festivals.visibleUpcoming.map { it.id })
            assertTrue(festivals.canShowMore)

            containerHost.onShowMoreTapped()
            expectState { copy(festivals = this.festivals.copy(showsAllUpcoming = true)) }
            assertEquals(4, containerHost.container.stateFlow.value.festivals.visibleUpcoming.size)
            assertFalse(containerHost.container.stateFlow.value.festivals.canShowMore)
        }
    }

    // 케이스 2: 받은 팩이 있으면 그 팩을 고르고 팩의 존을 읽는다
    @Test
    fun receivedPackLoadsZones() = runTest {
        val zones = listOf(BearerZone(id = "zone_a", name = "리벳 스테이지 앞 왼쪽"))
        val useCase =
            FakeBearerUseCase(onFetchFestival = { festivalId ->
                assertEquals("fst_wonder", festivalId)
                BearerFestival(id = festivalId, name = "원더리벳 2026", zones = zones)
            })
        viewModel(
            packs = listOf(FestivalReceivedPack(id = "fst_wonder", name = "원더리벳 2026", packVersion = 2)),
            useCase = useCase,
        ).test(this) {
            containerHost.onAppear()
            val loaded = awaitState()
            assertEquals("fst_wonder", loaded.selectedFestivalId)
            assertEquals(true, loaded.selectedFestival?.hasPack)
            expectState { copy(zones = zones) }
        }
    }

    // 고를 페스티벌이 없으면 실패로 두고 다시 시도할 수 있다
    @Test
    fun emptyFestivalsFail() = runTest {
        val catalogUseCase = FakeCatalogUseCase(cached = { emptyList() }, refresh = { throw IOException("오프라인") })
        viewModel(catalogUseCase = catalogUseCase).test(this) {
            containerHost.onAppear()
            expectState { copy(festivals = festivals.copy(load = BearerFestivalLoad.Failed(offline = true))) }
            containerHost.onRetryTapped()
            expectState { copy(festivals = festivals.copy(load = BearerFestivalLoad.Loading)) }
            expectState { copy(festivals = festivals.copy(load = BearerFestivalLoad.Failed(offline = true))) }
        }
    }

    // 등록하면 태그가 없을 때 초보 환영으로 보내고, 성공하면 핀을 요청하고 저장 완료(E1)를 보인다
    @Test
    fun registerSucceeds() = runTest {
        val useCase =
            FakeBearerUseCase(
                onMakeRegisterRequest = { festivalId, sentChange ->
                    assertEquals("fst_wonder", festivalId)
                    assertEquals(change, sentChange)
                    request
                },
                onRegister = { sent ->
                    assertEquals(request, sent)
                    claim
                },
            )
        val state = loadedState().copy(form = BearerRegisterForm(name = "호락호락"))
        viewModel(useCase = useCase).test(this, initialState = state) {
            containerHost.onSaveTapped()
            expectState { copy(pendingRequest = request, isSaving = true) }
            expectState {
                copy(isSaving = false, pendingRequest = null, completion = BearerRegisterCompletion(claim = claim, festival = wonder))
            }
            expectSideEffect(BearerRegisterSideEffect.Registered(flagId = "flg_9", festivalId = "fst_wonder"))

            containerHost.onPrimaryDoneTapped()
            expectSideEffect(BearerRegisterSideEffect.PackRequested(festivalId = "fst_wonder"))
            containerHost.onSecondaryDoneTapped()
            expectSideEffect(BearerRegisterSideEffect.Finished(festivalId = "fst_wonder"))
        }
    }

    // 오프라인이면 같은 멱등키를 남겨 다시 누를 때 같은 등록으로 보낸다
    @Test
    fun offlineRetryReusesKey() = runTest {
        var made = 0
        val useCase =
            FakeBearerUseCase(
                onMakeRegisterRequest = { _, _ ->
                    made += 1
                    request
                },
                onRegister = { throw BearerError.Offline() },
            )
        val offline = "인터넷에 연결되지 않았어요. 연결 후 다시 눌러 주세요"
        val state = loadedState().copy(form = BearerRegisterForm(name = "호락호락"))
        viewModel(useCase = useCase).test(this, initialState = state) {
            containerHost.onSaveTapped()
            expectState { copy(pendingRequest = request, isSaving = true) }
            expectState { copy(isSaving = false, saveError = offline) }
            containerHost.onSaveTapped()
            expectState { copy(isSaving = true, saveError = null) }
            expectState { copy(isSaving = false, saveError = offline) }
        }
        assertEquals(1, made)
    }

    // 409 이름 중복 · 기수 한도는 팝업으로 안내한다
    @Test
    fun conflictsShowAlerts() = runTest {
        val state = loadedState().copy(form = BearerRegisterForm(name = "호락호락"), pendingRequest = request)
        viewModel().test(this, initialState = state) {
            containerHost.onRegisterFailed(BearerError.NameTaken())
            expectState {
                copy(
                    nameError = "이미 있는 깃발 이름이에요. 다른 이름을 지어 주세요.",
                    alert = BearerRegisterAlert.NameTaken,
                    pendingRequest = null,
                )
            }
            assertFalse(containerHost.container.stateFlow.value.canSave)
            containerHost.onAlertDismissed()
            expectState { copy(alert = null) }
            containerHost.onRegisterFailed(BearerError.BearerLimit())
            expectState { copy(alert = BearerRegisterAlert.BearerLimit(festivalName = "원더리벳 2026")) }
        }
    }

    // 이름이 2자 미만이거나 페스티벌을 고르지 않으면 등록할 수 없다
    @Test
    fun validation() {
        var state = loadedState().copy(form = BearerRegisterForm(name = "호"))
        assertFalse(state.canSave)
        state = state.copy(form = BearerRegisterForm(name = "호락"))
        assertTrue(state.canSave)
        state = state.copy(selectedFestivalId = null)
        assertFalse(state.canSave)
    }

    // 기수 여부는 고른 뒤 다음을 눌러야 넘어간다
    @Test
    fun roleChoice() = runTest {
        BearerRoleChoiceViewModel().test(this) {
            containerHost.onNextTapped()
            containerHost.onRoleTapped(BearerRole.Bearer)
            expectState { copy(role = BearerRole.Bearer) }
            containerHost.onNextTapped()
            expectSideEffect(BearerRoleChoiceSideEffect.BearerChosen)
            containerHost.onRoleTapped(BearerRole.Audience)
            expectState { copy(role = BearerRole.Audience) }
            containerHost.onNextTapped()
            expectSideEffect(BearerRoleChoiceSideEffect.AudienceChosen)
        }
    }

    // 저장 중에는 페스티벌을 바꿀 수 없고, 완료는 보낸 요청의 페스티벌로 만든다
    @Test
    fun savingKeepsRequestedFestival() = runTest {
        val other = BearerFestivalOption(id = "fst_other", name = "다른 페스티벌", dateText = "12/01", hasPack = false)
        val state =
            loadedState().copy(
                festivals = BearerFestivalList(load = BearerFestivalLoad.Loaded, upcoming = listOf(wonder, other)),
                isSaving = true,
                pendingRequest = request,
            )
        viewModel().test(this, initialState = state) {
            containerHost.onFestivalTapped("fst_other")
            containerHost.onRegistered(claim)
            expectState {
                copy(isSaving = false, pendingRequest = null, completion = BearerRegisterCompletion(claim = claim, festival = wonder))
            }
            expectSideEffect(BearerRegisterSideEffect.Registered(flagId = "flg_9", festivalId = "fst_wonder"))
        }
    }

    // Android 추가: 고른 사진을 읽지 못하면 안내하고, 다시 고르면 지운다(프로필 `photoLoadFailed`와 같음)
    @Test
    fun photoLoadFailed() = runTest {
        val data = byteArrayOf(1, 2, 3)
        viewModel().test(this, initialState = loadedState()) {
            containerHost.onPhotoLoadFailed()
            expectState { copy(photoError = "사진을 올리지 못했어요. 다시 골라 주세요") }
            containerHost.onPhotoPicked(data)
            expectState { copy(photoData = data, photoError = null) }
        }
    }
}

/** 테스트 전용 축제 목록(iOS `MockFestivalCatalogUseCase`). 넘기지 않은 함수는 부르면 실패한다. */
internal class FakeCatalogUseCase(
    private val cached: (suspend () -> List<FestivalCatalogItem>)? = null,
    private val refresh: (suspend () -> List<FestivalCatalogItem>)? = null,
) : FestivalCatalogUseCase {
    override suspend fun cachedFestivals(): List<FestivalCatalogItem> = (cached ?: unused())()

    override suspend fun refreshFestivals(): List<FestivalCatalogItem> = (refresh ?: unused())()

    override suspend fun festivalPage(after: String?, limit: Int): FestivalCatalogPage = unused()

    override fun imageUrl(value: String?): String? = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("이 테스트는 이 함수를 넘기지 않았다")
}

/** 테스트 전용 받은 팩(iOS `MockFestivalReceivedPackUseCase`). */
internal class FakeReceivedPackUseCase(private val packs: List<FestivalReceivedPack>) : FestivalReceivedPackUseCase {
    override suspend fun receivedPacks(): List<FestivalReceivedPack> = packs

    override suspend fun refreshReceivedPacks(): List<FestivalReceivedPack> = throw UnsupportedOperationException("이 테스트는 이 함수를 넘기지 않았다")
}
