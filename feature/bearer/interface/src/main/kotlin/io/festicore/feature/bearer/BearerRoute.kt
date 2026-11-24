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
}
