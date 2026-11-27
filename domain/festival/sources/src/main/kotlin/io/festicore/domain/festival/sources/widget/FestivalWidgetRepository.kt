package io.festicore.domain.festival.sources.widget

import io.festicore.core.storage.shared.WidgetSnapshotStore
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalWidgetSnapshot
import io.festicore.domain.festival.widget.FestivalWidgetRead
import io.festicore.domain.festival.widget.FestivalWidgetUseCase
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class WidgetRequest(
    val festivalId: String?,
    val owner: String?,
    val generation: Long,
    val sequence: Long,
    val identityKnown: Boolean = true,
)

/** One account-aware writer. Public data is always validated against the actual local pack. */
internal class FestivalWidgetRepository(
    private val store: WidgetSnapshotStore,
    private val selectedId: suspend () -> String?,
    private val currentSession: suspend () -> String?,
    private val identities: () -> Flow<String?>,
    private val cachedPack: suspend (String) -> CachedFestivalPack?,
) : FestivalWidgetUseCase {
    private val mutex = Mutex()
    private val sequence = AtomicLong()
    private val applied = mutableMapOf<String, Long>()
    private var personalRevoked = false
    private var revokedSession: String? = null
    private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = updates
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun observeSessions() {
        identities().collect { owner ->
            mutex.withLock {
                val record = store.read()
                val existing = decode(record.payload)
                if (existing != null && existing.owner != owner) {
                    store.compareAndSet(record.revision, null, invalidate = true)
                    applied.clear()
                }
            }
            updates.emit(Unit)
        }
    }

    override suspend fun invalidatePersonal() {
        try {
            mutex.withLock {
                personalRevoked = true
                revokedSession = widgetAttempt { currentSession() }.value
                applied.clear()
                val record = store.read()
                check(store.compareAndSet(record.revision, null, invalidate = true)) { "Widget invalidation changed concurrently" }
            }
        } finally {
            updates.tryEmit(Unit)
        }
    }

    override suspend fun read(): FestivalWidgetRead = mutex.withLock {
        val record = store.read()
        val envelope = decode(record.payload)
        val corrupt = record.unavailable || (record.payload != null && envelope == null)
        val selected = selectedId() ?: return@withLock FestivalWidgetRead(null, record.revision, corrupt)
        val pack = cachedPack(selected) ?: return@withLock FestivalWidgetRead(null, record.revision, corrupt)
        val owner = widgetAttempt { currentSession() }
        // A refreshed public pack does not invalidate confirmed personal data from the same owner/festival.
        val personal = envelope?.takeIf {
            owner.succeeded && owner.value != null &&
                (!personalRevoked || owner.value != revokedSession) && it.owner == owner.value && it.festivalId == selected
        }
        FestivalWidgetRead(publicSnapshot(pack, personal), record.revision, corrupt)
    }

    suspend fun capture(festivalId: String? = null): WidgetRequest {
        val identity = widgetAttempt { currentSession() }
        return WidgetRequest(
            festivalId ?: selectedId(),
            identity.value,
            store.read().generation,
            sequence.incrementAndGet(),
            identity.succeeded,
        )
    }

    suspend fun schedule(request: WidgetRequest, ids: List<String>) = patch(request, "schedule") {
        it.copy(savedIds = ids.distinct().sorted(), scheduleKnown = true)
    }

    suspend fun board(request: WidgetRequest, board: FestivalLiveBoardSnapshot) {
        if (board.festivalId != request.festivalId) return
        val pack = request.festivalId?.let { cachedPack(it) } ?: return
        val document = pack.document ?: return
        val summaries = (board.directoryFlags + board.followedFlags).associate { flag ->
            flag.id to listOfNotNull(flag.name, document.zoneName(flag.zoneId), activity(flag.activity)).joinToString(" · ")
        }
        val places = board.crew?.members.orEmpty().mapNotNull { member ->
            when (member.state) {
                "eating" -> "밥"
                "toilet" -> "화장실"
                "leaving" -> "귀가 중"
                else -> document.zoneName(member.zoneId)
            }
        }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2)
        val crew = places.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "크루 ") { "${it.value}명 ${it.key}" }
        patch(request, setOf("flags", "pin", "crew")) { old, accepted ->
            val keepNewerPin = "pin" !in accepted
            // An older board may update crew without deleting a newer, already validated pin summary.
            val flags = if ("flags" in accepted) {
                summaries +
                    (if (keepNewerPin) old.flagSummaries.filterKeys { it == old.pinnedId } else emptyMap())
            } else {
                old.flagSummaries
            }
            old.copy(
                flagSummaries = flags,
                pinnedId = if (keepNewerPin) old.pinnedId else board.pinnedFlagId?.takeIf { it in flags },
                pendingPinnedId = if (keepNewerPin) old.pendingPinnedId else board.pinnedFlagId?.takeUnless { it in flags },
                crewSummary = if ("crew" in accepted) crew ?: board.crew?.name else old.crewSummary,
            )
        }
    }

    suspend fun pin(request: WidgetRequest, id: String?) = patch(request, "pin") { old ->
        old.copy(pinnedId = id?.takeIf { it in old.flagSummaries }, pendingPinnedId = id?.takeUnless { it in old.flagSummaries })
    }

    /** A crew mutation without a complete board must not keep an obsolete location summary. */
    suspend fun crewChanged(request: WidgetRequest) = patch(request, "crew") { it.copy(crewSummary = null) }

    suspend fun selectionChanged(previousId: String?, newId: String) {
        if (previousId == newId) return
        mutex.withLock {
            val record = store.read()
            store.compareAndSet(record.revision, null, invalidate = true)
            applied.clear()
        }
        updates.emit(Unit)
    }

    suspend fun refreshPublic() {
        // The renderer reads pack existence/version afresh, so a lost signal cannot resurrect a deleted pack.
        updates.emit(Unit)
    }

    private suspend fun patch(
        request: WidgetRequest,
        field: String,
        transform: (Envelope) -> Envelope,
    ) = patch(request, setOf(field)) { old, _ -> transform(old) }

    private suspend fun patch(
        request: WidgetRequest,
        fields: Set<String>,
        transform: (Envelope, Set<String>) -> Envelope,
    ) {
        if (!request.identityKnown || request.owner == null || request.festivalId == null) return
        mutex.withLock {
            if (personalRevoked && request.owner == revokedSession) return@withLock
            if (selectedId() != request.festivalId || currentSession() != request.owner) return@withLock
            val record = store.read()
            if (record.generation != request.generation) return@withLock
            val accepted = fields.filterTo(mutableSetOf()) { request.sequence >= (applied[it] ?: 0) }
            if (accepted.isEmpty()) return@withLock
            val pack = cachedPack(request.festivalId) ?: return@withLock
            val old = decode(record.payload)
            val base = old?.takeIf {
                it.owner == request.owner && it.festivalId == request.festivalId
            }?.let { existing ->
                existing.copy(
                    packVersion = pack.packVersion,
                    etag = pack.etag,
                    savedIds = existing.savedIds.filter { id -> pack.document?.performances?.any { it.id == id } == true },
                )
            } ?: Envelope(request.festivalId, request.owner, pack.packVersion, pack.etag)
            val next = transform(base, accepted)
            // Recheck account/selection after disk IO; never stamp a completed A result with B's identity.
            if (currentSession() != request.owner || selectedId() != request.festivalId) return@withLock
            if (!record.unavailable && next == old) {
                accepted.forEach { applied[it] = request.sequence }
                return@withLock
            }
            if (store.compareAndSet(record.revision, json.encodeToString(next))) {
                accepted.forEach { applied[it] = request.sequence }
                updates.tryEmit(Unit)
            }
        }
    }

    private fun decode(value: String?): Envelope? = value?.let {
        try {
            json.decodeFromString<Envelope>(it).takeIf { data -> data.schema == 1 }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun publicSnapshot(pack: CachedFestivalPack, personal: Envelope?): FestivalWidgetSnapshot? {
        val document = pack.document ?: return null
        val stages = document.stages.orEmpty().associate { it.id to it.name }
        val performances = document.performances.mapNotNull { performance ->
            val artist = performance.artist?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val start = FestivalIsoTime.parse(performance.startsAt)?.toEpochMilli() ?: return@mapNotNull null
            val end = FestivalIsoTime.parse(performance.endsAt)?.toEpochMilli()
            if (end != null && end <= start) return@mapNotNull null
            val stage = performance.stageId ?: performance.stageName ?: return@mapNotNull null
            FestivalWidgetSnapshot.Performance(
                performance.id,
                artist,
                stage,
                performance.stageName ?: stages[stage] ?: "무대 정보 없음",
                start,
                end,
                personal?.savedIds?.contains(performance.id) == true,
            )
        }.sortedWith(compareBy({ it.startsAt }, { it.id }))
        return FestivalWidgetSnapshot(
            festivalId = pack.festivalId,
            festivalName = document.name ?: "FESTICORE",
            packVersion = pack.packVersion,
            etag = pack.etag,
            performances = performances,
            days = document.days.orEmpty().mapNotNull { day ->
                day.date?.let { FestivalWidgetSnapshot.Day(it, FestivalIsoTime.parse(day.startsAt)?.toEpochMilli()) }
            }.sortedBy { it.date },
            scheduleKnownForOwner = personal?.scheduleKnown == true,
            pinnedFlagSummary = personal?.pinnedId?.let { personal.flagSummaries[it] },
            crewSummary = personal?.crewSummary,
            lastShuttle = document.directions?.lastShuttleAt,
            lastTrain = document.directions?.lastTrainAt,
            shuttleStopName = document.directions?.shuttleStopName,
            transitStationName = document.directions?.transitStationName,
        )
    }

    private fun activity(value: String?): String? = when (value) {
        "slam" -> "슬램"
        "sing" -> "떼창"
        "train" -> "기차놀이"
        "calm" -> "차분"
        "newbie" -> "초보 환영"
        else -> null
    }
}

@Serializable
private data class Envelope(
    val festivalId: String,
    val owner: String,
    val packVersion: Int,
    val etag: String,
    val schema: Int = 1,
    val savedIds: List<String> = emptyList(),
    val scheduleKnown: Boolean = false,
    val flagSummaries: Map<String, String> = emptyMap(),
    val pinnedId: String? = null,
    val pendingPinnedId: String? = null,
    val crewSummary: String? = null,
)

internal data class WidgetAttempt<T>(
    val value: T?,
    val succeeded: Boolean,
)

/** Widget side effects must not turn a successful primary API/save into an error. */
internal suspend fun <T> widgetAttempt(block: suspend () -> T): WidgetAttempt<T> = try {
    WidgetAttempt(block(), true)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    WidgetAttempt(null, false)
}
