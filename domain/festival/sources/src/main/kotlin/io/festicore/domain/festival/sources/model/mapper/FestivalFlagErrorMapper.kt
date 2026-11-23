package io.festicore.domain.festival.sources.model.mapper

import io.festicore.core.network.NetworkError
import io.festicore.domain.festival.entity.FestivalFlagDetailError
import io.festicore.domain.festival.entity.FestivalFlagFollowError
import io.festicore.domain.festival.entity.FestivalFlagPinError
import io.festicore.domain.festival.entity.FestivalFlagReportError
import io.festicore.domain.festival.sources.service.FestivalFlagServiceError
import io.festicore.service.api.base.AuthSessionError
import kotlin.coroutines.cancellation.CancellationException

private const val TERMS_REQUIRED = "TERMS_REQUIRED"
private const val FOLLOW_LIMIT = "FOLLOW_LIMIT"
private const val ALREADY_REPORTED = "ALREADY_REPORTED"
private const val PIN_NOT_FOLLOWING = "PIN_NOT_FOLLOWING"

/** 응답 형식 오류(해석 실패 · `code != SUCCESS` · 요청과 다른 응답). iOS `.decoding`과 `code == "SUCCESS"` 확인. */
private fun Throwable.isInvalidResponse(): Boolean =
    this is NetworkError.Decoding || this is NetworkError.InvalidResponse || this is FestivalFlagServiceError.InvalidResponse

/** iOS `FestivalFlagDetailRepositoryImpl.map`. 잘못된 ID · 404 → NotFound, 형식 오류 → InvalidResponse, 나머지 Unavailable. */
internal fun Throwable.toFestivalFlagDetailError(): Throwable = when {
    this is CancellationException || this is FestivalFlagDetailError -> this
    this is FestivalFlagServiceError.InvalidFlagId -> FestivalFlagDetailError.NotFound()
    isInvalidResponse() -> FestivalFlagDetailError.InvalidResponse()
    this is NetworkError.Http && status == 404 -> FestivalFlagDetailError.NotFound()
    else -> FestivalFlagDetailError.Unavailable()
}

/** iOS `NetworkFestivalFlagFollowService.map`. 409는 `FOLLOW_LIMIT`만 한도 초과로 본다(멱등 키 충돌과 구분). */
internal fun Throwable.toFestivalFlagFollowError(): Throwable = when {
    this is CancellationException || this is FestivalFlagFollowError -> this

    this is FestivalFlagServiceError.InvalidFlagId -> FestivalFlagFollowError.InvalidResponse()

    this is AuthSessionError -> FestivalFlagFollowError.LoginRequired()

    isInvalidResponse() -> FestivalFlagFollowError.InvalidResponse()

    this is NetworkError.Http -> when {
        isUnauthorized -> FestivalFlagFollowError.LoginRequired()
        status == 404 -> FestivalFlagFollowError.NotFound()
        status == 403 && code == TERMS_REQUIRED -> FestivalFlagFollowError.TermsRequired()
        status == 409 && code == FOLLOW_LIMIT -> FestivalFlagFollowError.LimitReached()
        else -> FestivalFlagFollowError.Unavailable()
    }

    else -> FestivalFlagFollowError.Unavailable()
}

/** iOS `FestivalFlagDetailRepositoryImpl.mapReport`. */
internal fun Throwable.toFestivalFlagReportError(): Throwable = when {
    this is CancellationException || this is FestivalFlagReportError -> this

    this is FestivalFlagServiceError.InvalidFlagId -> FestivalFlagReportError.InvalidRequest()

    this is AuthSessionError -> FestivalFlagReportError.LoginRequired()

    isInvalidResponse() -> FestivalFlagReportError.InvalidResponse()

    this is NetworkError.Http -> when {
        isUnauthorized -> FestivalFlagReportError.LoginRequired()
        status == 403 && code == TERMS_REQUIRED -> FestivalFlagReportError.TermsRequired()
        status == 404 -> FestivalFlagReportError.NotFound()
        status == 409 && code == ALREADY_REPORTED -> FestivalFlagReportError.AlreadyReported()
        else -> FestivalFlagReportError.Unavailable()
    }

    else -> FestivalFlagReportError.Unavailable()
}

/** iOS `FestivalFlagDetailRepositoryImpl.mapPin`. */
internal fun Throwable.toFestivalFlagPinError(): Throwable = when {
    this is CancellationException || this is FestivalFlagPinError -> this

    this is FestivalFlagServiceError.InvalidFlagId -> FestivalFlagPinError.InvalidRequest()

    this is AuthSessionError -> FestivalFlagPinError.LoginRequired()

    isInvalidResponse() -> FestivalFlagPinError.InvalidResponse()

    this is NetworkError.Http -> when {
        isUnauthorized -> FestivalFlagPinError.LoginRequired()
        status == 403 && code == TERMS_REQUIRED -> FestivalFlagPinError.TermsRequired()
        status == 409 && code == PIN_NOT_FOLLOWING -> FestivalFlagPinError.NotFollowing()
        else -> FestivalFlagPinError.Unavailable()
    }

    else -> FestivalFlagPinError.Unavailable()
}
