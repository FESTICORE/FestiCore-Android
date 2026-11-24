package io.festicore.feature.bearer.plan

import androidx.lifecycle.ViewModel
import io.festicore.domain.bearer.entity.BearerSet
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * iOS `BearerPlanPickFeature`. 깃발 프로필(S10)의 '오늘 계획' 고르기. 고른 셋을 프로필에 돌려준다.
 * 서버 저장은 프로필 저장(`PATCH flags/:id`의 plan)이 맡는다.
 *
 * @param sets 받아 둔 팩의 공연(프로필 State의 `sets`)
 * @param plan 이미 고른 오늘 계획(프로필 State의 `plan`)
 */
class BearerPlanPickViewModel(
    sets: List<BearerSet>,
    plan: List<String>,
) : ViewModel(),
    OrbitContainerHost<BearerPlanPickState, BearerPlanPickState, BearerPlanPickSideEffect> {
    override val container =
        orbitContainer<BearerPlanPickState, BearerPlanPickSideEffect>(BearerPlanPickState.initial(sets = sets, plan = plan))

    fun onDayTapped(index: Int) = intent {
        if (index !in state.days.indices) return@intent
        reduce { state.copy(selectedDayIndex = index) }
    }

    fun onSetTapped(setId: String) = intent {
        val selected = state.selectedSetIds
        when {
            setId in selected -> reduce { state.copy(selectedSetIds = selected - setId, limitMessage = null) }

            selected.size >= BearerPlanPickState.PLAN_LIMIT ->
                reduce { state.copy(limitMessage = "오늘 계획은 ${BearerPlanPickState.PLAN_LIMIT}개까지 고를 수 있어요.") }

            else -> reduce { state.copy(selectedSetIds = selected + setId) }
        }
    }

    fun onDoneTapped() = intent {
        postSideEffect(BearerPlanPickSideEffect.Picked(setIds = state.orderedPlan))
    }

    fun onBackTapped() = intent {
        postSideEffect(BearerPlanPickSideEffect.CloseRequested)
    }
}
