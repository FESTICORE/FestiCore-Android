package io.festicore.domain.bearer.sources.model.mapper

import io.festicore.core.network.error.NetworkError
import io.festicore.core.utility.date.AppDateFormat
import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFestivalTransfer
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerFollowedFlag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerSet
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.domain.bearer.sources.model.dto.BearerBoardDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerClaimDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerCoBearerDto
import io.festicore.domain.bearer.sources.model.dto.BearerFestivalTransferDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerFlagProfileDto
import io.festicore.domain.bearer.sources.model.dto.BearerFlagStatusDto
import io.festicore.domain.bearer.sources.model.dto.BearerFlagsDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerFollowedFlagDetailDto
import io.festicore.domain.bearer.sources.model.dto.BearerFollowsDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerInviteDataDto
import io.festicore.domain.bearer.sources.model.dto.BearerPackDto
import io.festicore.domain.bearer.sources.model.dto.BearerPinnedFlagDataDto
import io.festicore.service.api.base.AuthSessionError
import java.net.URI
import java.net.URISyntaxException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 서버가 주는 사진 값을 불러올 주소로 바꾼다. iOS `BearerImageURL`.
 * 스킴이 있으면 https만 그대로, 없으면 `/`로 시작하고 `//`가 아니며 [baseUrl]이 https일 때만 합친다. 나머지는 null.
 */
internal class BearerImageUrl(private val baseUrl: String?) {
    fun resolve(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val uri = value.toUriOrNull() ?: return null
        uri.scheme?.let { scheme -> return value.takeIf { scheme.equals(HTTPS, ignoreCase = true) } }
        if (!value.startsWith("/") || value.startsWith("//")) return null
        val base = baseUrl?.toUriOrNull() ?: return null
        if (!base.scheme.equals(HTTPS, ignoreCase = true)) return null
        return base.resolve(uri).toString()
    }

    private fun String.toUriOrNull(): URI? = try {
        URI(this)
    } catch (_: URISyntaxException) {
        null
    }

    private companion object {
        const val HTTPS = "https"
    }
}

/** 모르는 태그는 버리고 빠진 값은 iOS 기본값(팔로워 0 · 계획 공개 · 빈 계획)으로 채운다. */
internal fun BearerFlagProfileDto.toDomain(imageUrl: BearerImageUrl) = BearerFlagProfile(
    id = id,
    name = name,
    photoUrl = imageUrl.resolve(photoUrl),
    thumbnailUrl = imageUrl.resolve(thumbUrl),
    tags = tags.orEmpty().mapNotNull(BearerFlagTag::from),
    bio = bio,
    followerCount = followerCount ?: 0,
    planVisible = planVisible ?: true,
    plan = plan.orEmpty(),
    homeZoneId = homeZoneId,
)

/** 모르는 상태 · 시각 형식 오류는 [BearerError.InvalidResponse]. 모르는 활동(`resting` 포함)은 null. */
internal fun BearerFlagStatusDto.toDomain() = BearerFlagStatus(
    flagId = flagId,
    lifecycle = BearerFlagLifecycle.from(status) ?: throw BearerError.InvalidResponse(),
    zoneId = zoneId,
    activity = BearerActivity.from(activity),
    updatedAt = AppDateFormat.isoInstant(updatedAt) ?: throw BearerError.InvalidResponse(),
    updatedBy = updatedBy?.toDomain(),
)

private fun BearerCoBearerDto.toDomain() = BearerCoBearer(userId, nickname.orEmpty())

internal fun BearerBoardDataDto.toDomain(imageUrl: BearerImageUrl): BearerBoard {
    val profile = flag.toDomain(imageUrl)
    return BearerBoard(
        profile = profile,
        status = status.toDomain(),
        followerCount = followerCount ?: profile.followerCount,
        coBearers = coBearers.orEmpty().map { it.toDomain() },
        hidden = hidden ?: false,
        restartUsedToday = restartUsedToday ?: false,
    )
}

internal fun BearerClaimDataDto.toDomain(imageUrl: BearerImageUrl) = BearerClaim(
    flagId = flagId,
    profile = flag.toDomain(imageUrl),
    isNew = isNew ?: false,
    coBearerIds = coBearers.orEmpty(),
)

internal fun BearerFestivalTransferDataDto.toDomain(imageUrl: BearerImageUrl) = BearerFestivalTransfer(
    flagId = flagId,
    previousFestivalId = previousFestivalId,
    festivalId = festivalId,
    profile = flag.toDomain(imageUrl),
)

/** 모르는 상태 값은 버리지 않고 null로 두어 목록에서 깃발을 잃지 않는다. */
internal fun BearerFlagsDataDto.Item.toDomain(imageUrl: BearerImageUrl) = BearerOwnedFlag(
    festivalId = festivalId,
    isOwner = isOwner ?: false,
    joinedAt = joinedAt?.let(AppDateFormat::isoInstant),
    profile = flag.toDomain(imageUrl),
    lifecycle = BearerFlagLifecycle.from(status?.status),
    isHidden = hidden ?: false,
)

