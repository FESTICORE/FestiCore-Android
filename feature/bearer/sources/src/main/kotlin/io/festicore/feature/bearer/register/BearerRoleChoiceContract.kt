package io.festicore.feature.bearer.register

/** iOS `BearerRoleChoiceFeature.Role`. 화면에 보이는 순서다. */
enum class BearerRole {
    Audience,
    Bearer,
}

/** iOS `BearerRoleChoiceFeature.State`(Figma S14 C · C2 기수 여부). */
data class BearerRoleChoiceState(val role: BearerRole? = null)

/** iOS `BearerRoleChoiceDelegate`. */
sealed interface BearerRoleChoiceSideEffect {
    /** '관객이에요'로 다음을 눌렀다. */
    data object AudienceChosen : BearerRoleChoiceSideEffect

    /** '기수예요'로 다음을 눌렀다. 부모가 깃발 프로필 등록을 연다. */
    data object BearerChosen : BearerRoleChoiceSideEffect

    data object CloseRequested : BearerRoleChoiceSideEffect
}
