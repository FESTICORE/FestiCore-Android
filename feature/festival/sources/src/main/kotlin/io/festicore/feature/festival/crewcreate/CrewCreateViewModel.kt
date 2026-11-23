package io.festicore.feature.festival.crewcreate

import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.crew.FestivalCrewUseCase
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S07 B/D 크루 만들기(iOS `FestivalCrewCreateFeature`). 함수 이름은 iOS `Action.View`와 같고, 순서는 view → async → inner를 따른다.
 * 만들면 같은 화면이 초대 단계로 바뀐다. 보드 크루 카드는 보드가 다시 보일 때 새로 받는다(spec "화면 전환").
 */
class CrewCreateViewModel(
    festivalId: String,
    private val crewUseCase: FestivalCrewUseCase,
    boardSelectionUseCase: FestivalBoardSelectionUseCase,
) : ViewModel(),
    OrbitContainerHost<CrewCreateState, CrewCreateState, CrewCreateSideEffect> {
    override val container =
        orbitContainer<CrewCreateState, CrewCreateSideEffect>(
            CrewCreateState(
                festivalId = festivalId,
                // iOS: 보드가 고른 축제와 같을 때만 보드 표시 문서의 이름을 넣는다.
                festivalName = boardSelectionUseCase.selection.value?.takeIf { it.festivalId == festivalId }?.document?.name,
            ),
        )

    // MARK: - View

    fun onNameChanged(name: String) = intent {
        reduce { state.copy(name = name.take(FestivalCrew.MAX_NAME_LENGTH), errorMessage = null) }
    }

    fun onSubmitTapped() = intent { submit() }

    /** 제출 중에는 닫지 않는다. */
    fun onCloseTapped() = intent {
        if (state.isSubmitting) return@intent
        postSideEffect(CrewCreateSideEffect.CloseRequested)
    }

    fun onOpenCrewTapped() = intent {
        val crew = (state.step as? CrewCreateStep.Created)?.crew ?: return@intent
        postSideEffect(CrewCreateSideEffect.OpenCrewRequested(crew.id))
    }

    /** 다이얼로그 "다시 시도"(iOS `alert(.presented(.confirmTapped))` → `submitTapped`). */
    fun onRetryConfirmed() = intent {
        reduce { state.copy(isRetryDialogPresented = false) }
        submit()
    }

    /** 다이얼로그 "닫기". 이름은 그대로 둔다. */
    fun onRetryDismissed() = intent { reduce { state.copy(isRetryDialogPresented = false) } }

    // MARK: - Async

    private suspend fun submit() = subIntent {
        if (!state.canSubmit) return@subIntent
        reduce { state.copy(isSubmitting = true) }
        create(state.festivalId, state.name.trim())
    }

    private suspend fun create(festivalId: String, name: String) = subIntent {
        val result =
            try {
                Result.success(crewUseCase.create(festivalId, name))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: FestivalCrewError) {
                Result.failure(error)
            } catch (_: Exception) {
                Result.failure(FestivalCrewError.Unavailable())
            }
        result.fold(onSuccess = { onCreateSucceeded(it) }, onFailure = { onCreateFailed(it as FestivalCrewError) })
    }

    // MARK: - Inner

    private suspend fun onCreateSucceeded(crew: FestivalCrew) = subIntent {
        reduce { state.copy(isSubmitting = false, step = CrewCreateStep.Created(crew)) }
    }

    private suspend fun onCreateFailed(error: FestivalCrewError) = subIntent {
        reduce { state.copy(isSubmitting = false) }
        if (error is FestivalCrewError.Unauthenticated) {
            postSideEffect(CrewCreateSideEffect.LoginRequested)
            return@subIntent
        }
        val message = message(error)
        reduce { if (message != null) state.copy(errorMessage = message) else state.copy(isRetryDialogPresented = true) }
    }

    companion object {
        /** 다시 시도해도 같은 실패. 입력란 아래에 알린다. 일시 오류는 null(다시 시도 다이얼로그). */
        internal fun message(error: FestivalCrewError): String? = when (error) {
            is FestivalCrewError.AlreadyInCrew -> "이 페스티벌에서 이미 크루에 있어요. 크루는 페스티벌마다 하나만 만들거나 참여할 수 있어요."
            is FestivalCrewError.FestivalEnded -> "끝난 페스티벌에는 크루를 만들 수 없어요."
            is FestivalCrewError.InvalidInput -> "크루 이름을 1~${FestivalCrew.MAX_NAME_LENGTH}자로 입력해 주세요."
            is FestivalCrewError.TermsRequired -> "최신 이용약관에 동의한 뒤 다시 시도해 주세요."
            else -> null
        }
    }
}
