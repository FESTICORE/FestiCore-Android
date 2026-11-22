package io.festicore.feature.timetable

import io.festicore.domain.auth.CurrentUserProvider
import io.festicore.domain.festival.FestivalBoardSelection
import io.festicore.domain.festival.FestivalBoardSelectionUseCase
import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.domain.festival.FestivalSchedule
import io.festicore.domain.festival.FestivalScheduleSaved
import io.festicore.domain.festival.FestivalScheduleUseCase
import io.festicore.domain.timetable.FestivalPackTimetableMapper
import io.festicore.domain.timetable.TimetableDay
import io.festicore.domain.timetable.TimetableLineupArtist
import io.festicore.domain.timetable.TimetableLineupDay
import io.festicore.domain.timetable.TimetableOverlap
import io.festicore.domain.timetable.TimetableOverlapCalculator
import io.festicore.domain.timetable.TimetablePickOverlap
import io.festicore.domain.timetable.TimetableSet
import io.festicore.domain.timetable.TimetableSetOverlap
import io.festicore.domain.timetable.TimetableStage
import io.festicore.feature.timetable.main.TimetableContentStatus
import io.festicore.feature.timetable.main.TimetableState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.time.Duration as KotlinDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** 샘플 첫날(2026-08-07 00:00 KST). iOS `TimetableFeature+Sample.swift` `firstDay`. */
internal val FIRST_DAY: Instant = Instant.ofEpochSecond(1_786_028_400)

/** 축제 전날 정오. iOS `TimetableScheduleTests.beforeFestival`. */
internal val BEFORE_FESTIVAL: Instant = FIRST_DAY.minusSeconds(12 * 60 * 60)

internal enum class SampleVariant { Basic, Changed, Unpublished }

private val sampleStages = listOf(TimetableStage("rivet", "리벳 스테이지"), TimetableStage("wonder", "원더 스테이지"))

private val sampleSets =
    listOf(
        TimetableSet("opening", "오프닝 크루", "rivet", 750, 790),
        TimetableSet("noise", "노이즈캔디", "rivet", 780, 820),
        TimetableSet("smoking", "스모킹구스", "rivet", 880, 928),
        TimetableSet("noise-returns", "노이즈캔디 리턴즈", "rivet", 940, 990),
        TimetableSet("lime", "라임플래그스", "rivet", 1020, 1070),
        TimetableSet("headliner", "헤드라이너 X", "rivet", 1290, 1360),
        TimetableSet("rookie", "루키 쇼케이스", "wonder", 720, 760),
        TimetableSet("bluefuse", "블루퓨즈", "wonder", 890, 935),
        TimetableSet("cryingbird", "크라잉버드", "wonder", 955, 1005),
        TimetableSet("encore", "앵콜밴드", "wonder", 1090, 1140),
        TimetableSet("midnight", "미드나잇 세션", "wonder", 1410, 1530),
    )

/** Figma S02와 같은 테스트용 상태(iOS `TimetableFeature.State.sample`). 겹침 같은 계산 값은 [calculator]로 채운다. */
internal fun sample(
    variant: SampleVariant = SampleVariant.Basic,
    calculator: TimetableOverlapCalculator = FakeOverlapCalculator(),
): TimetableState {
    val stages = sampleStages
    var sets = sampleSets
    when (variant) {
        SampleVariant.Changed ->
            sets =
                sets.map {
                    when (it.id) {
                        "lime" -> it.copy(previousStart = 1000)
                        "encore" -> it.copy(previousStart = 1105)
                        else -> it
                    }
                }

        SampleVariant.Basic, SampleVariant.Unpublished -> Unit
    }
    val isUnpublished = variant == SampleVariant.Unpublished
    val days =
        listOf("0807" to "8/7 금", "0808" to "8/8 토", "0809" to "8/9 일").mapIndexed { index, (id, title) ->
            TimetableDay(
                id = id,
                title = title,
                date = FIRST_DAY.plusSeconds(index * 24 * 60 * 60L),
                stages = stages,
                sets = if (index == 0 && !isUnpublished) sets else emptyList(),
            )
        }
    val state =
        TimetableState(
            days = days,
            source = "출처: 주최측 공지 · 확인 10/16 14:00",
            mySetIds = setOf("noise", "noise-returns", "headliner", "cryingbird"),
            nowMinute = 928,
            contentStatus = if (isUnpublished) TimetableContentStatus.Unpublished else TimetableContentStatus.Published,
            lineup =
                if (isUnpublished) {
                    listOf(
                        TimetableLineupDay(
                            "0807",
                            "8/7 (금)",
                            listOf("노이즈캔디", "스모킹구스", "크라잉버드", "앵콜밴드", "헤드라이너 X").map(::TimetableLineupArtist),
                        ),
                        TimetableLineupDay("0808", "8/8 (토)", listOf("라임플래그스", "블루퓨즈", "터치드", "웨이브키즈").map(::TimetableLineupArtist)),
                    )
                } else {
                    emptyList()
                },
        )
    return state.derived(calculator)
}

