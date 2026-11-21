package io.festicore.feature.festival

import io.festicore.domain.festival.FestivalCatalogArtist
import io.festicore.domain.festival.FestivalCatalogItem
import io.festicore.domain.festival.FestivalOverview
import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 목록 화면의 표시 규칙. iOS `FestivalListPresentation`과 같은 입력에서 같은 결과를 낸다. 날짜는 Asia/Seoul 기준이다. */
internal object FestivalListPresentation {
    sealed interface Timing {
        data class DaysUntil(val days: Long) : Timing

        data object Live : Timing

        data object Ended : Timing

        data object Undated : Timing
    }

    sealed interface CardStatus {
        data class DDay(val label: String) : CardStatus

        data class Closing(val label: String) : CardStatus

        data object Upcoming : CardStatus

        data object Ended : CardStatus
    }

    sealed interface PackCopy {
        data class Unavailable(val text: String) : PackCopy

        data class Ready(val title: String) : PackCopy

        data class Received(val text: String) : PackCopy
    }

    data class ArtistSearchResult(
        val festival: FestivalOverview,
        val artist: FestivalCatalogArtist,
    ) {
        val id: String get() = "${festival.id}:${artist.name}"
    }

    val zone: ZoneId = ZoneId.of("Asia/Seoul")

    /** iOS `AppDateFormat.festivalDay`(`M/d(E)`, ko_KR). */
    private val festivalDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)

    fun visible(
        festivals: List<FestivalOverview>,
        query: String,
        bookedIds: Set<String>,
        onlyBooked: Boolean,
    ): List<FestivalOverview> {
        val search = query.trim()
        return festivals.filter { festival ->
            (!onlyBooked || festival.id in bookedIds) &&
                (search.isEmpty() || contains(festival.name, search) || festival.venue?.let { contains(it, search) } == true)
        }
    }

    fun matchingArtists(festivals: List<FestivalOverview>, query: String): List<ArtistSearchResult> {
        val search = query.trim()
        if (search.isEmpty()) return emptyList()
        return festivals.flatMap { festival ->
            festival.artists.orEmpty().filter { contains(it.name, search) }.map { ArtistSearchResult(festival, it) }
        }
    }

    /** "{축제} · {M/d(E)} {스테이지}". 지금 이후 첫 공연, 없으면 마지막 공연을 쓴다. */
    fun artistMeta(result: ArtistSearchResult, now: Instant): String {
        val collator = Collator.getInstance(Locale.KOREAN)
        val performances =
            result.artist.performances
                .mapNotNull { performance ->
                    val day = parseDay(performance.date)?.atStartOfDay(zone)?.toInstant()
                    val time = parseInstant(performance.startsAt) ?: day ?: return@mapNotNull null
                    Performance(time = time, day = day ?: time, stage = performance.stageName)
                }.sortedWith(
                    compareBy<Performance> { it.time }
                        .thenBy { it.day }
                        .thenComparator { lhs, rhs -> collator.compare(lhs.stage.orEmpty(), rhs.stage.orEmpty()) },
                )
        val performance = performances.firstOrNull { it.time >= now } ?: performances.lastOrNull()
        val schedule =
            performance?.let { item ->
                listOfNotNull(festivalDay(item.day.atZone(zone).toLocalDate()), item.stage?.takeIf { it.isNotEmpty() }).joinToString(" ")
            }
        return listOfNotNull(result.festival.name, schedule).joinToString(" · ")
    }

    fun timing(festival: FestivalOverview, now: Instant): Timing {
        val today = now.atZone(zone).toLocalDate()
        val validLast = festival.lastDate?.let(::parseDay)
        val first = festival.firstDate?.let(::parseDay)
        if (first == null) {
            // 시작일이 없거나 잘못돼도 지난 종료일이 있으면 종료로 본다.
            if (validLast != null && today > validLast) return Timing.Ended
            return Timing.Undated
        }
        val last = validLast ?: first
        if (today > last) return Timing.Ended
        val days = ChronoUnit.DAYS.between(today, first)
        return if (days > 0) Timing.DaysUntil(days) else Timing.Live
    }

    /** 아직 팩이 발행되지 않은 축제의 상태 배지. */
    fun isOpeningSoon(festival: FestivalOverview): Boolean = festival.packVersion == 0

    /** 종료 > 오픈 예정 > 진행 중 > D-3~D-1(빨강) > D-4 이상 > 날짜 없음. */
    fun cardStatus(festival: FestivalOverview, now: Instant): CardStatus {
        val timing = timing(festival, now)
        if (timing == Timing.Ended) return CardStatus.Ended
        if (isOpeningSoon(festival)) return CardStatus.Upcoming
        return when (timing) {
            Timing.Live -> CardStatus.DDay("진행 중")
            is Timing.DaysUntil -> if (timing.days <= 3) CardStatus.Closing("D-${timing.days}") else CardStatus.DDay("D-${timing.days}")
            Timing.Undated -> CardStatus.DDay("일정 확인")
            Timing.Ended -> CardStatus.Ended
        }
    }

    fun searchBadge(festival: FestivalOverview, now: Instant): String? = when (val status = cardStatus(festival, now)) {
        is CardStatus.DDay -> status.label
        is CardStatus.Closing -> status.label
        CardStatus.Upcoming -> "오픈 예정"
        CardStatus.Ended -> null
    }

    fun packCopy(festival: FestivalOverview): PackCopy {
        if (festival.packVersion <= 0) return PackCopy.Unavailable("팩 발행을 기다리고 있어요")
        // 카탈로그 캐시가 팩보다 오래됐을 수 있으므로 같거나 높으면 최신으로 본다.
        val cachedVersion = festival.cachedPackVersion
        if (cachedVersion != null && cachedVersion >= festival.packVersion) return PackCopy.Received("팩 받음 · 최신 버전")
        return PackCopy.Ready(if (cachedVersion == null) "팩 받기" else "팩 업데이트")
    }

    /** 검색 시작 화면의 다가오는 페스티벌. 진행 중이거나 시작일이 정해진 예정 축제만 일정 순으로 고른다. */
    fun upcoming(festivals: List<FestivalOverview>, now: Instant): List<FestivalOverview> = sorted(festivals, now).filter {
        when (timing(it, now)) {
            Timing.Live, is Timing.DaysUntil -> true
            Timing.Ended, Timing.Undated -> false
        }
    }

    /** 진행 중 → 가까운 일정 → 날짜 없음 → 종료 순서. 팩 발행 여부는 날짜 정렬에 영향을 주지 않는다. */
    fun sorted(festivals: List<FestivalOverview>, now: Instant): List<FestivalOverview> = festivals
        .map { it to sortKey(it, now) }
        .sortedWith(
            compareBy<Pair<FestivalOverview, SortKey>> { it.second.rank }
                .thenBy { it.second.order }
                .thenBy { it.first.id },
        ).map { it.first }

    /** 카드 · 검색 행의 메타. `[장소, "M/d(E) – M/d(E)"]`에서 없는 값을 빼고 " · "로 잇는다. */
    fun meta(festival: FestivalOverview): String {
        val dates = listOf(festival.firstDate, festival.lastDate).mapNotNull { it?.let(::parseDay)?.let(::festivalDay) }.joinToString(" – ")
        return listOfNotNull(festival.venue, dates.ifEmpty { null }).joinToString(" · ")
    }

    /** iOS `FestivalOverview(item:isCached:)`. 팩 병합은 팩 단계에서 붙인다. */
    fun overview(item: FestivalCatalogItem): FestivalOverview = FestivalOverview(
        id = item.id,
        name = item.name,
        venue = item.venue,
        firstDate = item.firstDate,
        lastDate = item.lastDate,
        thumbnailUrl = item.thumbnailUrl,
        packVersion = item.packVersion,
        isCached = false,
        cachedPackVersion = null,
        catalogStatus = item.status,
        ticketUrl = item.ticketLinks.firstOrNull()?.url,
        artists = item.artists,
    )

    /** `yyyy-MM-dd`. 없는 날짜(2월 30일 등)는 null. */
    fun parseDay(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    fun festivalDay(date: LocalDate): String = festivalDayFormatter.format(date)

    private fun parseInstant(value: String): Instant? = try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    private data class Performance(
        val time: Instant,
        val day: Instant,
        val stage: String?,
    )

    private data class SortKey(
        val rank: Int,
        val order: Long,
    )

    private fun sortKey(festival: FestivalOverview, now: Instant): SortKey {
        val timing = timing(festival, now)
        if (timing == Timing.Ended) {
            // timing과 같이 파싱에 성공한 종료일, 없으면 시작일을 쓴다.
            val lastDay = festival.lastDate?.let(::parseDay) ?: festival.firstDate?.let(::parseDay)
            return SortKey(3, -(lastDay?.toEpochDay() ?: 0))
        }
        val first = festival.firstDate?.let(::parseDay)?.toEpochDay() ?: Long.MAX_VALUE
        return when (timing) {
            Timing.Live -> SortKey(0, first)
            is Timing.DaysUntil -> SortKey(1, first)
            Timing.Undated, Timing.Ended -> SortKey(2, Long.MAX_VALUE)
        }
    }

    /** iOS `range(of:options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive])`. 접는 규칙은 designkit 검색 강조와 같다. */
    private fun contains(value: String, query: String): Boolean = fold(value).contains(fold(query))

    private fun fold(value: String): String {
        val text = StringBuilder()
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            text.append(foldCodePoint(codePoint))
            index += Character.charCount(codePoint)
        }
        return text.toString()
    }

    private fun foldCodePoint(codePoint: Int): String {
        if (Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()) return ""
        var folded = codePoint
        if (folded in FULL_WIDTH_FIRST..FULL_WIDTH_LAST) folded -= FULL_WIDTH_OFFSET
        if (folded == IDEOGRAPHIC_SPACE) folded = ' '.code
        var text = String(Character.toChars(folded))
        // 한글은 분해하면 자모가 갈라져 음절 경계를 넘어 일치하므로 라틴 범위만 발음 기호를 푼다.
        if (folded in LATIN_FIRST..LATIN_LAST) {
            text = Normalizer.normalize(text, Normalizer.Form.NFD).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        }
        return text.lowercase(Locale.ROOT)
    }

    private const val FULL_WIDTH_FIRST = 0xFF01
    private const val FULL_WIDTH_LAST = 0xFF5E
    private const val FULL_WIDTH_OFFSET = 0xFEE0
    private const val IDEOGRAPHIC_SPACE = 0x3000
    private const val LATIN_FIRST = 0x00C0
    private const val LATIN_LAST = 0x024F
}
