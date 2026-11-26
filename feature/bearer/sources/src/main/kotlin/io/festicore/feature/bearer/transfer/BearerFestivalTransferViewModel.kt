package io.festicore.feature.bearer.transfer

import androidx.lifecycle.ViewModel
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.feature.bearer.register.BearerFestivalLoad
import io.festicore.feature.bearer.register.loadBearerFestivalOptions
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 깃발 축제 이동(Android 먼저, 백엔드 #409). 내 깃발 목록 · 기수 보드로 원래 기수 · 공동 기수 여부를 보고, 페스티벌을 고르고,
 * 확인 팝업을 거쳐 보낸다. 읽기 · 보내기는 각각 하나만 돈다.
 */
class BearerFestivalTransferViewModel(
    flagId: String,
    private val bearerUseCase: BearerUseCase,
    private val catalogUseCase: FestivalCatalogUseCase,
    private val receivedPackUseCase: FestivalReceivedPackUseCase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
) : ViewModel(),
    OrbitContainerHost<BearerFestivalTransferState, BearerFestivalTransferState, BearerFestivalTransferSideEffect> {
    override val container =
        orbitContainer<BearerFestivalTransferState, BearerFestivalTransferSideEffect>(BearerFestivalTransferState(flagId = flagId))

    private val loadJob = AtomicReference<Job?>(null)
    private val transferJob = AtomicReference<Job?>(null)

    // MARK: - View

    fun onAppear() = intent {
        if (state.eligibility != BearerTransferEligibility.Loading) return@intent
        load()
    }

    fun onRetryTapped() = intent {
        reduce {
            state.copy(
                eligibility = BearerTransferEligibility.Loading,
                festivals = state.festivals.copy(load = BearerFestivalLoad.Loading),
            )
        }
        load()
    }

    fun onFestivalTapped(festivalId: String) = intent {
        if (state.isTransferring || state.selectedFestivalId == festivalId) return@intent
        reduce { state.copy(selectedFestivalId = festivalId) }
    }

    fun onShowMoreTapped() = intent {
        reduce { state.copy(festivals = state.festivals.copy(showsAllUpcoming = true)) }
    }

    fun onTransferTapped() = intent {
        if (!state.canTransfer) return@intent
        reduce { state.copy(isConfirming = true) }
    }

    fun onConfirmDismissed() = intent {
        reduce { state.copy(isConfirming = false) }
    }

    /** 확인 팝업의 '옮기기'. 같은 페스티벌로 다시 보내면 응답을 받지 못한 이전 요청(같은 멱등키)을 그대로 쓴다. */
    fun onConfirmTapped() = intent {
        val from = state.fromFestivalId
        val to = state.selectedFestivalId
        if (!state.canTransfer || from == null || to == null) return@intent
        val request =
            state.pendingRequest?.takeIf { it.fromFestivalId == from && it.toFestivalId == to }
                ?: bearerUseCase.makeFestivalTransferRequest(flagId = state.flagId, fromFestivalId = from, toFestivalId = to)
        reduce { state.copy(isConfirming = false, isTransferring = true, pendingRequest = request) }
        transfer()
    }

    fun onErrorDismissed() = intent {
        reduce { state.copy(errorMessage = null) }
    }

    fun onBackTapped() = intent {
        postSideEffect(BearerFestivalTransferSideEffect.CloseRequested)
    }

    // MARK: - Async

    private suspend fun load() = subIntent {
        val flagId = state.flagId
        val today = LocalDate.now(clock).toString()
        val job =
            intent {
                coroutineScope {
                    val festivals = async { loadBearerFestivalOptions(receivedPackUseCase, catalogUseCase, today) }
                    val (fromFestivalId, eligibility) = eligibility(flagId)
                    val loaded = festivals.await()
                    val received = loaded.options.received.filter { it.id != fromFestivalId }
                    val upcoming = loaded.options.upcoming.filter { it.id != fromFestivalId }
                    val load = if (received.isEmpty() &&
                        upcoming.isEmpty()
                    ) {
                        BearerFestivalLoad.Failed(loaded.isOffline)
                    } else {
                        BearerFestivalLoad.Loaded
                    }
                    reduce {
                        state.copy(
                            fromFestivalId = fromFestivalId,
                            eligibility = eligibility,
                            festivals = state.festivals.copy(load = load, received = received, upcoming = upcoming),
                            selectedFestivalId = state.selectedFestivalId?.takeIf { id -> (received + upcoming).any { it.id == id } },
                        )
                    }
                }
            }
        loadJob.getAndSet(job)?.cancel()
    }

    /** 내가 기수인 깃발에서 지금 페스티벌 · 원래 기수 여부를, 기수 보드에서 공동 기수 수를 읽는다. */
    private suspend fun eligibility(flagId: String): Pair<String?, BearerTransferEligibility> = try {
        val owned = bearerUseCase.fetchBearerFlags(festivalId = null).firstOrNull { it.id == flagId }
        when {
            owned == null || !owned.isOwner -> owned?.festivalId to BearerTransferEligibility.NotOwner

            // 기수 보드의 coBearers에는 원래 기수도 들어 있다.
            bearerUseCase.fetchBoard(flagId = flagId).coBearers.size > 1 -> owned.festivalId to BearerTransferEligibility.HasCoBearers

            else -> owned.festivalId to BearerTransferEligibility.Eligible
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null to BearerTransferEligibility.Failed(offline = error is BearerError.Offline)
    }

    private suspend fun transfer() = subIntent {
        val request = state.pendingRequest ?: return@subIntent
        val job =
            intent {
                val error =
                    try {
                        val result = bearerUseCase.transferFestival(request)
                        reduce { state.copy(isTransferring = false, pendingRequest = null) }
                        postSideEffect(BearerFestivalTransferSideEffect.Transferred(flagId = result.flagId, festivalId = result.festivalId))
                        return@intent
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: BearerError) {
                        error
                    } catch (_: Exception) {
                        BearerError.Unavailable()
                    }
                transferFailed(error)
            }
        transferJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner

    private suspend fun transferFailed(error: BearerError) = subIntent {
        // 결과를 모르는 실패(연결 · 서버 · 응답 해석)만 같은 키로 다시 보낸다. 서버가 거절했으면 다음 시도는 새 키다.
        val keepsRequest = error is BearerError.Offline || error is BearerError.Unavailable || error is BearerError.InvalidResponse
        reduce {
            state.copy(
                isTransferring = false,
                pendingRequest = if (keepsRequest) state.pendingRequest else null,
                eligibility =
                    when (error) {
                        is BearerError.NotOwner -> BearerTransferEligibility.NotOwner
                        is BearerError.HasCoBearers -> BearerTransferEligibility.HasCoBearers
                        else -> state.eligibility
                    },
                errorMessage = transferMessage(error),
            )
        }
    }

    internal companion object {
        const val OFFLINE_MESSAGE = "인터넷에 연결되지 않았어요. 연결 후 다시 눌러 주세요."

        fun transferMessage(error: BearerError): String = when (error) {
            is BearerError.FestivalMismatch -> "깃발의 페스티벌이 이미 바뀌었어요. 깃발을 다시 열어 확인해 주세요."
            is BearerError.HasCoBearers -> "공동 기수가 있는 깃발은 옮길 수 없어요."
            is BearerError.BearerLimit -> "옮길 페스티벌에서 이미 기수예요. 페스티벌마다 깃발은 하나만 맡을 수 있어요."
            is BearerError.NameTaken -> "옮길 페스티벌에 같은 이름의 깃발이 있어요. 이름을 바꾼 뒤 옮겨 주세요."
            is BearerError.BearerModeOff -> "기수 모드가 꺼져 있어요. 마이에서 기수 모드를 켠 뒤 옮겨 주세요."
            is BearerError.Conflict -> "요청이 겹쳤어요. 잠시 후 다시 시도해 주세요."
            is BearerError.NotOwner -> "처음 깃발을 만든 기수만 옮길 수 있어요."
            is BearerError.NotBearer -> "이 깃발의 기수가 아니에요."
            is BearerError.TermsRequired -> "새 약관에 동의한 뒤에 옮겨 주세요."
            is BearerError.NotFound -> "깃발이나 옮길 페스티벌을 찾을 수 없어요."
            is BearerError.NotAuthenticated -> "로그인한 뒤에 옮겨 주세요."
            is BearerError.Offline -> OFFLINE_MESSAGE
            else -> "옮기지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}
