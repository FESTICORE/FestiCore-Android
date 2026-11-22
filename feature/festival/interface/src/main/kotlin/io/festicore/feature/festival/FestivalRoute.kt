package io.festicore.feature.festival

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 라이브 탭 흐름의 화면. iOS `FestivalScreen`. */
@Serializable
sealed interface FestivalRoute : NavKey {
    /** S00 페스티벌 목록(iOS `festivalList`). 라이브 탭의 첫 화면이다. */
    @Serializable
    data object List : FestivalRoute

    /** S01 라이브 보드(iOS `liveBoard`). 목록 항목 · 팩은 보드가 다시 읽는다(경로 값은 ID 하나). */
    @Serializable
    data class LiveBoard(val festivalId: String) : FestivalRoute
}
