package io.festicore.feature.festival.crewjoin

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.festival.crew.FestivalCrewUseCase
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S08 크루 참여 확인(iOS `FestivalCrewJoinFeature`). 초대 링크의 크루를 미리 보고 참여하거나 닫는다.
 * 이미 같은 페스티벌의 다른 크루에 있으면(S08 D) 확인을 받은 뒤 기존 크루를 나가고 참여한다.
 * 로그인 상태는 현재 사용자로 정하고, 바뀌면 [CrewJoinState.hasSession]만 바꾼다(자동 참여하지 않는다, iOS `sessionChanged`).
 * 함수 이름은 iOS `Action.View`와 같고, 순서는 view → async → inner를 따른다.
 */
class CrewJoinViewModel(
    code: String,
    private val crewUseCase: FestivalCrewUseCase,
    private val liveBoardUseCase: FestivalLiveBoardUseCase,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel(),
    OrbitContainerHost<CrewJoinState, CrewJoinState, CrewJoinSideEffect> {
    override val container =
        orbitContainer<CrewJoinState, CrewJoinSideEffect>(
            CrewJoinState(code = code, hasSession = currentUserProvider.currentUserId.value != null),
        ) {
            currentUserProvider.currentUserId.collect { userId -> onSessionChanged(userId != null) }
        }

    // MARK: - View

    /** 처음 보일 때만 불러온다. */
    fun onAppear() = intent {
        if (state.invite != null) return@intent
        load()
    }

    fun onRetryTapped() = intent {
        reduce { state.copy(load = CrewJoinLoad.Loading) }
        load()
    }

    fun onCancelTapped() = intent { postSideEffect(CrewJoinSideEffect.CloseRequested) }

    fun onJoinTapped() = intent {
        if (state.load != CrewJoinLoad.Loaded || state.isJoining) return@intent
        if (!state.hasSession) {
            postSideEffect(CrewJoinSideEffect.LoginRequested(state.crewName))
            return@intent
        }
        reduce { state.copy(isJoining = true) }
        join()
    }

    fun onSessionChanged(hasSession: Boolean) = intent {
        if (state.hasSession == hasSession) return@intent
        reduce { state.copy(hasSession = hasSession) }
    }

    /** 다이얼로그 확인(iOS `alert(.presented(.confirmTapped))`). 나갈 크루가 있으면 나간 뒤 참여한다. */
    fun onAlertConfirmed() = intent {
        val pendingLeave = state.pendingLeave
        if (pendingLeave == null) {
            reduce { state.copy(alert = null) }
            return@intent
        }
        reduce { state.copy(alert = null, pendingLeave = null, isJoining = true) }
        leaveAndJoin(pendingLeave.crewId)
    }

    /** 다이얼로그 닫기 · 취소. 기존 크루에 그대로 남는다. */
    fun onAlertDismissed() = intent { reduce { state.copy(alert = null, pendingLeave = null) } }

    // MARK: - Async

    private suspend fun load() = subIntent {
        val result = crewCall { crewUseCase.invite(state.code) }
        result.fold(
            onSuccess = { invite -> reduce { state.copy(invite = invite, load = CrewJoinLoad.Loaded) } },
            onFailure = { onLoadFailed(it as FestivalCrewError) },
        )
    }

    private suspend fun join() = subIntent {
        val result = crewCall { crewUseCase.join(state.code) }
        result.fold(onSuccess = { onJoined(it) }, onFailure = { onJoinFailed(it as FestivalCrewError) })
    }

    /** 참여가 409(이미 다른 크루)로 막혔을 때 라이브 보드에서 지금 크루를 찾는다. 못 찾으면 null. */
    private suspend fun findCurrentCrew(festivalId: String) = subIntent {
        val crew =
            try {
                liveBoardUseCase.liveBoard(festivalId).crew
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        onCurrentCrewFound(crew?.let { CrewJoinPendingLeave(crewId = it.id, crewName = it.name) })
    }

    /** 기존 크루에서 나가지 못하면 참여하지 않는다. */
    private suspend fun leaveAndJoin(crewId: String) = subIntent {
        val left = crewCall { crewUseCase.leave(crewId) }
        left.exceptionOrNull()?.let {
            onLeaveFailed(it as FestivalCrewError)
            return@subIntent
        }
        join()
    }

    // MARK: - Inner

    private suspend fun onLoadFailed(error: FestivalCrewError) = subIntent {
        val unavailable = error is FestivalCrewError.NotFound || error is FestivalCrewError.FestivalEnded
        reduce { state.copy(load = if (unavailable) CrewJoinLoad.Unavailable else CrewJoinLoad.Failed) }
    }

    private suspend fun onJoined(crew: FestivalCrew) = subIntent {
        reduce { state.copy(isJoining = false) }
        postSideEffect(CrewJoinSideEffect.Joined(crewId = crew.id, festivalId = crew.festivalId))
    }

    private suspend fun onJoinFailed(error: FestivalCrewError) = subIntent {
        val invite = state.invite
        if (error is FestivalCrewError.AlreadyInCrew && invite != null) {
            // 참여 중 표시를 유지한 채 지금 크루를 찾아 S08 D 확인을 띄운다.
            findCurrentCrew(invite.festivalId)
            return@subIntent
        }
        if (error is FestivalCrewError.Unauthenticated) {
            reduce { state.copy(isJoining = false) }
            postSideEffect(CrewJoinSideEffect.LoginRequested(state.crewName))
            return@subIntent
        }
        reduce { state.copy(isJoining = false, alert = CrewJoinAlert(title = JOIN_FAILED_TITLE, message = message(error))) }
    }

    private suspend fun onCurrentCrewFound(pendingLeave: CrewJoinPendingLeave?) = subIntent {
        // 지금 크루를 찾지 못하면 나가기 없이 이전 안내만 띄운다.
        if (pendingLeave == null) {
            reduce {
                state.copy(
                    isJoining = false,
                    alert = CrewJoinAlert(title = JOIN_FAILED_TITLE, message = message(FestivalCrewError.AlreadyInCrew())),
                )
            }
            return@subIntent
        }
        reduce {
            state.copy(
                isJoining = false,
                pendingLeave = pendingLeave,
                alert =
                    CrewJoinAlert(
                        title = "기존 크루에서 나갈까요?",
                        message =
                            "지금 \"${pendingLeave.crewName}\"에 있어요. 페스티벌마다 크루는 하나만 들 수 있어서, " +
                                "나가야 ${state.invite?.crewName ?: "이 크루"}에 들어갈 수 있어요.",
                        confirmTitle = "나가고 참여하기",
                        cancelTitle = "취소",
                    ),
            )
        }
    }

    private suspend fun onLeaveFailed(error: FestivalCrewError) = subIntent {
        reduce {
            state.copy(
                isJoining = false,
                alert =
                    CrewJoinAlert(
                        title = "기존 크루에서 나가지 못했어요",
                        message =
                            if (error is FestivalCrewError.Forbidden) {
                                "크루장은 크루장을 넘기거나 크루를 해산한 뒤 나갈 수 있어요."
                            } else {
                                "연결을 확인한 뒤 다시 시도해 주세요."
                            },
                    ),
            )
        }
    }

    /** 크루 호출 결과. 크루 오류가 아닌 실패는 [FestivalCrewError.Unavailable]로 본다(iOS `error as? FestivalCrewError ?? .unavailable`). */
    private suspend fun <T> crewCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: FestivalCrewError) {
        Result.failure(error)
    } catch (_: Exception) {
        Result.failure(FestivalCrewError.Unavailable())
    }

    companion object {
        private const val JOIN_FAILED_TITLE = "크루에 참여하지 못했어요"

        /** iOS `FestivalCrewJoinFeature.message(_:)`. */
        internal fun message(error: FestivalCrewError): String = when (error) {
            is FestivalCrewError.AlreadyInCrew -> "이 페스티벌에서 이미 다른 크루에 있어요. 지금 크루에서 나간 뒤 다시 시도해 주세요."
            is FestivalCrewError.Full -> "크루 정원 ${FestivalCrew.MAX_MEMBERS}명이 모두 찼어요."
            is FestivalCrewError.NotFound, is FestivalCrewError.FestivalEnded -> "초대가 만료됐어요. 크루장에게 새 초대 링크를 받아 주세요."
            is FestivalCrewError.TermsRequired -> "최신 이용약관에 동의한 뒤 다시 시도해 주세요."
            else -> "연결을 확인한 뒤 다시 시도해 주세요."
        }
    }
}
