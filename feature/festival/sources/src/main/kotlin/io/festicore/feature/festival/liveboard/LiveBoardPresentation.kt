package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalLiveBoardSchedule
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOverview
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.festivallist.FestivalListPresentation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** 라이브 보드에 그릴 값을 계산한다. `now`는 서버 시각으로 보정한 시각을 받는다. iOS `FestivalLiveBoardPresentation`. */
internal object LiveBoardPresentation {
    enum class FlagStatus {
        Active,
        Resting,
        Ended,
    }

    data class StageRow(
        val id: String,
        val name: String,
        val now: Slot?,
        val next: Slot?,
        val isMine: Boolean,
    ) {
        data class Slot(
            val artist: String,
            val time: String,
        )
    }

    data class SetCard(
        val label: String,
        val artist: String,
        val meta: String,
        val remain: String,
        val progress: Double,
    )

    data class Countdown(
        val time: String,
        val sub: String,
    )

    sealed interface Phase {
        data class BeforeOpening(val countdown: Countdown) : Phase

        data object Live : Phase

        data class FinalHour(val countdown: Countdown) : Phase

        data object Ended : Phase

        data object Unknown : Phase

        val isBeforeOpening: Boolean get() = this is BeforeOpening
    }

    data class CrewMember(
        val id: String,
        val nickname: String,
        val isMe: Boolean,
        /** 존 칩을 누르면 존 지도에서 고를 존 */
        val zoneId: String?,
        val zone: String?,
        val status: String?,
        val time: String?,
        val isLeaving: Boolean,
    )

    data class Crew(
        val title: String,
        val subtitle: String,
        val members: List<CrewMember>,
    )

    data class Flag(
        val id: String,
        val name: String,
        val status: FlagStatus?,
        val zone: String?,
        val tags: String,
        val meta: String,
        val time: String?,
        val thumbnailUrl: String?,
    )

    val zone: ZoneId = ZoneId.of("Asia/Seoul")

