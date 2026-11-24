package io.festicore.feature.festival.crew

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.festival.crew.FestivalCrewUseCase
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewInviteCode
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import io.festicore.feature.festival.liveboard.crewgather.CrewGatherInput
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S07 크루(iOS `FestivalCrewFeature` + 크루장 메뉴 `FestivalCrewMenuFeature`). 함수 이름은 iOS `Action.View`와 같고 순서는 view → async → inner를 따른다.
 * 존 · 축제 이름 · 보관 시각은 보드 선택 문서(같은 축제일 때만), 첫 크루원 값은 보드의 마지막 라이브 상태에서 읽는다(iOS는 push할 때 보드 상태를 복사한다).
 * 조회 · 상태 보내기 · 토스트 만료 · 시계는 각각 하나만 돈다. [clock] · [sleep]은 테스트에서 시각과 기다림을 조절하려고 주입한다.
 */
class CrewViewModel(
    festivalId: String,
    crewId: String,
    private val crewUseCase: FestivalCrewUseCase,
    private val liveBoardUseCase: FestivalLiveBoardUseCase,
    boardSelectionUseCase: FestivalBoardSelectionUseCase,
    currentUserProvider: CurrentUserProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel(),
    OrbitContainerHost<CrewState, CrewState, CrewSideEffect> {
    override val container =
        orbitContainer<CrewState, CrewSideEffect>(
            boardSelectionUseCase.selection.value?.document?.takeIf { it.id == festivalId }.let { document ->
                CrewState(
                    festivalId = festivalId,
                    crewId = crewId,
                    zones = document?.zones.orEmpty(),
                    archivesAt = CrewState.archiveTime(document),
                    festivalName = document?.name,
                    sessionUserId = currentUserProvider.currentUserId.value,
                    now = clock.instant(),
                )
            },
        )

    private val loadJob = AtomicReference<Job?>(null)
    private val toastJob = AtomicReference<Job?>(null)
    private val clockJob = AtomicReference<Job?>(null)

    // MARK: - View

    /** 보드를 거쳐 왔으면 그 라이브 값으로 먼저 그리고, 상세가 없을 때만 조회한다. 보이는 동안 30초마다 시각을 바꾼다. */
    fun onAppear() = intent {
        if (state.live == null) showCachedLive()
        if (state.crew == null) load()
        startClock()
    }

    fun onDisappear() = intent { clockJob.getAndSet(null)?.cancel() }

    fun onRetryTapped() = intent {
        reduce { state.copy(load = CrewLoad.Loading) }
        load()
    }

    fun onBackTapped() = intent { postSideEffect(CrewSideEffect.BackRequested) }

    /** 크루장만 연다. 넘기기 · 내보내기 대상은 나를 뺀 크루원이다. */
    fun onMenuTapped() = intent {
        if (!state.isLeader) return@intent
        val members = state.live?.members.orEmpty().filter { it.id != state.sessionUserId }.map { CrewMenuState.Member(it.id, it.nickname) }
        reduce { state.copy(destination = CrewDestination.Menu(CrewMenuState(crewName = state.title, members = members))) }
    }

    fun onGatherTapped() = intent {
        if (!CrewPresentation.of(state, clock.instant()).canGather) return@intent
        val count = state.gatherCount + 1
        val input =
            CrewGatherInput(
                presentation = count,
                crewId = state.crewId,
                zones = state.zones,
                availableAt = state.live?.gatherAvailableAt,
                recipientCount = state.live?.members?.count { it.id != state.sessionUserId },
            )
        reduce { state.copy(gatherCount = count, destination = CrewDestination.Gather(input)) }
    }

    /** 누르면 바로 내 줄에 반영하고, 보내지 못하면 서버 값으로 다시 받는다. */
    fun onStateTapped(newState: FestivalCrewState) = intent {
        val me = state.sessionUserId ?: return@intent
        val live = state.live ?: return@intent
        if (state.isSendingStatus || state.myState == newState) return@intent
        val member = live.members.firstOrNull { it.id == me } ?: return@intent
        val updated =
            member.copy(
                state = newState.rawValue,
                zoneId = if (newState == FestivalCrewState.LEAVING) null else member.zoneId,
                updatedAt = clock.instant(),
            )
        reduce { state.copy(live = live.copy(members = live.members.map { if (it.id == me) updated else it }), isSendingStatus = true) }
        updateStatus(newState, member.zoneId)
    }

    /** 받은 크루원만 응답한다. 보낸 사람 · 끝난 크루 · 응답 중에는 보내지 않는다. */
    fun onGatherResponseTapped(response: FestivalCrewGatherResponse) = intent {
        val me = state.sessionUserId ?: return@intent
        val request = state.live?.gatherRequest ?: return@intent
        val gatherId = request.id ?: return@intent
        if (state.isRespondingGather || state.isArchived) return@intent
        if (gatherId == state.expiredGatherId || request.byUserId == me || state.myGatherResponse(request) == response) return@intent
        reduce { state.copy(isRespondingGather = true) }
        respondToGather(gatherId, response)
    }

    /** 모여 시트에서 보냈다(iOS `gather(.delegate(.gathered))`). 시트를 닫고 다시 받는다. */
    fun onGathered() = intent {
        reduce { state.copy(destination = null) }
        load()
    }

    /** 시트 · 다이얼로그를 닫았다(iOS `.dismiss` · 취소 · 알림 확인). */
    fun onDestinationDismissed() = intent {
        if (state.destination == null && state.pendingConfirm == null) return@intent
        reduce { state.copy(destination = null, pendingConfirm = null) }
    }

    /** 확인 다이얼로그의 확인(iOS `confirm(.confirmTapped)`). */
    fun onConfirmTapped() = intent {
        val confirm = state.pendingConfirm
        reduce { state.copy(destination = null, pendingConfirm = null) }
        when (confirm) {
            is CrewConfirm.Transfer -> transfer(confirm.userId, confirm.nickname)
            is CrewConfirm.Remove -> remove(confirm.userId, confirm.nickname)
            CrewConfirm.Dissolve -> dissolve()
            null -> Unit
        }
    }

    // MARK: - Scope: 크루장 메뉴

    fun onMenuRenameTapped() = menu { it.copy(step = CrewMenuStep.Rename, nameDraft = it.crewName) }

    fun onMenuTransferTapped() = menu { it.copy(step = CrewMenuStep.Transfer) }

    fun onMenuRemoveTapped() = menu { it.copy(step = CrewMenuStep.Remove) }

    fun onMenuBackTapped() = menu { it.copy(step = CrewMenuStep.Menu) }

    fun onMenuNameDraftChanged(value: String) = menu { it.copy(nameDraft = value) }

    /** 초대 링크 다시 만들기. 확인 없이 시트를 닫고 보낸다. */
    fun onMenuRotateInviteTapped() = intent {
        if (state.destination !is CrewDestination.Menu) return@intent
        reduce { state.copy(destination = null) }
        perform { onInviteRotated(crewUseCase.rotateInvite(state.crewId)) }
    }

    /** 이름 저장. 확인 없이 시트를 닫고 보낸다. */
    fun onMenuSaveNameTapped() = intent {
        val menu = (state.destination as? CrewDestination.Menu)?.menu ?: return@intent
        if (!menu.canSaveName) return@intent
        val name = menu.nameDraft.trim()
        reduce { state.copy(destination = null) }
        perform { onCrewChanged(crewUseCase.rename(state.crewId, name), CrewToast.Renamed) }
    }

    fun onMenuMemberTapped(userId: String) = intent {
        val menu = (state.destination as? CrewDestination.Menu)?.menu ?: return@intent
        val member = menu.members.firstOrNull { it.id == userId } ?: return@intent
        when (menu.step) {
            CrewMenuStep.Transfer -> askConfirm(CrewConfirm.Transfer(member.id, member.nickname))
            CrewMenuStep.Remove -> askConfirm(CrewConfirm.Remove(member.id, member.nickname))
            CrewMenuStep.Menu, CrewMenuStep.Rename -> Unit
        }
    }

    fun onMenuDissolveTapped() = intent {
        if (state.destination !is CrewDestination.Menu) return@intent
        askConfirm(CrewConfirm.Dissolve)
    }

    private fun menu(transform: (CrewMenuState) -> CrewMenuState) = intent {
        val menu = (state.destination as? CrewDestination.Menu)?.menu ?: return@intent
        reduce { state.copy(destination = CrewDestination.Menu(transform(menu))) }
    }

    /** 메뉴를 닫고 확인 다이얼로그를 띄운다(iOS `handleMenuDelegate`). */
    private suspend fun askConfirm(confirm: CrewConfirm) = subIntent {
        val dialog =
            when (confirm) {
                is CrewConfirm.Transfer ->
                    CrewDestination.Confirm("${confirm.nickname}님에게 크루장을 넘길까요?", "넘긴 뒤에는 크루장 메뉴를 쓸 수 없어요.", "넘기기")

                is CrewConfirm.Remove ->
                    CrewDestination.Confirm("${confirm.nickname}님을 내보낼까요?", "내보낸 크루원은 이 크루의 존과 상태를 볼 수 없어요.", "내보내기")

                CrewConfirm.Dissolve ->
                    CrewDestination.Confirm("크루를 해산할까요?", "모든 크루원이 크루에서 나가요. 해산한 크루는 되돌릴 수 없어요.", "해산하기")
            }
        reduce { state.copy(pendingConfirm = confirm, destination = dialog) }
    }

    // MARK: - Async

    private suspend fun showCachedLive() = subIntent {
        val cached =
            try {
                liveBoardUseCase.cachedLiveBoard(state.festivalId)?.crew
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            } ?: return@subIntent
        if (cached.id != state.crewId) return@subIntent
        reduce { if (state.live != null) state else state.copy(live = cached) }
    }

    private suspend fun load() = subIntent {
        val crewId = state.crewId
        val job =
            intent {
                val result =
                    try {
                        Result.success(crewUseCase.detail(crewId))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        Result.failure(crewError(error))
                    }
                result.fold(onSuccess = { onLoaded(it) }, onFailure = { onLoadFailed(it as FestivalCrewError) })
            }
        loadJob.getAndSet(job)?.cancel()
    }

    private suspend fun updateStatus(newState: FestivalCrewState, zoneId: String?) = subIntent {
        val sent =
            try {
                crewUseCase.updateStatus(state.crewId, newState, zoneId)
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
        reduce { state.copy(isSendingStatus = false) }
        if (!sent) {
            showToast(CrewToast.StatusFailed)
            load()
        }
    }

    private suspend fun respondToGather(gatherId: String, response: FestivalCrewGatherResponse) = subIntent {
        val result =
            try {
                Result.success(crewUseCase.respondToGather(state.crewId, gatherId, response))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(crewError(error))
            }
        result.fold(onSuccess = { onGatherResponded(it) }, onFailure = { onGatherResponseFailed(it as FestivalCrewError) })
    }

    private suspend fun transfer(userId: String, nickname: String) = subIntent {
        perform { onCrewChanged(crewUseCase.transfer(state.crewId, userId), CrewToast.Transferred(nickname)) }
    }

    private suspend fun remove(userId: String, nickname: String) = subIntent {
        perform {
            crewUseCase.removeMember(state.crewId, userId)
            onMemberRemoved(userId, nickname)
        }
    }

    private suspend fun dissolve() = subIntent {
        perform {
            crewUseCase.dissolve(state.crewId)
            postSideEffect(CrewSideEffect.CrewClosed)
        }
    }

    /** 크루장 동작 한 번을 보내고 실패하면 알림을 띄운다(iOS `perform`). */
    private suspend fun perform(operation: suspend () -> Unit) = subIntent {
        try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            onActionFailed(crewError(error))
        }
    }

    private suspend fun startClock() = subIntent {
        val job =
            intent {
                while (true) {
                    sleep(CLOCK_INTERVAL)
                    reduce { state.copy(now = clock.instant()) }
                }
            }
        clockJob.getAndSet(job)?.cancel()
    }

    /** 토스트를 띄우고 잠시 뒤 지운다. 새 토스트가 오면 이전 타이머를 끊는다. */
    private suspend fun showToast(toast: CrewToast) = subIntent {
        reduce { state.copy(toast = toast) }
        val job =
            intent {
                sleep(TOAST_DURATION)
                reduce { state.copy(toast = null) }
            }
        toastJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner

    private suspend fun onLoaded(crew: FestivalCrew) = subIntent {
        reduce { state.copy(crew = crew, live = crew.live ?: state.live, load = CrewLoad.Loaded) }
    }

    /** 크루에서 빠졌거나 크루가 없어졌으면 화면을 닫는다. */
    private suspend fun onLoadFailed(error: FestivalCrewError) = subIntent {
        if (error is FestivalCrewError.Forbidden || error is FestivalCrewError.NotFound) {
            postSideEffect(CrewSideEffect.CrewClosed)
            return@subIntent
        }
        reduce { state.copy(load = if (state.live == null) CrewLoad.Failed else CrewLoad.Loaded) }
    }

    private suspend fun onGatherResponded(reply: FestivalCrewGatherReply) = subIntent {
        reduce { state.copy(isRespondingGather = false, gatherReply = reply) }
    }

    private suspend fun onGatherResponseFailed(error: FestivalCrewError) = subIntent {
        if (error is FestivalCrewError.GatherExpired) {
            reduce { state.copy(isRespondingGather = false, expiredGatherId = state.live?.gatherRequest?.id) }
            showToast(CrewToast.GatherExpired)
            load()
            return@subIntent
        }
        reduce { state.copy(isRespondingGather = false, destination = CrewDestination.Alert("모여에 응답하지 못했어요", gatherMessage(error))) }
    }

    private suspend fun onInviteRotated(code: FestivalCrewInviteCode) = subIntent {
        reduce { state.copy(crew = state.crew?.copy(inviteCode = code.code, inviteExpiresAt = code.expiresAt)) }
        showToast(CrewToast.InviteRotated)
    }

    private suspend fun onCrewChanged(crew: FestivalCrew, toast: CrewToast) = subIntent {
        reduce { state.copy(crew = crew, live = state.live?.copy(name = crew.name, ownerId = crew.ownerId)) }
        showToast(toast)
    }

    private suspend fun onMemberRemoved(userId: String, nickname: String) = subIntent {
        reduce { state.copy(live = state.live?.let { live -> live.copy(members = live.members.filter { it.id != userId }) }) }
        showToast(CrewToast.Removed(nickname))
        load()
    }

    private suspend fun onActionFailed(error: FestivalCrewError) = subIntent {
        reduce { state.copy(destination = CrewDestination.Alert("요청하지 못했어요", message(error))) }
    }

    companion object {
        /** iOS `TimelineView(.periodic(by: 30))`. */
        val CLOCK_INTERVAL: Duration = 30.seconds

        /** iOS `toastDuration`. */
        val TOAST_DURATION: Duration = 2.seconds

        private fun crewError(error: Exception): FestivalCrewError = error as? FestivalCrewError ?: FestivalCrewError.Unavailable()

        internal fun message(error: FestivalCrewError): String = when (error) {
            is FestivalCrewError.Forbidden -> "크루장만 할 수 있어요."
            is FestivalCrewError.FestivalEnded -> "끝난 페스티벌의 크루는 바꿀 수 없어요."
            is FestivalCrewError.Unauthenticated -> "로그인 상태를 확인해 주세요."
            is FestivalCrewError.TermsRequired -> "최신 이용약관에 동의한 뒤 다시 시도해 주세요."
            is FestivalCrewError.InvalidInput -> "크루 이름은 1~${FestivalCrew.MAX_NAME_LENGTH}자로 입력해 주세요."
            else -> "연결을 확인한 뒤 다시 시도해 주세요."
        }

        internal fun gatherMessage(error: FestivalCrewError): String = when (error) {
            is FestivalCrewError.Forbidden -> "이 모여에는 응답할 수 없어요."
            is FestivalCrewError.FestivalEnded -> "끝난 페스티벌의 크루는 바꿀 수 없어요."
            is FestivalCrewError.Unauthenticated -> "로그인 상태를 확인해 주세요."
            is FestivalCrewError.TermsRequired -> "최신 이용약관에 동의한 뒤 다시 시도해 주세요."
            is FestivalCrewError.NotFound -> "모여 요청을 찾지 못했어요."
            else -> "연결을 확인한 뒤 다시 시도해 주세요."
        }
    }
}
