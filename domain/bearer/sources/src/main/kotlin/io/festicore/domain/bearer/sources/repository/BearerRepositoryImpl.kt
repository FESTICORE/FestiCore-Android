package io.festicore.domain.bearer.sources.repository

import io.festicore.core.network.FestiCoreJson
import io.festicore.domain.bearer.bearer.BearerRepository
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFlagPhoto
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFollowedFlag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.sources.model.dto.BearerBoardDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerClaimDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerEnvelopeDto
import io.festicore.domain.bearer.sources.model.dto.BearerErrorBodyDto
import io.festicore.domain.bearer.sources.model.dto.BearerFlagProfileDto
import io.festicore.domain.bearer.sources.model.dto.BearerFlagStatusDto
import io.festicore.domain.bearer.sources.model.dto.BearerFlagsDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerFollowedFlagDetailDto
import io.festicore.domain.bearer.sources.model.dto.BearerFollowsDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerInviteDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerPhotoResponseDto
import io.festicore.domain.bearer.sources.model.dto.BearerPinnedFlagDataDto
import io.festicore.domain.bearer.sources.model.mapper.BearerImageUrl
import io.festicore.domain.bearer.sources.model.mapper.bearerError
import io.festicore.domain.bearer.sources.model.mapper.toBearerError
import io.festicore.domain.bearer.sources.model.mapper.toBody
import io.festicore.domain.bearer.sources.model.mapper.toDomain
import io.festicore.domain.bearer.sources.outbox.BearerServerClock
import io.festicore.service.apiendpoint.flag.BearerClaimBody
import io.festicore.service.apiendpoint.flag.FlagRequest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException

/**
 * 기수 API 저장소(iOS `BearerRepositoryImpl`). 모든 요청을 현재 세션에 묶어 보내고 재시도하지 않는다.
 * 세션이 없으면 요청하지 않고 [BearerError.NotAuthenticated]. 실패는 [BearerError]로 바꾼다.
 * 보드 · 상태 응답의 `Date` 헤더로 [serverClock]을 맞춘다(iOS에 없음, 설계 "시계 오차").
 */
