package io.festicore.feature.bearer.claim

import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerFestival
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** 테스트 전용 가짜 값(iOS `BearerSample.claim`). 서버에 쓰지 않는다. */
internal val SampleClaim =
    BearerClaim(
        flagId = "flg_1",
        profile = BearerFlagProfile(id = "flg_1", name = "테스트 깃발"),
        isNew = true,
        coBearerIds = emptyList(),
    )

/** 코드 인증만 쓰는 가짜 UseCase(iOS `BearerUseCaseStub(claim:)`). 다른 함수는 이 화면이 부르지 않는다. */
internal class FakeBearerUseCase(
    private val onClaim: suspend (festivalId: String, code: String) -> BearerClaim = { _, _ -> SampleClaim },
) : BearerUseCase {
    override suspend fun claim(festivalId: String, code: String): BearerClaim = onClaim(festivalId, code)

    override fun makeRegisterRequest(festivalId: String, change: BearerProfileChange): BearerRegisterRequest = unused()

    override suspend fun register(request: BearerRegisterRequest): BearerClaim = unused()

    override suspend fun fetchBearerFlags(festivalId: String?): List<BearerOwnedFlag> = unused()

    override suspend fun fetchPinnedFlag(): BearerPinnedFlag? = unused()

    override suspend fun fetchFollowedFlags(festivalId: String?): List<BearerFollowedFlag> = unused()

    override suspend fun fetchFestival(festivalId: String): BearerFestival = unused()

    override suspend fun fetchBoard(flagId: String): BearerBoard = unused()

    override suspend fun updateProfile(flagId: String, change: BearerProfileChange): BearerFlagProfile = unused()

    override fun uploadPhoto(flagId: String, imageData: ByteArray): Flow<BearerPhotoUploadEvent> = unused()

    override fun makeStatusRequest(change: BearerStatusChange): BearerStatusRequest = unused()

    override suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus = unused()

    override suspend fun enqueueStatus(
        flagId: String,
        ownerUserId: String,
        request: BearerStatusRequest,
    ): Unit = unused()

    override suspend fun pendingStatus(flagId: String): List<BearerStatusRequest> = unused()

    override fun statusEvents(flagId: String): Flow<BearerOutboxEvent> = unused()

    override suspend fun retryPendingStatus(): Unit = unused()

    override suspend fun inviteCoBearer(flagId: String): BearerInvite = unused()

    override suspend fun removeCoBearer(flagId: String, userId: String): Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("기수 인증 화면은 이 함수를 부르지 않는다")
}

/** 테스트가 시각을 옮기는 시계(iOS `DateGenerator { current.value }`). */
internal class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