/** ViewModel이 매 변경 뒤 채우는 계산 값(겹침 · 겹치는 공연)을 같은 규칙으로 채운다. */
internal fun TimetableState.derived(calculator: TimetableOverlapCalculator = FakeOverlapCalculator()): TimetableState {
    val day = selectedDay ?: return copy(overlaps = emptyList(), overlappingSetIds = emptySet())
    val overlapping =
        if (!shouldShowOverlapWarning) {
            emptySet()
        } else {
            day.sets.filter { it.id in mySetIds && calculator.setOverlaps(days, mySetIds, it, day).isNotEmpty() }.map { it.id }.toSet()
        }
    return copy(overlaps = calculator.overlaps(days, mySetIds, day), overlappingSetIds = overlapping)
}

/** 겹침 계산 대역. 구현(`domain/timetable/sources`)은 feature가 의존할 수 없어 같은 교집합 규칙만 다시 쓴다. */
internal class FakeOverlapCalculator : TimetableOverlapCalculator {
    private data class Mine(
        val set: TimetableSet,
        val dayId: String,
        val start: Instant,
        val end: Instant,
    )

    private fun mine(days: List<TimetableDay>, ids: Set<String>): List<Mine> = days
        .flatMap { day -> day.sets.filter { it.id in ids }.map { Mine(it, day.id, day.interval(it).start, day.interval(it).end) } }
        .sortedBy { it.start }

    private fun shared(
        aStart: Instant,
        aEnd: Instant,
        bStart: Instant,
        bEnd: Instant,
    ): Pair<Instant, Instant>? {
        val start = maxOf(aStart, bStart)
        val end = minOf(aEnd, bEnd)
        return if (end > start) start to end else null
    }

    override fun overlaps(
        days: List<TimetableDay>,
        mySetIds: Set<String>,
        day: TimetableDay,
    ): List<TimetableOverlap> {
        val mine = mine(days, mySetIds)
        return mine.indices.flatMap { i ->
            mine.drop(i + 1).mapNotNull { other ->
                val current = mine[i]
                if (current.dayId != day.id && other.dayId != day.id) return@mapNotNull null
                val (start, end) = shared(current.start, current.end, other.start, other.end) ?: return@mapNotNull null
                TimetableOverlap(Duration.between(day.date, start).toMinutes().toInt(), Duration.between(day.date, end).toMinutes().toInt())
            }
        }
    }

    override fun setOverlaps(
        days: List<TimetableDay>,
        mySetIds: Set<String>,
        set: TimetableSet,
        day: TimetableDay,
    ): List<TimetableSetOverlap> {
        val interval = day.interval(set)
        return mine(days, mySetIds).mapNotNull { other ->
            if (other.set.id == set.id) return@mapNotNull null
            val (start, end) = shared(other.start, other.end, interval.start, interval.end) ?: return@mapNotNull null
            val stage = days.first { it.id == other.dayId }.stages.firstOrNull { it.id == other.set.stageId }?.name ?: "무대 미정"
            TimetableSetOverlap(
                other.set.artist,
                stage,
                other.set.timeRange,
                (Duration.between(start, end).toMillis() / 60_000.0).roundToInt(),
            )
        }
    }

    /** iOS `SchedulePickFeature.State.overlap(of:)`: 같은 날 먼저(또는 같은 시각에) 시작한 고른 공연 중 가장 많이 겹친 것. */
    override fun pickOverlap(
        day: TimetableDay,
        selectedSetIds: Set<String>,
        set: TimetableSet,
    ): TimetablePickOverlap? {
        if (set.id !in selectedSetIds) return null
        return day.sets
            .filter { it.id != set.id && it.id in selectedSetIds && it.start <= set.start }
            .mapNotNull { other ->
                val minutes = minOf(other.end, set.end) - maxOf(other.start, set.start)
                if (minutes > 0) TimetablePickOverlap(other.artist, minutes) else null
            }.maxByOrNull { it.minutes }
    }
}

