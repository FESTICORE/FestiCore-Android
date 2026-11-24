package io.festicore.feature.bearer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 기수 흐름의 화면. iOS `BearerEntryStep`(App `Navigation/Bearer`).
 * 경로 연결 · Coordinator는 BC1(`app` `navigation/bearer/BearerEntryCoordinator`)에서 한다. 나머지 화면 경로는 각 화면 PR(BS2~BS4)에서 더한다.
 */
@Serializable
sealed interface BearerRoute : NavKey {
    /** 기수 인증 코드(iOS `claim`, Figma S13 F~I). [festivalId] 축제의 코드를 받는다. */
    @Serializable
    data class Claim(val festivalId: String) : BearerRoute

    /**
     * 깃발 프로필(iOS `profile`, Figma S10 A~E). 코드 등록 응답의 프로필은 경로에 싣지 않고 Coordinator가 ViewModel 인자로 넘긴다(없으면 서버에서 읽는다).
     * [isNewBearer]는 코드로 막 기수가 된 경우다.
     */
    @Serializable
    data class Profile(
        val festivalId: String,
        val flagId: String,
        val isNewBearer: Boolean = false,
    ) : BearerRoute

    /** 오늘 계획 고르기(iOS `planPick`). 팩 공연 · 고른 계획은 Coordinator가 프로필 State에서 ViewModel 인자로 넘긴다. */
    @Serializable
    data class PlanPick(val festivalId: String) : BearerRoute
}
