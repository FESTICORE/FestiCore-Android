package io.festicore.feature.festival.widget

import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalWidgetSnapshot

internal enum class WidgetPhase { NoPack, Unavailable, Before, Playing, GoingHome, Finished, Unknown }
internal data class FestivalWidgetPresentation(
    val phase: WidgetPhase,
    val title: String,
    val subtitle: String = "",
    val footer: String = "앱에서 일정 보기",
    val rows: List<String> = emptyList(),
    val festivalId: String? = null,
    val info: String = "",
)
private val widgetZone = java.time.ZoneId.of("Asia/Seoul")
private fun dayOf(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(widgetZone).toLocalDate()
private fun clock(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(widgetZone)
    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

internal fun presentWidget(
    snapshot: FestivalWidgetSnapshot?,
    now: Long,
    unavailable: Boolean = false,
): FestivalWidgetPresentation {
    if (unavailable) {
        return FestivalWidgetPresentation(
            WidgetPhase.Unavailable,
            "정보를 불러올 수 없어요",
            snapshot?.festivalName ?: "앱에서 다시 확인해 주세요",
            festivalId = snapshot?.festivalId,
            info = "저장 정보 확인 필요 · 앱에서 다시 확인",
        )
    }
    if (snapshot == null) return FestivalWidgetPresentation(WidgetPhase.NoPack, "팩을 받아요", "오프라인에서도 축제를 즐겨요", "축제 둘러보기")
    val sets = snapshot.performances.sortedWith(compareBy({ it.startsAt }, { it.id }))
    val openings = snapshot.days.filter { it.opensAt != null }.sortedBy { it.opensAt }
    fun festivalDay(set: FestivalWidgetSnapshot.Performance) =
        openings.lastOrNull { it.opensAt!! <= set.startsAt }?.date ?: dayOf(set.startsAt).toString()
    fun nextStageStart(set: FestivalWidgetSnapshot.Performance) =
        sets.firstOrNull { it.stageId == set.stageId && it.startsAt > set.startsAt && festivalDay(it) == festivalDay(set) }?.startsAt
    val active = sets.filter { it.startsAt <= now && (it.endsAt ?: nextStageStart(it))?.let { end -> now < end } == true }
    val currentOpening = openings.lastOrNull { it.opensAt!! <= now }
    val todayBeforeOpening = openings.firstOrNull { it.date == dayOf(now).toString() && it.opensAt!! > now }
        ?.takeUnless { sets.any { set -> festivalDay(set) == currentOpening?.date && (set.endsAt ?: Long.MAX_VALUE) > now } }
    val date = (active.firstOrNull() ?: sets.firstOrNull { it.endsAt == now })?.let(::festivalDay)
        ?: todayBeforeOpening?.date ?: currentOpening?.date ?: openings.firstOrNull()?.date ?: dayOf(now).toString()
    val daySets = sets.filter { festivalDay(it) == date }
    val opening = snapshot.days.firstOrNull { it.date == date }?.opensAt
    val lastEnd = daySets.mapNotNull { it.endsAt }.maxOrNull()
    val unresolved = daySets.filter { it.endsAt == null && nextStageStart(it) == null }
    val remainingUnknown = unresolved.isNotEmpty()
    val nextOpening = openings.firstOrNull { it.date > date }?.opensAt
    val unknown = unresolved.lastOrNull { set ->
        val safeUntil = nextOpening ?: dayOf(set.startsAt).plusDays(1).atStartOfDay(widgetZone).toInstant().toEpochMilli()
        set.startsAt <= now && now < safeUntil && now >= (daySets.maxOfOrNull { it.startsAt } ?: now)
    }.takeIf { active.none { it.endsAt != null } }
    val upcoming = sets.filter { it.startsAt > now }
    val nextMine = upcoming.firstOrNull { it.isSaved }
    val stage = nextMine?.stageId ?: daySets.firstOrNull()?.stageId
    val current = active.firstOrNull { it.stageId == stage } ?: active.firstOrNull()
    val next = nextMine ?: upcoming.firstOrNull { it.stageId == stage } ?: upcoming.firstOrNull()
    val rows = (if (snapshot.scheduleKnownForOwner) sets.filter { it.isSaved } else sets)
        .filter { (it.endsAt ?: it.startsAt) >= now }.take(4)
        .map { "${clock(it.startsAt)}  ${it.artist} · ${it.stageName}" }
    val footer = "앱에서 일정 보기"
    fun model(
        phase: WidgetPhase,
        title: String,
        subtitle: String,
        destination: String? = snapshot.festivalId,
    ) = FestivalWidgetPresentation(
        phase,
        title,
        subtitle,
        if (phase == WidgetPhase.Finished && destination == null) "축제 둘러보기" else footer,
        rows,
        destination,
        listOfNotNull(
            nextMine?.let { "다음 내 일정 ${clock(it.startsAt)} ${it.artist}" },
            snapshot.pinnedFlagSummary,
            snapshot.crewSummary,
        ).joinToString(" · "),
    )
    if (unknown != null) return model(WidgetPhase.Unknown, "공연 종료 시각 미정", "${unknown.artist} · ${clock(unknown.startsAt)} 시작 · 앱에서 확인")
    if (opening != null &&
        now < opening
    ) {
        return model(
            WidgetPhase.Before,
            "곧 시작해요!",
            "${clock(opening)} 개장" + (nextMine?.let { " · ${clock(it.startsAt)} ${it.artist}" } ?: ""),
        )
    }
    if (lastEnd != null && !remainingUnknown && now >= lastEnd) {
        val isLast = snapshot.days.none { it.date > date } && sets.none { festivalDay(it) > date }
        return model(
            WidgetPhase.Finished,
            "오늘 끝!",
            if (isLast) {
                "다음 축제에서 만나요"
            } else {
                nextOpening?.let { "다음 개장 ${clock(it)}" }
                    ?: "다음 공연도 함께해요"
            },
            if (isLast) null else snapshot.festivalId,
        )
    }
    if (lastEnd != null && !remainingUnknown && now >= lastEnd - 60 * 60 * 1000L && now < lastEnd) {
        val transit = listOfNotNull(
            transitClock(snapshot.lastShuttle)?.let { "셔틀 $it" + snapshot.shuttleStopName?.let { name -> " $name" }.orEmpty() },
            transitClock(snapshot.lastTrain)?.let { "막차 $it" + snapshot.transitStationName?.let { name -> " $name" }.orEmpty() },
        ).joinToString(" · ")
        return model(
            WidgetPhase.GoingHome,
            "귀가 준비!",
            "${clock(lastEnd)} 공연 종료" + transit.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty(),
        )
    }
    val times = current?.let {
        val ending = it.endsAt?.let(::clock)
            ?: ("종료 미정" + nextStageStart(it)?.let { start -> " · 다음 ${clock(start)}" }.orEmpty())
        "${clock(it.startsAt)}–$ending · ${it.stageName}"
    }
    return model(
        WidgetPhase.Playing,
        current?.artist ?: "다음 공연 준비 중",
        times ?: next?.let { "${clock(it.startsAt)} ${it.artist} · ${it.stageName}" } ?: snapshot.festivalName,
    )
}

private fun transitClock(value: String?): String? {
    FestivalIsoTime.parse(value)?.let { return clock(it.toEpochMilli()) }
    return value?.takeIf { Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(it) }
}