    /** iOS `AppDateFormat.time`. */
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN).withZone(zone)

    /** iOS `AppDateFormat.liveBoardDay`. */
    private val dayFormatter = DateTimeFormatter.ofPattern("MM.dd (E)", Locale.KOREAN)

    /** iOS `AppDateFormat.monthDayTime`. */
    private val monthDayTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.KOREAN).withZone(zone)

    fun time(value: Instant): String = timeFormatter.format(value)

    fun monthDayTime(value: Instant): String = monthDayTimeFormatter.format(value)

    // MARK: - 시각

    /** 최근 업데이트 내림차순, 같으면 ID 오름차순. */
    fun sortedActiveFlags(flags: List<FestivalLiveBoardSnapshot.Flag>): List<FestivalLiveBoardSnapshot.Flag> =
        flags.sortedWith(compareByDescending<FestivalLiveBoardSnapshot.Flag> { it.updatedAt ?: Instant.MIN }.thenBy { it.id })

    /**
     * 개장 전 활성 깃발은 도착 예정 순(Figma S01 B). 공개 계획 중 [now] 이후 가장 이른 공연 시작이 빠른 깃발이 앞이고,
     * 계획이 없는 깃발은 뒤에 최근 업데이트 순으로 둔다.
     */
    fun sortedActiveFlagsByArrival(
        flags: List<FestivalLiveBoardSnapshot.Flag>,
        pack: FestivalPackDocument,
        now: Instant,
    ): List<FestivalLiveBoardSnapshot.Flag> {
        val starts = mutableMapOf<String, Instant>()
        for (entry in FestivalLiveBoardSchedule.entries(pack)) {
            if (entry.slot.startsAt >= now) starts.putIfAbsent(entry.slot.id, entry.slot.startsAt)
        }
        // 최근 업데이트 순을 안정 정렬의 기준 순서로 쓴다.
        return sortedActiveFlags(flags)
            .mapIndexed { index, flag -> Triple(flag, flag.plannedSetIds.mapNotNull { starts[it] }.minOrNull(), index) }
            .sortedWith(
                compareBy<Triple<FestivalLiveBoardSnapshot.Flag, Instant?, Int>>({ it.second == null }, { it.second }, { it.third }),
            ).map { it.first }
    }

    /** 서버 시각에 첫 수신 후 흐른 기기 시간을 더한다. 304 확인은 기준점을 바꾸지 않는다. */
    fun serverNow(
        serverAt: Instant?,
        clockAnchorAt: Instant?,
        deviceNow: Instant,
    ): Instant {
        if (serverAt == null || clockAnchorAt == null) return deviceNow
        return serverAt.plus(Duration.between(clockAnchorAt, deviceNow))
    }

    fun relativeTime(date: Instant?, now: Instant): String? {
        if (date == null) return null
        val minutes = maxOf(0L, Duration.between(date, now).seconds / 60)
        if (minutes < 1) return "방금"
        if (minutes < 60) return "${minutes}분 전"
        val hours = minutes / 60
        return if (hours < 24) "${hours}시간 전" else "${hours / 24}일 전"
    }

    /** 다음 일정 카드의 갱신 문구. 수신 시각과 같은 기기 시각으로 잰다. */
    fun updatedText(receivedAt: Instant?, deviceNow: Instant): String = relativeTime(receivedAt, deviceNow)?.let { "$it 업데이트" } ?: "시간표 기준"

    /** 오프라인 배너 문구. 경과 시간은 기기 시각으로 잰다. */
    fun offlineText(receivedAt: Instant?, deviceNow: Instant): String {
        val age = relativeTime(receivedAt, deviceNow) ?: return "오프라인"
        return "오프라인 · $age 정보"
    }

    // MARK: - 축제 · 일정

    /** 팩에 게시된 개장 · 종료 시각만으로 시간대별 상태를 고른다. */
    fun phase(
        pack: FestivalPackDocument?,
        festival: FestivalOverview,
        now: Instant,
    ): Phase {
        if (pack == null) return Phase.Unknown
        val days =
            pack.days.orEmpty().mapNotNull { day ->
                val date = day.date ?: return@mapNotNull null
                val start = FestivalIsoTime.parse(day.startsAt) ?: return@mapNotNull null
                val end = FestivalIsoTime.parse(day.endsAt)?.takeIf { it > start }
                PackDay(date, start, end)
            }.sortedBy { it.start }
        if (days.isEmpty()) return Phase.Unknown

        // 카탈로그의 마지막 날짜와 팩의 마지막 날짜가 다르면 팩이 일부만 내려온 상태다.
        val last = days.last()
        val hasFinalDay = festival.lastDate == null || last.date == festival.lastDate
        val finalEnd = if (hasFinalDay) last.end else null
        if (finalEnd != null && now >= finalEnd) return Phase.Ended

        val next = days.firstOrNull { now < it.start }
        if (next != null && days.none { it.start <= now && (it.end?.let { end -> now < end } ?: true) }) {
            var sub = "${time(next.start)} 개장"
            val followingStart = days.firstOrNull { it.start > next.start }?.start
            val firstSet =
                FestivalLiveBoardSchedule.entries(pack).firstOrNull { entry ->
                    entry.slot.startsAt >= next.start &&
                        (next.end?.let { entry.slot.startsAt < it } ?: true) &&
                        (followingStart?.let { entry.slot.startsAt < it } ?: true)
                }
            if (firstSet != null) sub += " · 첫 공연 ${time(firstSet.slot.startsAt)} ${firstSet.stageName}"
            return Phase.BeforeOpening(Countdown(duration(now, next.start), sub))
        }

        if (finalEnd != null && now >= finalEnd.minusSeconds(3_600)) {
            return Phase.FinalHour(Countdown(duration(now, finalEnd), "${time(finalEnd)} 종료"))
        }
        return Phase.Live
    }

    fun switcherSubtitle(
        pack: FestivalPackDocument?,
        festival: FestivalOverview,
        now: Instant,
    ): String {
        val day = selectedDay(pack, festival, now)
        val start = day?.startsAt
        val end = day?.endsAt
        if (day != null && parseDay(day.date) == today(now) && start != null && end != null) {
            val status = if (now < start) {
                "시작 전"
            } else if (now < end) {
                "진행 중"
            } else {
                "종료"
            }
            return "오늘 · DAY ${day.index + 1} $status"
        }
        return listOfNotNull(FestivalListPresentation.searchBadge(festival, now), festival.venue).joinToString(" · ")
    }

    /** `DAY n · MM.dd (E) · HH:mm–HH:mm`. 오늘 날짜가 없으면 첫날을 쓴다. */
    fun dayLine(
        pack: FestivalPackDocument?,
        festival: FestivalOverview,
        now: Instant,
    ): String? {
        val day = selectedDay(pack, festival, now) ?: return null
        val parts = mutableListOf("DAY ${day.index + 1}")
        parseDay(day.date)?.let { parts += dayFormatter.format(it) }
        if (day.startsAt != null && day.endsAt != null) parts += "${time(day.startsAt)}–${time(day.endsAt)}"
        return parts.joinToString(" · ")
    }

    /** 종료 요약은 축제 이름과 회차 · 운영 시간만 표시한다. */
    fun doneSubtitle(
        pack: FestivalPackDocument?,
        festival: FestivalOverview,
        now: Instant,
    ): String {
        val day = selectedDay(pack, festival, now) ?: return festival.name
        var text = "${festival.name} DAY ${day.index + 1}"
        if (day.startsAt != null && day.endsAt != null) text += " · ${time(day.startsAt)}–${time(day.endsAt)}"
        return text
    }

    /** 오늘 개장 전이면 카운트다운을 돌려준다. */
    fun countdown(
        pack: FestivalPackDocument?,
        festival: FestivalOverview,
        now: Instant,
    ): Countdown? {
        val opensAt = selectedDay(pack, festival, now)?.startsAt ?: return null
        if (now >= opensAt) return null
        var sub = "${time(opensAt)} 개장"
        val first = pack?.let { FestivalLiveBoardSchedule.entries(it).firstOrNull { entry -> entry.slot.startsAt >= opensAt } }
        if (first != null) sub += " · 첫 공연 ${time(first.slot.startsAt)} ${first.stageName}"
        return Countdown(duration(now, opensAt), sub)
    }

    /** 내 다음 일정, 없으면 전체 다음 공연. 개장 전에는 첫 공연 시작 시각을 보인다. */
    fun setCard(
        pack: FestivalPackDocument?,
        mySetIds: Set<String>,
        isBeforeOpening: Boolean,
        now: Instant,
    ): SetCard? {
        if (pack == null) return null
        val entries = FestivalLiveBoardSchedule.entries(pack)
        val mine = entries.firstOrNull { it.slot.id in mySetIds && (it.slot.endsAt ?: it.slot.startsAt) > now }
        val entry: FestivalLiveBoardSchedule.Entry?
        val label: String
        if (mine != null) {
            entry = mine
            label = if (isBeforeOpening) "내 첫 공연" else "내 다음 일정"
        } else {
            entry = entries.firstOrNull { it.slot.startsAt > now }
            label = if (isBeforeOpening) "첫 공연" else "다음 공연"
        }
        if (entry == null) return null
        val slot = entry.slot
        val range = slot.endsAt?.let { "${time(slot.startsAt)}–${time(it)}" } ?: time(slot.startsAt)
        val meta = "${entry.stageName} · $range"
        if (isBeforeOpening) return SetCard(label, slot.artist, meta, time(slot.startsAt), 0.0)
        val end = slot.endsAt
        if (slot.startsAt <= now && end != null) {
            val elapsed = seconds(slot.startsAt, now) / seconds(slot.startsAt, end)
            return SetCard(label, slot.artist, meta, "공연 중", elapsed.coerceIn(0.0, 1.0))
        }
        val minutes = seconds(now, slot.startsAt) / 60
        return SetCard(label, slot.artist, meta, remaining(minutes.toInt()), (1 - minutes / 60).coerceIn(0.0, 1.0))
    }

    fun stageRows(stages: List<FestivalLiveBoardSchedule.Stage>, mySetIds: Set<String>): List<StageRow> = stages.map { stage ->
        StageRow(
            id = stage.id,
            name = stage.name,
            now = stage.current?.let { StageRow.Slot(it.artist, it.endsAt?.let { end -> "${time(end)} 종료" } ?: "공연 중") },
            next = stage.next?.let { StageRow.Slot(it.artist, "${time(it.startsAt)} 시작") },
            isMine = listOfNotNull(stage.current?.id, stage.next?.id).any { it in mySetIds },
        )
    }

    /** `출처: 이름 · 확인 MM/dd HH:mm`. 이름이 없으면 줄을 그리지 않는다. */
    fun sourceLine(source: FestivalPackDocument.Source?): String? {
        val name = source?.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val verifiedAt = FestivalIsoTime.parse(source.verifiedAt) ?: return "출처: $name"
        return "출처: $name · 확인 ${monthDayTime(verifiedAt)}"
    }

    // MARK: - 크루 · 깃발

    fun crew(
        crew: FestivalLiveBoardSnapshot.Crew,
        sessionUserId: String?,
        zoneName: (String?) -> String?,
        now: Instant,
    ): Crew {
        val members =
            crew.members.map { member ->
                CrewMember(
                    id = member.id,
                    nickname = member.nickname,
                    isMe = sessionUserId != null && member.id == sessionUserId,
                    zoneId = member.zoneId,
                    zone = zoneName(member.zoneId),
                    status = member.state?.let(::crewStateText),
                    time = relativeTime(member.updatedAt, now),
                    isLeaving = member.state == "leaving",
                )
            }
        val counts = members.mapNotNull { it.zone }.groupingBy { it }.eachCount()
        // 가장 많은 존. 같으면 이름이 앞선 쪽(iOS `max`와 같은 결과).
        val busiest = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).firstOrNull()
        val subtitle = busiest?.let { "${members.size}명 · ${it.value}명이 ${it.key} 근처" } ?: "${members.size}명"
        return Crew(title = "내 크루 · ${crew.name}", subtitle = subtitle, members = members)
    }

    fun flag(
        flag: FestivalLiveBoardSnapshot.Flag,
        zoneName: (String?) -> String?,
        now: Instant,
        festivalEnded: Boolean = false,
    ): Flag {
        val zone = zoneName(flag.zoneId)
        return Flag(
            id = flag.id,
            name = flag.name,
            status = if (festivalEnded) FlagStatus.Ended else flagStatus(flag.status),
            zone = zone,
            tags = flag.tags.mapNotNull(::tagText).joinToString(" · "),
            meta = listOfNotNull(zone, flag.activity?.let(::tagText)).joinToString(" · "),
            time = relativeTime(flag.updatedAt, now),
            thumbnailUrl = flag.thumbnailUrl,
        )
    }

    fun flagStatus(value: String): FlagStatus? = when (value) {
        "active" -> FlagStatus.Active
        "resting" -> FlagStatus.Resting
        "ended" -> FlagStatus.Ended
        else -> null
    }

    fun crewStateText(value: String): String? = when (value) {
        "here" -> "나 여기"
        "eating" -> "밥"
        "toilet" -> "화장실"
        "leaving" -> "귀가 중"
        else -> null
    }

    /** 깃발 성향 태그와 활동 문구. `resting` 같은 값은 표시하지 않는다. */
    fun tagText(value: String): String? = when (value) {
        "slam" -> "슬램"
        "sing" -> "떼창"
        "train" -> "기차놀이"
        "calm" -> "차분"
        "newbie" -> "초보 환영"
        else -> null
    }

    // MARK: - private

    private class PackDay(
        val date: String,
        val start: Instant,
        val end: Instant?,
    )

    private class Day(
        val index: Int,
        val date: String,
        val startsAt: Instant?,
        val endsAt: Instant?,
    )

    private fun selectedDay(
        pack: FestivalPackDocument?,
        festival: FestivalOverview,
        now: Instant,
    ): Day? {
        val packDays = pack?.days.orEmpty().filter { day -> day.date?.let(::parseDay) != null }
        val dates =
            if (packDays.isEmpty()) {
                listOfNotNull(festival.firstDate, festival.lastDate).filter { parseDay(it) != null }
            } else {
                packDays.mapNotNull { it.date }
            }
        val sorted = dates.distinct().sorted()
        val today = today(now)
        val date = sorted.firstOrNull { parseDay(it) == today } ?: sorted.firstOrNull() ?: return null
        val packDay = packDays.firstOrNull { it.date == date }
        return Day(
            index = sorted.indexOf(date),
            date = date,
            startsAt = FestivalIsoTime.parse(packDay?.startsAt),
            endsAt = FestivalIsoTime.parse(packDay?.endsAt),
        )
    }

    private fun today(now: Instant): LocalDate = now.atZone(zone).toLocalDate()

    private fun parseDay(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun seconds(from: Instant, to: Instant): Double = Duration.between(from, to).toMillis() / 1_000.0

    private fun duration(now: Instant, date: Instant): String {
        val minutes = maxOf(0L, Duration.between(now, date).seconds / 60)
        val hours = minutes / 60
        if (hours >= 24) return "${hours / 24}일 ${hours % 24}시간"
        return if (hours > 0) "${hours}시간 ${minutes % 60}분" else "${minutes}분"
    }

    private fun remaining(minutes: Int): String {
        val value = maxOf(0, minutes)
        if (value < 60) return "${value}분 후"
        val hours = value / 60
        return if (hours < 24) "${hours}시간 후" else "${hours / 24}일 후"
    }
}
