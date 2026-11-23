package io.festicore.domain.festival.sources.repository

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackDownloadProgress
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.pack.FestivalPackRepository
import io.festicore.domain.festival.sources.model.dto.FestivalPackDetailDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDocumentDto
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import io.festicore.service.pack.PackDownloadEvent as ServiceDownloadEvent
import io.festicore.service.pack.PackSyncError
import io.festicore.service.pack.PackSyncResult as ServiceSyncResult
import io.festicore.service.pack.PackSyncService
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/** 팩 서비스 결과를 엔티티로 바꾸고 공유 [state]를 갱신한다(설계 D1). 한 앱에 하나만 둔다. */
internal class FestivalPackRepositoryImpl(
    private val service: PackSyncService,
    private val json: Json,
) : FestivalPackRepository {
    private val mutableState = MutableStateFlow(FestivalPackState())
    override val state: StateFlow<FestivalPackState> = mutableState.asStateFlow()

    override suspend fun reloadCachedPacks(): List<CachedFestivalPack> = mapPackErrors {
        val packs = service.cachedPacks().map { it.toDomain() }
        mutableState.update { it.copy(packs = packs.associateBy(CachedFestivalPack::festivalId)) }
        packs
    }

    override fun packUpdates(festivalId: String): Flow<PackSyncResult> = service.updates(festivalId)
        .map { it.toDomain() }
        .onEach(::record)
        .catch { throw if (it is CancellationException) it else it.toPackError() }

    override fun downloadPack(festivalId: String): Flow<PackDownloadEvent> = service.download(festivalId)
        .map { it.toDomain() }
        .onStart { mutableState.update { it.copy(downloads = it.downloads + (festivalId to null), failures = it.failures - festivalId) } }
        .onEach { event ->
            when (event) {
                PackDownloadEvent.Reset -> setProgress(festivalId, null)
                is PackDownloadEvent.Progress -> setProgress(festivalId, event.progress)
                is PackDownloadEvent.Finished -> record(event.result)
            }
        }
        .catch { cause ->
            if (cause is CancellationException) throw cause
            val error = cause.toPackError()
            mutableState.update { it.copy(failures = it.failures + (festivalId to error)) }
            throw error
        }
        .onCompletion { mutableState.update { it.copy(downloads = it.downloads - festivalId) } }

    override suspend fun cachedPack(festivalId: String): CachedFestivalPack? = mapPackErrors { service.cachedPack(festivalId)?.toDomain() }

    override suspend fun syncPack(festivalId: String): PackSyncResult = mapPackErrors {
        service.sync(festivalId).toDomain().also(::record)
    }

    override suspend fun deletePack(festivalId: String) = mapPackErrors {
        service.deletePack(festivalId)
        mutableState.update { it.copy(packs = it.packs - festivalId) }
    }

    override suspend fun packDetail(festivalId: String, pack: (CachedFestivalPack) -> FestivalReceivedPack): FestivalPackDetail? =
        mapPackErrors {
            val cached = service.cachedPack(festivalId) ?: return@mapPackErrors null
            val summary = pack(cached.toDomain())
            // 상세 키를 읽지 못하면 빈 상세로 둔다(iOS `try?`). 라이브 보드 문서와 따로 읽는다(설계 D3).
            val detail = try {
                json.decodeFromString(FestivalPackDetailDto.serializer(), cached.pack.decodeToString())
            } catch (_: IllegalArgumentException) { // SerializationException 포함
                null
            }
            detail?.toDomain(summary) ?: FestivalPackDetail(summary)
        }

    private fun setProgress(festivalId: String, progress: PackDownloadProgress?) {
        mutableState.update { state ->
            if (festivalId in state.downloads) state.copy(downloads = state.downloads + (festivalId to progress)) else state
        }
    }

    private fun record(result: PackSyncResult) {
        val pack = when (result) {
            is PackSyncResult.Updated -> result.pack
            is PackSyncResult.NotModified -> result.pack
            else -> return
        }
        mutableState.update { it.copy(packs = it.packs + (pack.festivalId to pack)) }
    }

    private fun ServiceSyncResult.toDomain(): PackSyncResult = when (this) {
        is ServiceSyncResult.Updated -> PackSyncResult.Updated(pack.toDomain())
        is ServiceSyncResult.NotModified -> PackSyncResult.NotModified(pack.toDomain())
        is ServiceSyncResult.RetryAfter -> PackSyncResult.RetryAfter(after)
        ServiceSyncResult.Reconnecting -> PackSyncResult.Reconnecting
    }

    private fun ServiceDownloadEvent.toDomain(): PackDownloadEvent = when (this) {
        ServiceDownloadEvent.Reset -> PackDownloadEvent.Reset
        is ServiceDownloadEvent.Progress -> PackDownloadEvent.Progress(PackDownloadProgress(progress.receivedBytes, progress.totalBytes))
        is ServiceDownloadEvent.Finished -> PackDownloadEvent.Finished(result.toDomain())
    }

    /** 팩 JSON을 상세와 같은 문서 DTO로 읽는다. 해석하지 못하면 문서만 null(보드는 상세 조회로 그린다). */
    private fun ServicePack.toDomain() = CachedFestivalPack(
        festivalId = festivalId,
        packVersion = packVersion,
        etag = etag,
        checkedAt = checkedAtMillis?.let(Instant::ofEpochMilli),
        sizeBytes = pack.size,
        document = try {
            json.decodeFromString(FestivalPackDocumentDto.serializer(), pack.decodeToString())
                .takeIf { it.id == festivalId }
                ?.toDomain()
        } catch (_: IllegalArgumentException) { // SerializationException 포함
            null
        },
    )
}

private inline fun <T> mapPackErrors(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    throw e.toPackError()
}

internal fun Throwable.toPackError(): FestivalPackError = when {
    this is FestivalPackError -> this

    PackSyncError.isTransportFailure(this) -> FestivalPackError.Offline()

    this is PackSyncError.InvalidFestivalId -> FestivalPackError.InvalidFestivalId()

    this is PackSyncError.InvalidMessage || this is PackSyncError.InvalidPack || this is PackSyncError.InvalidChunk ->
        FestivalPackError.InvalidPack()

    this is PackSyncError.PackTooLarge -> FestivalPackError.PackTooLarge()

    this is PackSyncError.PackDeleted -> FestivalPackError.PackDeleted()

    this is PackSyncError.Server -> FestivalPackError.Server(code)

    else -> FestivalPackError.Unavailable() // 서버 주소 없음 · 저장소 IO 실패 등
}.also { if (it !== this) it.initCause(this) }
