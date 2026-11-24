package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalLiveBoardSchedule
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.festivallist.FestivalListPresentation
import java.time.Instant

/** 라이브 보드 화면에 필요한 계산 값만 모은 표시 모델. iOS `FestivalLiveBoardScreenModel`. */
internal class LiveBoardScreenModel(private val state: LiveBoardState) {
    data class HomeRide(
        val remain: String,
        /** 셔틀 · 막차 시각이 없거나 읽을 수 없으면 null이고 해당 줄을 숨긴다. */
        val shuttle: String?,
        val train: String?,
    )

    data class DoneSummary(
        val seenSetCount: Int,
        val crewCount: Int,
        val followedFlagCount: Int,
    )

    val pack: FestivalPackDocument? get() = state.displayDocument

    val showsOfflineStatus: Boolean
        get() = (state.isUnavailable && !state.isUnpublished) ||
            state.connection == LiveBoardConnection.Offline ||
            state.connection == LiveBoardConnection.Reconnecting

    /** 선택 축제의 팩을 받는 중이면 진행 표시(목록에서 시작한 받기도 같은 값, 설계 D1). */
    val packDownload: FestivalListPresentation.PackDownloadDisplay?
        get() = FestivalListPresentation.packDownloadDisplay(state.packDownloads, state.festivalId)

    private val directions: FestivalPackDocument.Directions? get() = state.homeDirections

    /** 지도 계산값, 없으면 서버의 역 도보 분(iOS `homeWalkMinutes`). */
    private val walkMinutes: HomeWalkMinutes get() = state.homeWalkMinutes

    fun homeRide(now: Instant): HomeRide? {
        val directions = directions ?: return null
        val departure = HomeDeparture.of(directions, walkMinutes, now) ?: return null
        val shuttleAt = validTime(directions.lastShuttleAt)
        val trainAt = validTime(directions.lastTrainAt)
        val shuttleStation = directions.shuttleStopName?.let { " · $it" }.orEmpty()
        val trainStation = directions.transitStationName?.let { " · $it" }.orEmpty()
        val shuttleWalk = walkText(walkMinutes.shuttleStop, walkMinutes.shuttleStopDriving)
        val trainWalk = walkText(walkMinutes.station, walkMinutes.stationDriving)
        return HomeRide(
            remain = "${departure.name}까지 ${departure.remainingMinutes(now)}분",
            shuttle = shuttleAt?.let { "마지막 셔틀 ${timeText(it)}$shuttleStation$shuttleWalk" },
            train = trainAt?.let { "막차 ${timeText(it)}$trainStation$trainWalk" },
        )
    }

    fun doneSummary(now: Instant): DoneSummary? {
        val snapshot = state.snapshot
        val pack = pack
        if (!state.hasSession || state.mySchedule == null || snapshot == null || pack == null) return null
        val entries = FestivalLiveBoardSchedule.entries(pack)
        val lastPerformanceDay = (entries.lastOrNull()?.slot?.startsAt ?: now).atZone(LiveBoardPresentation.zone).toLocalDate()
        val seenSetCount =
            entries.count { entry ->
                entry.slot.id in state.mySetIds &&
                    entry.slot.startsAt.atZone(LiveBoardPresentation.zone).toLocalDate() == lastPerformanceDay &&
                    (entry.slot.endsAt ?: entry.slot.startsAt) <= now
            }
        return DoneSummary(
            seenSetCount = seenSetCount,
            crewCount = snapshot.crew?.members?.size ?: 0,
            followedFlagCount = snapshot.followedFlags.size,
        )
    }

    private fun timeText(value: String): String = FestivalIsoTime.parse(value)?.let(LiveBoardPresentation::time) ?: value

    /** " (도보 N분)", 자동차 분이 있으면 " (도보 N분 · 차로 M분)"(자동차는 iOS에 없는 사용자 요청 표시, 설계 R6 쟁점 F4). */
    private fun walkText(walking: Int?, driving: Int?): String {
        val parts = listOfNotNull(walking?.let { "도보 ${it}분" }, driving?.let { "차로 ${it}분" })
        return if (parts.isEmpty()) "" else parts.joinToString(" · ", prefix = " (", postfix = ")")
    }

    private fun validTime(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed.takeIf { FestivalIsoTime.parse(it) != null }
    }
}
