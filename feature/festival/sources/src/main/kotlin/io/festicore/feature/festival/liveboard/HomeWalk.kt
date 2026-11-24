package io.festicore.feature.festival.liveboard

import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingPlace
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalPackDocument
import java.time.Instant

/** 현재 위치에서 귀가 카드의 역 · 셔틀 정류장까지 걸리는 시간을 구하는 단계. iOS `FestivalFeature.HomeWalkState`. */
sealed interface HomeWalkState {
    /** 귀가 카드가 아직 보이지 않았다. */
    data object Idle : HomeWalkState

    /** 권한 확인과 경로 계산 중. 다시 계산하는 동안 직전 결과를 계속 보인다. */
    data class Estimating(
        val festivalId: String,
        val last: HomeWalkMinutes?,
    ) : HomeWalkState

    /** 지도로 계산한 도보 분. 계산하지 못한 목적지는 null이다. */
    data class Estimated(
        val festivalId: String,
        val minutes: HomeWalkMinutes,
    ) : HomeWalkState

    /** 위치 권한이 없거나 현재 위치를 잡지 못했다. 서버 값으로 보인다. */
    data class Unavailable(val festivalId: String) : HomeWalkState

    /** 계산을 멈췄을 때의 상태. 계산 중이었으면 직전 결과로 되돌린다. */
    val stopped: HomeWalkState
        get() {
            if (this !is Estimating) return this
            return last?.let { Estimated(festivalId, it) } ?: Idle
        }

    fun isEstimating(festivalId: String): Boolean = this is Estimating && this.festivalId == festivalId

    /** 이 축제에서 지금 쓸 수 있는 지도 계산값 */
    fun minutes(festivalId: String): HomeWalkMinutes? = when {
        this is Estimating && this.festivalId == festivalId -> last
        this is Estimated && this.festivalId == festivalId -> minutes
        else -> null
    }
}

/** 도보 시간을 계산하고 지도 앱으로 안내할 목적지. iOS `FestivalFeature.HomeWalkPlaces`. */
internal data class HomeWalkPlaces(
    val station: WalkingPlace? = null,
    val shuttleStop: WalkingPlace? = null,
) {
    val isEmpty: Boolean get() = station == null && shuttleStop == null
}

/** 귀가 카드가 읽는 받은 팩의 가는 길(iOS `homeDetails`는 받은 팩의 상세를 읽는다). */
internal val LiveBoardState.homeDirections: FestivalPackDocument.Directions?
    get() = displayDocument?.id?.let { cachedPacks[it]?.document?.directions }

/** 도보 시간을 계산할 역 · 셔틀 정류장. 이름이 없는 목적지는 빼고, 축제 장소를 검색 기준으로 쓴다(iOS `homeWalkPlaces`). */
internal val LiveBoardState.homeWalkPlaces: HomeWalkPlaces
    get() {
        val directions = homeDirections ?: return HomeWalkPlaces()
        val venue = directions.venueLocation?.toLocationCoordinate()
        return HomeWalkPlaces(
            station = walkingPlace(directions.transitStationName, directions.transitLocation, venue),
            shuttleStop = walkingPlace(directions.shuttleStopName, directions.shuttleLocation, venue),
        )
    }

/**
 * 귀가 카드에 보일 도보 분. 지도 계산값을 먼저 쓰고, 계산하지 못한 역은 서버 값으로 대신한다(iOS `homeWalkMinutes`).
 * 셔틀 정류장은 서버 값이 없어 계산하지 못하면 비워 둔다.
 */
internal val LiveBoardState.homeWalkMinutes: HomeWalkMinutes
    get() {
        val directions = homeDirections ?: return HomeWalkMinutes()
        val estimated = homeWalk.minutes(festivalId)
        return HomeWalkMinutes(
            station = estimated?.station ?: directions.transitWalkMinutes,
            shuttleStop = estimated?.shuttleStop,
        )
    }

/** "가는 길"이 지도 앱으로 안내할 목적지. 카드가 안내하는 출발편의 역 · 정류장을 먼저 고른다(iOS `homeDirectionsPlace(now:)`). */
internal fun LiveBoardState.homeDirectionsPlace(now: Instant): WalkingPlace? {
    val places = homeWalkPlaces
    val departure = homeDirections?.let { HomeDeparture.of(it, homeWalkMinutes, now) }
    return when (departure?.kind) {
        HomeDeparture.Kind.Shuttle -> places.shuttleStop ?: places.station
        HomeDeparture.Kind.Train, null -> places.station ?: places.shuttleStop
    }
}

private fun walkingPlace(
    name: String?,
    location: FestivalPackDetail.Coordinate?,
    searchCenter: LocationCoordinate?,
): WalkingPlace? {
    name ?: return null
    return WalkingPlace(name = name, coordinate = location?.toLocationCoordinate(), searchCenter = searchCenter)
}

private fun FestivalPackDetail.Coordinate.toLocationCoordinate() = LocationCoordinate(latitude, longitude)
