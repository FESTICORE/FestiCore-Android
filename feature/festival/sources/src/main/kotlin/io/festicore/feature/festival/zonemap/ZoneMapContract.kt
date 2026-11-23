package io.festicore.feature.festival.zonemap

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalZoneMap
import java.time.Instant

/** 받아 둔 팩의 지도 상태. iOS `FestivalZoneMapFeature.MapLoad`. */
sealed interface ZoneMapLoad {
    data object Loading : ZoneMapLoad

    data class Loaded(val map: FestivalZoneMap) : ZoneMapLoad

    /** 이 축제 팩을 받아 두지 않았다. */
    data object Missing : ZoneMapLoad

    data object Failed : ZoneMapLoad
}

/** S06 존 지도 State. iOS `FestivalZoneMapFeature.State`(라이브 부분 `Live`를 펼쳐 둔다). */
data class ZoneMapState(
    val festivalId: String,
    /** 로그인한 사용자. 크루 줄에 `나`를 붙인다. 계정이 바뀌면 Coordinator가 이 화면을 뺀다. */
    val viewerUserId: String?,
    val selectedZoneId: String? = null,
    val map: ZoneMapLoad = ZoneMapLoad.Loading,
    /** 보드가 마지막으로 받은 라이브 상태로 먼저 채우고(설계 E3) 감시 결과로 바꾼다. */
    val snapshot: FestivalLiveBoardSnapshot? = null,
    /** 마지막으로 받은 시각. 오프라인 띠에 쓴다. */
    val receivedAt: Instant? = null,
    val isOffline: Boolean = false,
    /** 서버 지도 버전이 받아 둔 지도와 다르다(S06 J). */
    val isMapChanged: Boolean = false,
    val isUpdatingPack: Boolean = false,
    val isVisible: Boolean = false,
    val isForeground: Boolean = true,
    val hasStarted: Boolean = false,
) {
    val loadedMap: FestivalZoneMap? get() = (map as? ZoneMapLoad.Loaded)?.map
}

sealed interface ZoneMapSideEffect {
    /** iOS `delegate(.backRequested)`. */
    data object BackRequested : ZoneMapSideEffect

    /** "깃발을 팔로우하면…" 띠. Coordinator가 pop하고 깃발 탭을 요청한다(iOS `delegate(.flagDirectoryRequested)`, 설계 E6). */
    data object FlagDirectoryRequested : ZoneMapSideEffect
}