/** 내 일정 대역(iOS `ScheduleServiceSpy`). 읽기는 [fetchResults]를 차례로 쓰고 비면 빈 일정, 저장은 받은 목록을 그대로 돌려준다. */
internal class FakeScheduleUseCase(
    fetchResults: List<Result<FestivalSchedule>> = emptyList(),
    var fetch: (suspend (String) -> FestivalSchedule)? = null,
    var replace: (suspend (String, List<String>, Instant) -> FestivalSchedule)? = null,
) : FestivalScheduleUseCase {
    private val fetchResults = fetchResults.toMutableList()
    var fetchCount = 0
    val replaced = mutableListOf<Replaced>()
    val notified = mutableListOf<FestivalScheduleSaved>()

    data class Replaced(
        val festivalId: String,
        val setIds: List<String>,
        val clientAt: Instant,
    )

    override suspend fun mySchedule(festivalId: String): FestivalSchedule {
        fetchCount += 1
        fetch?.let { return it(festivalId) }
        return if (fetchResults.isEmpty()) FestivalSchedule(emptyList()) else fetchResults.removeAt(0).getOrThrow()
    }

    override suspend fun saveMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule {
        replaced += Replaced(festivalId, setIds, clientAt)
        return replace?.invoke(festivalId, setIds, clientAt) ?: FestivalSchedule(setIds)
    }

    override val savedSchedules: Flow<FestivalScheduleSaved> = MutableSharedFlow()

    override suspend fun notifyMyScheduleSaved(saved: FestivalScheduleSaved) {
        notified += saved
    }
}

internal class FakeCurrentUserProvider(userId: String? = null) : CurrentUserProvider {
    override val currentUserId = MutableStateFlow(userId)
}

/** 매퍼 대역. 문서 ID별로 정해 둔 날짜 · 라인업을 돌려주고, 받은 직전 날짜를 기록한다. */
internal class FakeMapper(
    var days: Map<String, List<TimetableDay>> = emptyMap(),
    var lineup: Map<String, List<TimetableLineupDay>> = emptyMap(),
) : FestivalPackTimetableMapper {
    val previousDays = mutableListOf<List<TimetableDay>>()

    override fun days(pack: FestivalPackDocument, previousDays: List<TimetableDay>): List<TimetableDay> {
        this.previousDays += previousDays
        return days[pack.id].orEmpty()
    }

    override fun lineup(pack: FestivalPackDocument): List<TimetableLineupDay> = lineup[pack.id].orEmpty()

    override fun source(source: FestivalPackDocument.Source?): String = source?.let { "출처: 테스트" }.orEmpty()
}

internal class FakeBoardSelectionUseCase(initial: FestivalBoardSelection? = null) : FestivalBoardSelectionUseCase {
    override val selection = MutableStateFlow(initial)
    private val retries = MutableSharedFlow<String>()
    override val retryRequests: Flow<String> = retries
    val retried = mutableListOf<String>()

    override fun update(selection: FestivalBoardSelection) {
        this.selection.value = selection
    }

    override fun clear() {
        selection.value = null
    }

    override suspend fun requestRetry(festivalId: String) {
        retried += festivalId
    }
}

internal fun selection(
    festivalId: String = "fst",
    document: FestivalPackDocument? = FestivalPackDocument(id = festivalId),
    connection: FestivalBoardSelection.Connection = FestivalBoardSelection.Connection.Live,
    isOnlineDetailLoading: Boolean = false,
    isOnlineDetailFailed: Boolean = false,
    isPackWatchFailed: Boolean = false,
    packCheckedAt: Instant? = null,
) = FestivalBoardSelection(festivalId, document, isOnlineDetailLoading, isOnlineDetailFailed, isPackWatchFailed, connection, packCheckedAt)

internal class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

/** 기다림을 간격마다 따로 풀어 주는 대역. [release]할 때까지 기다림이 끝나지 않는다. */
internal class TestSleeper {
    private val gates = mutableMapOf<KotlinDuration, Channel<Unit>>()

    private fun gate(duration: KotlinDuration) = gates.getOrPut(duration) { Channel(Channel.UNLIMITED) }

    suspend fun sleep(duration: KotlinDuration) {
        gate(duration).receive()
    }

    fun release(duration: KotlinDuration) {
        gate(duration).trySend(Unit)
    }
}
