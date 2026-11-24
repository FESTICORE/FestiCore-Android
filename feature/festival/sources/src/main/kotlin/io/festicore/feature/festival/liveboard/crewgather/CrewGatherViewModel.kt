package io.festicore.feature.festival.liveboard.crewgather

import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.crew.FestivalCrewUseCase
import io.festicore.domain.festival.entity.FestivalCrewError
import java.time.Clock
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S07 E "모여!" 존 고르기(iOS `FestivalCrewGatherFeature`). 존을 누르면 바로 집결을 요청하고, 실패해도 시트를 유지한다.
 * [clock]은 테스트에서 쿨다운 기준 시각을 정하려고 주입한다.
 */
class CrewGatherViewModel(
    input: CrewGatherInput,
    private val crewUseCase: FestivalCrewUseCase,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel(),
    OrbitContainerHost<CrewGatherState, CrewGatherState, CrewGatherSideEffect> {
    override val container =
        orbitContainer<CrewGatherState, CrewGatherSideEffect>(
            CrewGatherState(
                crewId = input.crewId,
                zones = input.zones,
                availableAt = input.availableAt,
                recipientCount = input.recipientCount,
            ),
        )

    // MARK: - View

    /** Figma S07 E: 공식 존이 아닌 값 · 제출 중은 무시한다. 쿨다운이면 서버를 부르지 않고 안내한다. */
    fun onZoneTapped(zoneId: String) = intent {
        if (state.isSubmitting || state.zones.none { it.id == zoneId }) return@intent
        val availableAt = state.availableAt
        if (availableAt != null && availableAt.isAfter(clock.instant())) {
            reduce { state.copy(alert = CrewGatherAlert("잠시 후 다시 모일 수 있어요", "크루 집결은 10분에 한 번 요청할 수 있어요.")) }
            return@intent
        }
        reduce { state.copy(selectedZoneId = zoneId, isSubmitting = true) }
        gather(state.crewId, zoneId)
    }

    fun onAlertDismissed() = intent { reduce { state.copy(alert = null) } }

    // MARK: - Async

    private suspend fun gather(crewId: String, zoneId: String) = subIntent {
        val failure =
            try {
                crewUseCase.gather(crewId, zoneId)
                null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: FestivalCrewError) {
                error
            } catch (_: Exception) {
                FestivalCrewError.Unavailable()
            }
        if (failure == null) onSucceeded() else onFailed(failure)
    }

    // MARK: - Inner

    private suspend fun onSucceeded() = subIntent {
        reduce { state.copy(isSubmitting = false) }
        postSideEffect(CrewGatherSideEffect.Gathered)
    }

    private suspend fun onFailed(error: FestivalCrewError) = subIntent {
        reduce { state.copy(isSubmitting = false, alert = CrewGatherAlert("집결을 요청하지 못했어요", message(error))) }
    }

    companion object {
        internal fun message(error: FestivalCrewError): String = when (error) {
            is FestivalCrewError.Cooldown -> "크루 집결은 10분에 한 번 요청할 수 있어요. 잠시 후 다시 시도해 주세요."
            is FestivalCrewError.Unauthenticated -> "로그인 상태를 확인해 주세요."
            is FestivalCrewError.TermsRequired -> "최신 이용약관에 동의한 뒤 다시 시도해 주세요."
            is FestivalCrewError.Forbidden -> "현재 크루 멤버만 집결을 요청할 수 있어요."
            is FestivalCrewError.FestivalEnded -> "종료된 페스티벌에서는 집결을 요청할 수 없어요."
            else -> "연결과 선택한 존을 확인한 뒤 다시 시도해 주세요."
        }
    }
}