internal class BearerRepositoryImpl(
    private val api: FlagRequest,
    private val currentSessionId: suspend () -> String?,
    private val installationDeviceId: suspend () -> String,
    private val imageUrl: BearerImageUrl,
    private val serverClock: BearerServerClock = BearerServerClock(),
) : BearerRepository {
    override suspend fun claim(festivalId: String, code: String): BearerClaim {
        val sessionId = sessionId()
        val deviceId = try {
            installationDeviceId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw BearerError.Unavailable()
        }
        val response = mapErrors { api.claim(BearerClaimBody(festivalId, code), deviceId, sessionId) }
        // 잠금 남은 시간(`data.retryAfter`) · 남은 횟수를 읽으려고 원시 응답을 직접 해석한다.
        if (response.status !in SUCCESS_STATUS) {
            val body = response.body.decodeOrNull(BearerErrorBodyDto.serializer())
            throw bearerError(response.status, body?.code, body?.data?.remainingAttempts, body?.data?.retryAfter)
        }
        val decoded = response.body.decodeOrNull(BearerEnvelopeDto.serializer(BearerClaimDataDto.serializer()))
            ?: throw BearerError.InvalidResponse()
        return decoded.data.toDomain(imageUrl)
    }

    override suspend fun register(request: BearerRegisterRequest): BearerClaim {
        val sessionId = sessionId()
        return mapErrors {
            api.register<BearerClaimDataDto>(request.toBody(), request.idempotencyKey, sessionId).toDomain(imageUrl)
        }
    }

    override suspend fun fetchBearerFlags(festivalId: String?): List<BearerOwnedFlag> {
        val sessionId = sessionId()
        return mapErrors { api.bearerFlags<BearerFlagsDataDto>(festivalId, sessionId).items.map { it.toDomain(imageUrl) } }
    }

    override suspend fun fetchPinnedFlag(): BearerPinnedFlag? {
        val sessionId = sessionId()
        return mapErrors { api.pinnedFlag<BearerPinnedFlagDataDto>(sessionId).toDomain(imageUrl) }
    }

    override suspend fun fetchFollowedFlags(festivalId: String?): List<BearerFollowedFlag> {
        val sessionId = sessionId()
        val follows = mapErrors { api.follows<BearerFollowsDataDto>(festivalId, sessionId).items }
        // 팔로우 목록에는 이름이 없어 깃발 공개 상세를 함께 읽고, 순서는 팔로우 목록을 따른다.
        val details = coroutineScope { follows.map { async { followedFlagDetail(it.flagId) } }.awaitAll() }
        return follows.zip(details).mapNotNull { (follow, detail) -> detail?.let { follow.toDomain(it, imageUrl) } }
    }

    override suspend fun fetchBoard(flagId: String): BearerBoard {
        val sessionId = sessionId()
        return mapErrors { api.bearer<BearerBoardDataDto>(flagId, sessionId, serverClock::record).toDomain(imageUrl) }
    }

    override suspend fun updateProfile(flagId: String, change: BearerProfileChange): BearerFlagProfile {
        val sessionId = sessionId()
        return mapErrors { api.updateProfile<BearerFlagProfileDto>(flagId, change.toBody(), sessionId).toDomain(imageUrl) }
    }

    override suspend fun uploadPhoto(
        flagId: String,
        jpeg: ByteArray,
        onProgress: (Double) -> Unit,
    ): BearerFlagPhoto {
        val sessionId = sessionId()
        val response = mapErrors { api.uploadPhoto<BearerPhotoResponseDto>(flagId, jpeg, sessionId, onProgress) }
        return BearerFlagPhoto(imageUrl.resolve(response.photoUrl), imageUrl.resolve(response.thumbUrl))
    }

    override suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus =
        sendStatus(flagId, request, sessionId())

    /** 대기열이 고른 세션 [expectedSessionId]로만 보낸다. 그 사이 세션이 바뀌면 인증 플러그인이 막아 [BearerError.NotAuthenticated]. */
    suspend fun sendStatus(
        flagId: String,
        request: BearerStatusRequest,
        expectedSessionId: String,
    ): BearerFlagStatus = mapErrors {
        api.status<BearerFlagStatusDto>(
            flagId,
            request.toBody(),
            request.idempotencyKey,
            expectedSessionId,
            serverClock::record,
        ).toDomain()
    }

    override suspend fun inviteCoBearer(flagId: String): BearerInvite {
        val sessionId = sessionId()
        return mapErrors { api.inviteCoBearer<BearerInviteDataDto>(flagId, sessionId).toDomain() }
    }

    override suspend fun removeCoBearer(flagId: String, userId: String) {
        val sessionId = sessionId()
        mapErrors { api.removeCoBearer(flagId, userId, sessionId) }
    }

    private suspend fun sessionId(): String = currentSessionId() ?: throw BearerError.NotAuthenticated()

    /** 숨김 · 비공개로 바뀌어 공개 조회할 수 없는(404) 깃발은 고를 수 없으므로 null로 뺀다. 그 밖의 실패는 목록 전체 실패다. */
    private suspend fun followedFlagDetail(flagId: String): BearerFollowedFlagDetailDto? = try {
        api.detail<BearerFollowedFlagDetailDto>(flagId)
    } catch (e: Exception) {
        val mapped = e.toBearerError()
        if (mapped is BearerError.NotFound) null else throw mapped
    }

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw e.toBearerError()
    }

    private fun <T> String.decodeOrNull(serializer: kotlinx.serialization.KSerializer<T>): T? = try {
        FestiCoreJson.decodeFromString(serializer, this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val SUCCESS_STATUS = 200..299
    }
}