internal fun BearerPinnedFlagDataDto.toDomain(imageUrl: BearerImageUrl): BearerPinnedFlag? = flag?.let {
    BearerPinnedFlag(
        flagId = pinnedFlagId ?: it.id,
        name = it.name,
        festivalId = it.festivalId,
        thumbnailUrl = imageUrl.resolve(it.thumbUrl),
    )
}

internal fun BearerFollowsDataDto.Item.toDomain(detail: BearerFollowedFlagDetailDto, imageUrl: BearerImageUrl) = BearerFollowedFlag(
    flagId = flagId,
    festivalId = festivalId,
    name = detail.name,
    thumbnailUrl = imageUrl.resolve(detail.thumbUrl),
    followedAt = followedAt?.let(AppDateFormat::isoInstant),
)

internal fun BearerInviteDataDto.toDomain() = BearerInvite(
    code = code,
    expiresAt = AppDateFormat.isoInstant(expiresAt) ?: throw BearerError.InvalidResponse(),
)

/** 이름 없는 존 · 빈 ID 존은 버리고, 시간이 맞는(끝 > 시작) 공연만 시작 순으로 둔다. 무대 이름은 공연에 없으면 무대 목록에서 찾는다. */
internal fun BearerPackDto.toDomain(festivalId: String): BearerFestival {
    val stageNames = mutableMapOf<String, String>()
    stages.orEmpty().forEach { stage ->
        val id = stage.id ?: return@forEach
        val name = stage.name ?: return@forEach
        stageNames.putIfAbsent(id, name)
    }
    val sets = performances.orEmpty().mapNotNull { performance ->
        val id = performance.id?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val startsAt = performance.startsAt?.let(AppDateFormat::isoInstant) ?: return@mapNotNull null
        val endsAt = performance.endsAt?.let(AppDateFormat::isoInstant) ?: return@mapNotNull null
        if (!endsAt.isAfter(startsAt)) return@mapNotNull null
        BearerSet(
            id = id,
            artist = performance.artist.orEmpty(),
            stageName = performance.stageName ?: performance.stageId?.let(stageNames::get),
            startsAt = startsAt,
            endsAt = endsAt,
        )
    }.sortedBy { it.startsAt }
    return BearerFestival(
        id = festivalId,
        name = name,
        zones = zones.orEmpty().mapNotNull { zone ->
            val id = zone.id?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            BearerZone(id, zone.name ?: return@mapNotNull null)
        },
        sets = sets,
    )
}

/**
 * 요청 오류를 [BearerError]로 바꾼다. iOS `BearerRepositoryImpl.map`.
 * 취소는 그대로, 연결 실패 · 시간 초과는 [BearerError.Offline], 세션 불일치는 [BearerError.NotAuthenticated].
 */
internal fun Throwable.toBearerError(): Throwable = when (this) {
    is CancellationException, is BearerError -> this
    is AuthSessionError -> BearerError.NotAuthenticated()
    is NetworkError.Offline, is NetworkError.Timeout -> BearerError.Offline()
    is NetworkError.Http -> bearerError(status, code)
    is NetworkError.InvalidResponse, is NetworkError.Decoding -> BearerError.InvalidResponse()
    else -> BearerError.Unavailable()
}

/** HTTP 상태 · 서버 코드를 기수 오류로 나눈다. 422 · 423은 실패 본문의 남은 횟수 · 잠금 시간(없으면 600초)을 담는다. */
internal fun bearerError(
    status: Int,
    code: String?,
    remainingAttempts: Int? = null,
    retryAfter: Int? = null,
): BearerError = when (status) {
    400 -> BearerError.InvalidRequest()

    401 -> BearerError.NotAuthenticated()

    403 -> when (code) {
        "TERMS_REQUIRED" -> BearerError.TermsRequired()
        "FLAG_NOT_OWNER" -> BearerError.NotOwner()
        else -> BearerError.NotBearer()
    }

    404 -> if (code == "ZONE_NOT_FOUND") BearerError.ZoneNotFound() else BearerError.NotFound()

    409 -> when (code) {
        "FLAG_NAME_TAKEN" -> BearerError.NameTaken()
        "STATUS_STALE" -> BearerError.Stale()
        "FLAG_TRANSITION", "FLAG_RESTART_REQUIRED" -> BearerError.TransitionRejected()
        "CO_BEARER_FULL" -> BearerError.CoBearerFull()
        "BEARER_LIMIT" -> BearerError.BearerLimit()
        "FLAG_OWNER_REQUIRED" -> BearerError.OwnerRequired()
        "FESTIVAL_MISMATCH" -> BearerError.FestivalMismatch()
        "FLAG_HAS_CO_BEARERS" -> BearerError.HasCoBearers()
        "BEARER_MODE_OFF" -> BearerError.BearerModeOff()
        else -> BearerError.Conflict()
    }

    413 -> BearerError.PhotoTooLarge()

    415 -> BearerError.UnsupportedPhoto()

    422 -> BearerError.InvalidCode(remainingAttempts)

    423 -> BearerError.CodeLocked(retryAfter ?: DEFAULT_LOCK_SECONDS)

    429 -> BearerError.RateLimited()

    else -> BearerError.Unavailable()
}

private const val DEFAULT_LOCK_SECONDS = 600
