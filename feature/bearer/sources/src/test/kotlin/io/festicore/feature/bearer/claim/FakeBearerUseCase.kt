package io.festicore.feature.bearer.claim

import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFestivalTransfer
import io.festicore.domain.bearer.entity.BearerFestivalTransferRequest
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFlagTag
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

/** 테스트 전용 가짜 값(iOS `BearerSample.profile`). 코드 등록 직후 서버 임시 이름 · 기본 태그다. */
internal val SampleProfile = BearerFlagProfile(id = "flg_1", name = "새 깃발 a1b2c3", tags = listOf(BearerFlagTag.Newbie))

/** 화면 테스트용 가짜 UseCase(iOS `BearerUseCaseStub`). 넘기지 않은 함수는 부르면 실패한다. 서버에 쓰지 않는다. */
internal class FakeBearerUseCase(
    private val onClaim: suspend (festivalId: String, code: String) -> BearerClaim = { _, _ -> SampleClaim },
    private val onFetchFestival: (suspend (festivalId: String) -> BearerFestival)? = null,
    private val onFetchBoard: (suspend (flagId: String) -> BearerBoard)? = null,
    private val onUpdateProfile: (suspend (flagId: String, change: BearerProfileChange) -> BearerFlagProfile)? = null,
    private val onUploadPhoto: ((flagId: String, imageData: ByteArray) -> Flow<BearerPhotoUploadEvent>)? = null,
    private val onInviteCoBearer: (suspend (flagId: String) -> BearerInvite)? = null,
    private val onMakeRegisterRequest: ((festivalId: String, change: BearerProfileChange) -> BearerRegisterRequest)? = null,
    private val onRegister: (suspend (request: BearerRegisterRequest) -> BearerClaim)? = null,
    private val onMakeStatusRequest: ((change: BearerStatusChange) -> BearerStatusRequest)? = null,
    private val onEnqueueStatus: (suspend (flagId: String, ownerUserId: String, request: BearerStatusRequest) -> Unit)? = null,
    private val onPendingStatus: (suspend (flagId: String) -> List<BearerStatusRequest>)? = null,
    private val onStatusEvents: ((flagId: String) -> Flow<BearerOutboxEvent>)? = null,
    private val onRetryPendingStatus: (suspend () -> Unit)? = null,
    private val onMakeFestivalTransferRequest: (
        (flagId: String, fromFestivalId: String, toFestivalId: String) -> BearerFestivalTransferRequest
    )? = null,
    private val onFetchBearerFlags: (suspend (festivalId: String?) -> List<BearerOwnedFlag>)? = null,
    private val onTransferFestival: (suspend (request: BearerFestivalTransferRequest) -> BearerFestivalTransfer)? = null,
) : BearerUseCase {
    override suspend fun claim(festivalId: String, code: String): BearerClaim = onClaim(festivalId, code)

    override fun makeRegisterRequest(festivalId: String, change: BearerProfileChange): BearerRegisterRequest =
        (onMakeRegisterRequest ?: unused())(festivalId, change)

    override suspend fun register(request: BearerRegisterRequest): BearerClaim = (onRegister ?: unused())(request)

    override suspend fun fetchBearerFlags(festivalId: String?): List<BearerOwnedFlag> = (onFetchBearerFlags ?: unused())(festivalId)

    override suspend fun fetchPinnedFlag(): BearerPinnedFlag? = unused()

    override suspend fun fetchFollowedFlags(festivalId: String?): List<BearerFollowedFlag> = unused()

    override suspend fun fetchFestival(festivalId: String): BearerFestival = (onFetchFestival ?: unused())(festivalId)

    override suspend fun fetchBoard(flagId: String): BearerBoard = (onFetchBoard ?: unused())(flagId)

    override fun makeFestivalTransferRequest(
        flagId: String,
        fromFestivalId: String,
        toFestivalId: String,
    ): BearerFestivalTransferRequest = (onMakeFestivalTransferRequest ?: unused())(flagId, fromFestivalId, toFestivalId)

    override suspend fun transferFestival(request: BearerFestivalTransferRequest): BearerFestivalTransfer =
        (onTransferFestival ?: unused())(request)

    override suspend fun updateProfile(flagId: String, change: BearerProfileChange): BearerFlagProfile =
        (onUpdateProfile ?: unused())(flagId, change)

    override fun uploadPhoto(flagId: String, imageData: ByteArray): Flow<BearerPhotoUploadEvent> =
        (onUploadPhoto ?: unused())(flagId, imageData)

    override fun makeStatusRequest(change: BearerStatusChange): BearerStatusRequest = (onMakeStatusRequest ?: unused())(change)

    override suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus = unused()

    override suspend fun enqueueStatus(
        flagId: String,
        ownerUserId: String,
        request: BearerStatusRequest,
    ): Unit = (onEnqueueStatus ?: unused())(flagId, ownerUserId, request)

    override suspend fun pendingStatus(flagId: String): List<BearerStatusRequest> = (onPendingStatus ?: unused())(flagId)

    override fun statusEvents(flagId: String): Flow<BearerOutboxEvent> = (onStatusEvents ?: unused())(flagId)

    override suspend fun retryPendingStatus(): Unit = (onRetryPendingStatus ?: unused())()

    override suspend fun inviteCoBearer(flagId: String): BearerInvite = (onInviteCoBearer ?: unused())(flagId)

    override suspend fun removeCoBearer(flagId: String, userId: String): Unit = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("이 테스트는 이 함수를 넘기지 않았다")
}

/** 테스트가 시각을 옮기는 시계(iOS `DateGenerator { current.value }`). */
internal class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
