package io.festicore.domain.bearer.bearer

import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFollowedFlag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.entity.BearerPhotoUploadEvent
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import kotlinx.coroutines.flow.Flow

/**
 * 기수 화면(코드 인증 · 깃발 등록 · 깃발 프로필 · 기수 보드)이 하는 일. iOS `BearerUseCaseInterface`.
 * 상태 대기열은 BD3에서 더한다.
 */
interface BearerUseCase {
    /** 코드를 대문자로 맞추고 서버 형식(I · O · 0 · 1 제외 6자리)이 아니면 보내지 않고 `BearerError.InvalidRequest`. */
    suspend fun claim(festivalId: String, code: String): BearerClaim

    /** 새 멱등키(`register-<uuid>`)로 등록 한 건을 만든다. 같은 내용을 다시 보낼 때는 만든 값을 그대로 쓴다. */
    fun makeRegisterRequest(festivalId: String, change: BearerProfileChange): BearerRegisterRequest

    suspend fun register(request: BearerRegisterRequest): BearerClaim

    suspend fun fetchBearerFlags(festivalId: String?): List<BearerOwnedFlag>

    suspend fun fetchPinnedFlag(): BearerPinnedFlag?

    /** 잠금화면 대표 깃발로 고를 수 있는 팔로우한 깃발(최근 팔로우순) */
    suspend fun fetchFollowedFlags(festivalId: String?): List<BearerFollowedFlag>

    /** 기기에 받아 둔 팩에서 축제 이름 · 존 · 공연을 읽는다. 팩이 없거나 해석하지 못하면 빈 목록이다. */
    suspend fun fetchFestival(festivalId: String): BearerFestival

    suspend fun fetchBoard(flagId: String): BearerBoard

    suspend fun updateProfile(flagId: String, change: BearerProfileChange): BearerFlagProfile

    /**
     * 사진을 서버 규격(JPEG · 5MB 이하 · 짧은 변 256px 이상 · 긴 변 2048px)으로 다시 인코딩해 올린다.
     * 픽셀만 새로 쓰므로 EXIF · GPS 등 메타데이터는 나가지 않는다. 진행률(0~1)을 알린 뒤 완료로 끝나고, 실패는 `BearerError`로 끝난다.
     */
    fun uploadPhoto(flagId: String, imageData: ByteArray): Flow<BearerPhotoUploadEvent>

    /** 누른 시각과 새 멱등키로 전송 한 건을 만든다. */
    fun makeStatusRequest(change: BearerStatusChange): BearerStatusRequest

    suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus

    suspend fun inviteCoBearer(flagId: String): BearerInvite

    suspend fun removeCoBearer(flagId: String, userId: String)
}
