package io.festicore.feature.festival

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 라이브 탭 흐름의 화면. iOS `FestivalScreen`. 라이브 보드는 이후 단계에서 추가한다. */
@Serializable
sealed interface FestivalRoute : NavKey {
    /** S00 페스티벌 목록(iOS `festivalList`). 라이브 탭의 첫 화면이다. */
    @Serializable
    data object List : FestivalRoute
}
