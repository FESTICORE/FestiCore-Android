package io.festicore.feature.festival.flagdetail

import io.festicore.domain.festival.FestivalFlagDetail
import io.festicore.feature.festival.flag.FlagPresentation
import io.festicore.feature.festival.flag.FlagPresentation.title
import io.festicore.feature.festival.flag.FlagTag
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 지금 카드(iOS `FestivalFlagDetailNow`). */
sealed interface FlagDetailNow {
    /** 현재 위치와 활동. [isMuted]면 오래된 정보 · 오프라인이라 흐리게 보인다. */
    data class Located(
        val zone: String,
        val activity: String,
        val source: String,
        val isMuted: Boolean,
    ) : FlagDetailNow

    /** 오늘 활동이 끝났다. */
    data object Ended : FlagDetailNow
}

/** 오늘 계획 한 줄(iOS `FestivalFlagDetailPlanRow`). */
data class FlagDetailPlanRow(
    val id: String,
    val time: String,
    val artist: String,
    val stage: String,
    val timing: Timing,
    val isMine: Boolean,
) {
    enum class Timing { Upcoming, Now, Past }
}

/** 깃발 상세 화면 파생 값(iOS `FestivalFlagDetailFeature.State` 확장). */
internal object FlagDetailPresentation {
    /** 이 시간 이상 지난 위치는 지금과 다를 수 있다고 알린다. */
    val STALE_INTERVAL: Duration = Duration.ofMinutes(30)

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA).withZone(ZoneId.of("Asia/Seoul"))

    /** 히어로 태그. 서버 태그 코드를 목록과 같은 이름으로 바꾸고, 모르는 코드는 그대로 보인다. */
    fun tagTitles(state: FlagDetailState): List<String> = state.detail?.tags.orEmpty().map { FlagTag.of(it)?.title ?: it }

    /** 상단 오프라인 안내. 받은 상세가 있는데 다시 불러오지 못했을 때만 보인다. */
    fun offlineText(state: FlagDetailState): String? =
        if (state.isOffline) FlagPresentation.offlineText(state.lastLoadedAt, state.now) else null

    fun nowCard(state: FlagDetailState): FlagDetailNow? {
        val detail = state.detail ?: return null
        if (detail.status == FestivalFlagDetail.Status.Ended) return FlagDetailNow.Ended
        val zone = (detail.zoneId ?: detail.homeZoneId)?.let { state.context.zoneNames[it] } ?: "위치를 아직 알리지 않았어요"
        val isStale = Duration.between(detail.statusUpdatedAt, state.now) >= STALE_INTERVAL
        return FlagDetailNow.Located(
            zone = zone,
            activity = activityText(detail),
            source = sourceText(detail, state, isStale),
            // Figma S05 F: 쉬는 중은 기본 카드로 보이고, 오래된 정보 · 오프라인만 흐리게 보인다.
            isMuted = isStale || state.isOffline,
        )
    }

    /** 기수가 정한 순서를 그대로 두고, 받은 팩에 없는 공연은 건너뛴다. */
    fun planRows(state: FlagDetailState): List<FlagDetailPlanRow> {
        val detail = state.detail ?: return emptyList()
        val isEnded = detail.status == FestivalFlagDetail.Status.Ended
        return detail.planSetIds.mapNotNull { id ->
            val performance = state.context.performances[id] ?: return@mapNotNull null
            FlagDetailPlanRow(
                id = id,
                time = timeFormatter.format(performance.startsAt),
                artist = performance.artist,
                stage = performance.stageName,
                timing = timing(performance, isEnded, state),
                isMine = state.context.mySetIds.contains(id),
            )
        }
    }

    private fun activityText(detail: FestivalFlagDetail): String {
        if (detail.status == FestivalFlagDetail.Status.Resting) return "쉬는 중"
        // 시작 전 깃발은 활동 값이 남아 있어도 예정으로 보인다.
        if (detail.status == FestivalFlagDetail.Status.Scheduled) return "예정"
        return when (detail.activity) {
            FestivalFlagDetail.Activity.Slam -> "슬램"
            FestivalFlagDetail.Activity.Sing -> "떼창"
            FestivalFlagDetail.Activity.Train -> "기차놀이"
            FestivalFlagDetail.Activity.Calm -> "차분"
            FestivalFlagDetail.Activity.Resting -> "쉬는 중"
            null -> "활동 중"
        }
    }

    private fun sourceText(
        detail: FestivalFlagDetail,
        state: FlagDetailState,
        isStale: Boolean,
    ): String {
        if (state.isOffline) return "마지막으로 받은 정보예요"
        val relative = LiveBoardPresentation.relativeTime(detail.statusUpdatedAt, state.now) ?: "방금"
        if (isStale) return "$relative 정보예요 · 지금은 다를 수 있어요"
        return if (relative == "방금") "기수가 방금 알렸어요" else "기수가 ${relative}에 알렸어요"
    }

    private fun timing(
        performance: FlagDetailContext.Performance,
        isEnded: Boolean,
        state: FlagDetailState,
    ): FlagDetailPlanRow.Timing {
        if (isEnded || performance.endsAt <= state.now) return FlagDetailPlanRow.Timing.Past
        return if (performance.startsAt <= state.now) FlagDetailPlanRow.Timing.Now else FlagDetailPlanRow.Timing.Upcoming
    }
}
