package io.festicore.feature.festival

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 깃발 탭 흐름의 화면. iOS `FlagCoordinator.FlagScreen`. */
@Serializable
sealed interface FlagRoute : NavKey {
    /** S04 깃발 목록(iOS `directory`). 깃발 탭의 첫 화면이다. */
    @Serializable
    data object Directory : FlagRoute

    /** S05 깃발 상세(iOS `detail`). 스택에 하나만 둔다. 표시 문맥은 흐름이 따로 받는다. */
    @Serializable
    data class Detail(val flagId: String) : FlagRoute
}

/**
 * 깃발 탭 목록이 따르는 입력(iOS MainTab이 깃발 탭에 넘기는 `festivalChanged` · `accountChanged` · `zoneNamesChanged`).
 * 상위(메인 탭)가 라이브 탭 상태 · 세션에서 만든다.
 *
 * @param selectedFestivalId 라이브 스택에서 뒤에서부터 찾은 첫 보드의 축제. 없으면 `null`
 * @param accountId 로그인 계정. 바뀌면 목록이 이전 계정 값을 비운다(스택은 그대로)
 * @param zoneNames 선택 축제 팩의 존 ID → 이름
 */
data class FlagTabInput(
    val selectedFestivalId: String? = null,
    val accountId: String? = null,
    val zoneNames: Map<String, String> = emptyMap(),
)

/** 깃발 탭이 상위에 요청하는 로그인의 문맥(iOS `loginRequested(nil)` · `.follow` · `.report`). `null`이면 기본 문구다. */
enum class FlagLoginContext { Follow, Report }
