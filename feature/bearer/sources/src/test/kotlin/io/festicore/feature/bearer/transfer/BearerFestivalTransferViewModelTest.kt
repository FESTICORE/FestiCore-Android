package io.festicore.feature.bearer.transfer

import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestivalOption
import io.festicore.domain.bearer.entity.BearerFestivalTransfer
import io.festicore.domain.bearer.entity.BearerFestivalTransferRequest
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.feature.bearer.claim.FakeBearerUseCase
import io.festicore.feature.bearer.register.BearerFestivalList
import io.festicore.feature.bearer.register.BearerFestivalLoad
import io.festicore.feature.bearer.register.FakeCatalogUseCase
import io.festicore.feature.bearer.register.FakeReceivedPackUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** 깃발 축제 이동(Android 먼저, 백엔드 #409). 서버에 쓰지 않는다. */
class BearerFestivalTransferViewModelTest {
    private companion object {
        // 2026-11-13 00:00 UTC(서울 09:00)
        val now: Instant = Instant.ofEpochSecond(1_794_528_000)

        val catalog =
            listOf(
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
            )

        val busan = BearerFestivalOption(id = "fst_busan", name = "부산국제록페스티벌 2026", dateText = "11/30-12/1", hasPack = false)

        val profile = BearerFlagProfile(id = "flg_1", name = "호락호락")

        fun owned(isOwner: Boolean = true) = BearerOwnedFlag(
            festivalId = "fst_wonder",
            isOwner = isOwner,
            joinedAt = null,
            profile = profile,
            lifecycle = BearerFlagLifecycle.Scheduled,
            isHidden = false,
        )

        fun board(bearers: Int) = BearerBoard(
            profile = profile,
            status = BearerFlagStatus("flg_1", BearerFlagLifecycle.Scheduled, null, null, now),
            followerCount = 12,
            coBearers = List(bearers) { BearerCoBearer("usr_$it", "기수$it") },
        )

        val request = BearerFestivalTransferRequest("flg_1", "fst_wonder", "fst_busan", "transfer-1")

        val transferred = BearerFestivalTransfer("flg_1", "fst_wonder", "fst_busan", profile)

        val eligible =
            BearerFestivalTransferState(
                flagId = "flg_1",
                fromFestivalId = "fst_wonder",
                eligibility = BearerTransferEligibility.Eligible,
                festivals = BearerFestivalList(load = BearerFestivalLoad.Loaded, upcoming = listOf(busan)),
                selectedFestivalId = "fst_busan",
            )
    }

    private fun viewModel(useCase: FakeBearerUseCase) = BearerFestivalTransferViewModel(
        flagId = "flg_1",
        bearerUseCase = useCase,
        catalogUseCase = FakeCatalogUseCase(refresh = { catalog }),
        receivedPackUseCase = FakeReceivedPackUseCase(emptyList()),
        clock = Clock.fixed(now, ZoneId.of("Asia/Seoul")),
    )

    /** 원래 기수 · 공동 기수 없음이면 옮길 수 있고, 지금 페스티벌은 고를 목록에서 뺀다. 처음에는 아무것도 고르지 않는다. */
    @Test
    fun loadsEligibilityAndExcludesCurrentFestival() = runTest {
        val useCase = FakeBearerUseCase(onFetchBearerFlags = { listOf(owned()) }, onFetchBoard = { board(bearers = 1) })
        viewModel(useCase).test(this) {
            containerHost.onAppear()
            expectState {
                copy(
                    fromFestivalId = "fst_wonder",
                    eligibility = BearerTransferEligibility.Eligible,
                    festivals = BearerFestivalList(load = BearerFestivalLoad.Loaded, upcoming = listOf(busan)),
                )
            }
            containerHost.onFestivalTapped("fst_busan")
            expectState { copy(selectedFestivalId = "fst_busan") }
        }
    }

    @Test
    fun notOwnerOrCoBearersCannotTransfer() = runTest {
        listOf(
            FakeBearerUseCase(onFetchBearerFlags = { listOf(owned(isOwner = false)) }, onFetchBoard = { board(1) }) to
                BearerTransferEligibility.NotOwner,
            FakeBearerUseCase(onFetchBearerFlags = { emptyList() }, onFetchBoard = { board(1) }) to BearerTransferEligibility.NotOwner,
            FakeBearerUseCase(onFetchBearerFlags = { listOf(owned()) }, onFetchBoard = { board(2) }) to
                BearerTransferEligibility.HasCoBearers,
            FakeBearerUseCase(onFetchBearerFlags = { throw BearerError.Offline() }, onFetchBoard = { board(1) }) to
                BearerTransferEligibility.Failed(offline = true),
        ).forEach { (useCase, expected) ->
            viewModel(useCase).test(this) {
                containerHost.onAppear()
                val loaded = awaitState()
                assertEquals(expected, loaded.eligibility)
                assertFalse(loaded.copy(selectedFestivalId = "fst_busan").canTransfer)
            }
        }
    }

    /** 확인 팝업을 거쳐 보내고, 성공하면 부모에 알린다. */
    @Test
    fun confirmThenTransfer() = runTest {
        val sent = mutableListOf<BearerFestivalTransferRequest>()
        val useCase =
            FakeBearerUseCase(
                onMakeFestivalTransferRequest = { flagId, from, to -> BearerFestivalTransferRequest(flagId, from, to, "transfer-1") },
                onTransferFestival = {
                    sent += it
                    transferred
                },
            )
        viewModel(useCase).test(this, initialState = eligible) {
            assertTrue(eligible.canTransfer)
            containerHost.onTransferTapped()
            expectState { copy(isConfirming = true) }
            containerHost.onConfirmTapped()
            expectState { copy(isConfirming = false, isTransferring = true, pendingRequest = request) }
            expectState { copy(isTransferring = false, pendingRequest = null) }
            expectSideEffect(BearerFestivalTransferSideEffect.Transferred(flagId = "flg_1", festivalId = "fst_busan"))
        }
        assertEquals(listOf(request), sent)
    }

    /** 응답을 받지 못했으면 같은 멱등키로 다시 보낸다. */
    @Test
    fun retryAfterOfflineReusesKey() = runTest {
        var made = 0
        val sent = mutableListOf<BearerFestivalTransferRequest>()
        val useCase =
            FakeBearerUseCase(
                onMakeFestivalTransferRequest = { flagId, from, to ->
                    BearerFestivalTransferRequest(flagId, from, to, "transfer-${++made}")
                },
                onTransferFestival = {
                    sent += it
                    if (sent.size == 1) throw BearerError.Offline() else transferred
                },
            )
        viewModel(useCase).test(this, initialState = eligible) {
            containerHost.onConfirmTapped()
            expectState { copy(isTransferring = true, pendingRequest = request) }
            expectState { copy(isTransferring = false, errorMessage = BearerFestivalTransferViewModel.OFFLINE_MESSAGE) }
            containerHost.onErrorDismissed()
            expectState { copy(errorMessage = null) }
            containerHost.onConfirmTapped()
            expectState { copy(isTransferring = true) }
            expectState { copy(isTransferring = false, pendingRequest = null) }
            expectSideEffect(BearerFestivalTransferSideEffect.Transferred(flagId = "flg_1", festivalId = "fst_busan"))
        }
        assertEquals(1, made)
        assertEquals(listOf("transfer-1", "transfer-1"), sent.map { it.idempotencyKey })
    }

    /** 서버가 거절했으면 다음 시도는 새 키로 보낸다. 공동 기수 · 원래 기수 거절은 버튼도 막는다. */
    @Test
    fun rejectionClearsKey() = runTest {
        val useCase =
            FakeBearerUseCase(
                onMakeFestivalTransferRequest = { _, _, _ -> request },
                onTransferFestival = { throw BearerError.HasCoBearers() },
            )
        viewModel(useCase).test(this, initialState = eligible) {
            containerHost.onConfirmTapped()
            expectState { copy(isTransferring = true, pendingRequest = request) }
            expectState {
                copy(
                    isTransferring = false,
                    pendingRequest = null,
                    eligibility = BearerTransferEligibility.HasCoBearers,
                    errorMessage = BearerFestivalTransferViewModel.transferMessage(BearerError.HasCoBearers()),
                )
            }
        }
    }

    @Test
    fun transferMessages() {
        val messages =
            mapOf(
                BearerError.FestivalMismatch() to "깃발의 페스티벌이 이미 바뀌었어요. 깃발을 다시 열어 확인해 주세요.",
                BearerError.HasCoBearers() to "공동 기수가 있는 깃발은 옮길 수 없어요.",
                BearerError.BearerLimit() to "옮길 페스티벌에서 이미 기수예요. 페스티벌마다 깃발은 하나만 맡을 수 있어요.",
                BearerError.NameTaken() to "옮길 페스티벌에 같은 이름의 깃발이 있어요. 이름을 바꾼 뒤 옮겨 주세요.",
                BearerError.BearerModeOff() to "기수 모드가 꺼져 있어요. 마이에서 기수 모드를 켠 뒤 옮겨 주세요.",
                BearerError.Conflict() to "요청이 겹쳤어요. 잠시 후 다시 시도해 주세요.",
                BearerError.NotOwner() to "처음 깃발을 만든 기수만 옮길 수 있어요.",
                BearerError.NotBearer() to "이 깃발의 기수가 아니에요.",
                BearerError.TermsRequired() to "새 약관에 동의한 뒤에 옮겨 주세요.",
                BearerError.NotFound() to "깃발이나 옮길 페스티벌을 찾을 수 없어요.",
                BearerError.NotAuthenticated() to "로그인한 뒤에 옮겨 주세요.",
                BearerError.Offline() to BearerFestivalTransferViewModel.OFFLINE_MESSAGE,
                BearerError.Unavailable() to "옮기지 못했어요. 잠시 후 다시 시도해 주세요.",
            )
        messages.forEach { (error, message) ->
            assertEquals(error.toString(), message, BearerFestivalTransferViewModel.transferMessage(error))
        }
    }
}
