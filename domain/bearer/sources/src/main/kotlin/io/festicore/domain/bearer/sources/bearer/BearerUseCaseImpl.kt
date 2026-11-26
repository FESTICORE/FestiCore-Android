package io.festicore.domain.bearer.sources.bearer

import io.festicore.domain.bearer.bearer.BearerRepository
import io.festicore.domain.bearer.bearer.BearerStatusOutbox
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFestivalTransfer
import io.festicore.domain.bearer.entity.BearerFestivalTransferRequest
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFollowedFlag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerOutboxEvent
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.entity.BearerPhotoUploadEvent
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.sources.model.dto.BearerPackDto
import io.festicore.domain.bearer.sources.model.mapper.toDomain
import io.festicore.domain.bearer.sources.photo.BearerPhotoEncoder
import io.festicore.service.pack.PackSyncService
import java.time.Clock
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/**
 * 기수 UseCase(iOS `BearerUseCaseImpl`). 코드 형식 확인 · 멱등키 · 누른 시각을 만들고 나머지는 저장소에 맡긴다.
 * [newUuid] · [clock]은 테스트 결정성을 위해 주입한다(iOS `@Dependency(\.uuid)` · `\.date`).
 * 앱에서는 [clock]에 서버 시각 추정 시계(`BearerServerClock`)를 넣어 누른 시각이 기기 시계 오차를 따르지 않게 한다.
 * 상태 변경 대기열은 [outbox]에 맡긴다.
 * 사진 인코딩은 CPU 작업이라 [encodeContext]에서 한다.
 */
internal class BearerUseCaseImpl(
    private val repository: BearerRepository,
    private val packSyncService: PackSyncService,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC(),
    private val photoEncoder: BearerPhotoEncoder = BearerPhotoEncoder(),
    private val encodeContext: CoroutineContext = Dispatchers.Default,
    private val outbox: BearerStatusOutbox,
) : BearerUseCase {
    override suspend fun claim(festivalId: String, code: String): BearerClaim {
        val normalized = code.uppercase(Locale.ROOT)
        // 서버 코드 형식(I · O · 0 · 1 제외 6자리)이 아니면 시도 횟수를 쓰지 않게 보내지 않는다.
        if (!CODE_PATTERN.matches(normalized)) throw BearerError.InvalidRequest()
        return repository.claim(festivalId, normalized)
    }

    override fun makeRegisterRequest(festivalId: String, change: BearerProfileChange) =
        BearerRegisterRequest(festivalId, change, idempotencyKey = "register-${newUuid().lowercase(Locale.ROOT)}")

    override suspend fun register(request: BearerRegisterRequest): BearerClaim = repository.register(request)

    override suspend fun fetchBearerFlags(festivalId: String?): List<BearerOwnedFlag> = repository.fetchBearerFlags(festivalId)

    override suspend fun fetchPinnedFlag(): BearerPinnedFlag? = repository.fetchPinnedFlag()

    override suspend fun fetchFollowedFlags(festivalId: String?): List<BearerFollowedFlag> = repository.fetchFollowedFlags(festivalId)

    override suspend fun fetchFestival(festivalId: String): BearerFestival {
        val pack = packSyncService.cachedPack(festivalId)?.let { BearerPackDto.parse(it.pack) }
            ?: return BearerFestival(festivalId, name = null, zones = emptyList())
        return pack.toDomain(festivalId)
    }

    override suspend fun fetchBoard(flagId: String): BearerBoard = repository.fetchBoard(flagId)

    override suspend fun updateProfile(flagId: String, change: BearerProfileChange): BearerFlagProfile =
        repository.updateProfile(flagId, change)

    override fun makeFestivalTransferRequest(
        flagId: String,
        fromFestivalId: String,
        toFestivalId: String,
    ) = BearerFestivalTransferRequest(flagId, fromFestivalId, toFestivalId, idempotencyKey = "transfer-${newUuid().lowercase(Locale.ROOT)}")

    override suspend fun transferFestival(request: BearerFestivalTransferRequest): BearerFestivalTransfer =
        repository.transferFestival(request)

    override fun uploadPhoto(flagId: String, imageData: ByteArray): Flow<BearerPhotoUploadEvent> = channelFlow {
        val jpeg = withContext(encodeContext) { photoEncoder.encode(imageData) }
        // 진행률은 콜백에서 오므로 trySend로 넣는다. 버퍼가 차면 중간 진행률만 빠진다.
        val photo = repository.uploadPhoto(flagId, jpeg) { fraction ->
            trySend(BearerPhotoUploadEvent.Progress(fraction.coerceIn(0.0, 1.0)))
        }
        send(BearerPhotoUploadEvent.Completed(photo))
    }

    override fun makeStatusRequest(change: BearerStatusChange) =
        BearerStatusRequest(change, idempotencyKey = newUuid().lowercase(Locale.ROOT), clientAt = clock.instant())

    override suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus = repository.sendStatus(flagId, request)

    override suspend fun enqueueStatus(
        flagId: String,
        ownerUserId: String,
        request: BearerStatusRequest,
    ) = outbox.enqueue(flagId, ownerUserId, request)

    override suspend fun pendingStatus(flagId: String): List<BearerStatusRequest> = outbox.pending(flagId)

    override fun statusEvents(flagId: String): Flow<BearerOutboxEvent> = outbox.events(flagId)

    override suspend fun retryPendingStatus() = outbox.resume()

    override suspend fun inviteCoBearer(flagId: String): BearerInvite = repository.inviteCoBearer(flagId)

    override suspend fun removeCoBearer(flagId: String, userId: String) = repository.removeCoBearer(flagId, userId)

    private companion object {
        val CODE_PATTERN = Regex("^[A-HJ-NP-Z2-9]{6}$")
    }
}
