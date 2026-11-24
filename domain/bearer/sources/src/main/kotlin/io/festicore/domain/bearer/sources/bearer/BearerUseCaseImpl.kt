package io.festicore.domain.bearer.sources.bearer

import io.festicore.domain.bearer.bearer.BearerRepository
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFollowedFlag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.sources.model.dto.BearerPackDto
import io.festicore.domain.bearer.sources.model.mapper.toDomain
import io.festicore.service.pack.PackSyncService
import java.time.Clock
import java.util.Locale
import java.util.UUID

/**
 * 기수 UseCase(iOS `BearerUseCaseImpl`). 코드 형식 확인 · 멱등키 · 누른 시각을 만들고 나머지는 저장소에 맡긴다.
 * [newUuid] · [clock]은 테스트 결정성을 위해 주입한다(iOS `@Dependency(\.uuid)` · `\.date`).
 */
internal class BearerUseCaseImpl(
    private val repository: BearerRepository,
    private val packSyncService: PackSyncService,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC(),
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

    override fun makeStatusRequest(change: BearerStatusChange) =
        BearerStatusRequest(change, idempotencyKey = newUuid().lowercase(Locale.ROOT), clientAt = clock.instant())

    override suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus = repository.sendStatus(flagId, request)

    override suspend fun inviteCoBearer(flagId: String): BearerInvite = repository.inviteCoBearer(flagId)

    override suspend fun removeCoBearer(flagId: String, userId: String) = repository.removeCoBearer(flagId, userId)

    private companion object {
        val CODE_PATTERN = Regex("^[A-HJ-NP-Z2-9]{6}$")
    }
}
