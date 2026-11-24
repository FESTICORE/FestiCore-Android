package io.festicore.domain.bearer.bearer

import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFollowedFlag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerStatusRequest

/**
 * 기수 API 저장소. 모든 요청은 로그인 세션에 묶여 보내고, 세션이 없으면 보내지 않고 `BearerError.NotAuthenticated`.
 * 실패는 `BearerError`. iOS `BearerRepositoryInterface`(사진 업로드는 BD2).
 */
interface BearerRepository {
    /** [code]는 UseCase가 대문자로 맞추고 형식을 확인한 값이다. */
    suspend fun claim(festivalId: String, code: String): BearerClaim

    /** 코드 없이 깃발을 만들고 원래 기수가 된다. 응답은 코드 인증과 같다. */
    suspend fun register(request: BearerRegisterRequest): BearerClaim

    /** 내가 기수인 깃발. [festivalId]를 주면 그 페스티벌 것만 받는다. */
    suspend fun fetchBearerFlags(festivalId: String?): List<BearerOwnedFlag>

    /** 내 대표 깃발. 지정하지 않았으면 null이다. */
    suspend fun fetchPinnedFlag(): BearerPinnedFlag?

    /** 내가 팔로우한 깃발(최근 팔로우순)과 이름. 공개 조회할 수 없는(404) 깃발은 뺀다. */
    suspend fun fetchFollowedFlags(festivalId: String?): List<BearerFollowedFlag>

    suspend fun fetchBoard(flagId: String): BearerBoard

    suspend fun updateProfile(flagId: String, change: BearerProfileChange): BearerFlagProfile

    suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus

    suspend fun inviteCoBearer(flagId: String): BearerInvite

    suspend fun removeCoBearer(flagId: String, userId: String)
}
