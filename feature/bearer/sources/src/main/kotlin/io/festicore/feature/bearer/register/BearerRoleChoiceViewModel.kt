package io.festicore.feature.bearer.register

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** iOS `BearerRoleChoiceFeature`(Figma S14 C · C2). 관객이면 흐름을 마치고, 기수면 깃발 등록(D)으로 간다. */
class BearerRoleChoiceViewModel :
    ViewModel(),
    OrbitContainerHost<BearerRoleChoiceState, BearerRoleChoiceState, BearerRoleChoiceSideEffect> {
    override val container = orbitContainer<BearerRoleChoiceState, BearerRoleChoiceSideEffect>(BearerRoleChoiceState())

    fun onRoleTapped(role: BearerRole) = intent {
        reduce { state.copy(role = role) }
    }

    fun onNextTapped() = intent {
        when (state.role) {
            BearerRole.Audience -> postSideEffect(BearerRoleChoiceSideEffect.AudienceChosen)
            BearerRole.Bearer -> postSideEffect(BearerRoleChoiceSideEffect.BearerChosen)
            null -> Unit
        }
    }

    fun onBackTapped() = intent {
        postSideEffect(BearerRoleChoiceSideEffect.CloseRequested)
    }
}
