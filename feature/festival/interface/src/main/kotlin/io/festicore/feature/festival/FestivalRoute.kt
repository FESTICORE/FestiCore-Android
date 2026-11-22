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

    /** S06 존 지도(iOS `zoneMap`). 보드 크루 카드의 존 칩에서 연다. [zoneId]는 처음 고를 존. 라이브 상태는 화면이 다시 읽는다(설계 E3). */
    @Serializable
    data class ZoneMap(
        val festivalId: String,
        val zoneId: String?,
    ) : FestivalRoute
}
