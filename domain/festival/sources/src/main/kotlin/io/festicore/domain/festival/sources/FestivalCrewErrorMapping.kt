package io.festicore.domain.festival.sources

import io.festicore.core.network.NetworkError
import io.festicore.domain.festival.FestivalCrewError
import io.festicore.service.api.AuthSessionError
import kotlin.coroutines.cancellation.CancellationException

private const val TERMS_REQUIRED = "TERMS_REQUIRED"
private const val CREW_FULL = "CREW_FULL"
private const val CREW_ALREADY_IN = "CREW_ALREADY_IN"

/** 크루 요청 실패를 [FestivalCrewError]로 바꾼다. [http]가 null을 주면 공통 매핑으로 넘어간다. */
private inline fun Throwable.toCrewError(http: NetworkError.Http.() -> FestivalCrewError?): Throwable = when (this) {
    is CancellationException, is FestivalCrewError -> this
    is AuthSessionError -> FestivalCrewError.Unauthenticated()
    is NetworkError.Decoding, is NetworkError.InvalidResponse -> FestivalCrewError.InvalidResponse()
    is NetworkError.Http -> http() ?: commonHttpError()
    else -> FestivalCrewError.Unavailable()
}

/** iOS `FestivalCrewRepositoryImpl.map`(`CRp:317-330`). */
private fun NetworkError.Http.commonHttpError(): FestivalCrewError = when (status) {
    400 -> FestivalCrewError.InvalidInput()
    401 -> FestivalCrewError.Unauthenticated()
    403 -> if (code == TERMS_REQUIRED) FestivalCrewError.TermsRequired() else FestivalCrewError.Forbidden()
    404 -> FestivalCrewError.NotFound()
    409 -> if (code == CREW_FULL) FestivalCrewError.Full() else FestivalCrewError.AlreadyInCrew()
    410 -> FestivalCrewError.FestivalEnded()
    429 -> FestivalCrewError.Cooldown()
    else -> FestivalCrewError.Unavailable()
}

/** 상세 · 상태 · 초대 · 이름 · 내보내기 · 이전 · 해산 · 미리보기 · 참여. */
internal fun Throwable.toFestivalCrewError(): Throwable = toCrewError { null }

/** iOS `create`. 409는 `CREW_ALREADY_IN`만, 403은 `TERMS_REQUIRED`만 본다. 404 · 429 등은 Unavailable. */
internal fun Throwable.toFestivalCrewCreateError(): Throwable = toCrewError {
    when (status) {
        400, 401, 410 -> commonHttpError()
        403 -> if (code == TERMS_REQUIRED) FestivalCrewError.TermsRequired() else FestivalCrewError.Unavailable()
        409 -> if (code == CREW_ALREADY_IN) FestivalCrewError.AlreadyInCrew() else FestivalCrewError.Unavailable()
        else -> FestivalCrewError.Unavailable()
    }
}

/** iOS `gather`. 400 · 401 · 403 · 410 · 429만 고른다(429 cooldown). */
internal fun Throwable.toFestivalCrewGatherError(): Throwable = toCrewError {
    when (status) {
        400, 401, 403, 410, 429 -> commonHttpError()
        else -> FestivalCrewError.Unavailable()
    }
}

/** iOS `respondToGather`. 10분이 지난 모여는 409 `GATHER_EXPIRED`. 보낸 사람(403 `GATHER_SENDER`)은 공통 403. */
internal fun Throwable.toFestivalCrewGatherReplyError(): Throwable = toCrewError {
    if (status == 409) FestivalCrewError.GatherExpired() else null
}

/** iOS `leave`. 크루장은 409 `OWNER_CANNOT_LEAVE`로 막힌다. */
internal fun Throwable.toFestivalCrewLeaveError(): Throwable = toCrewError {
    if (status == 409) FestivalCrewError.Forbidden() else null
}
