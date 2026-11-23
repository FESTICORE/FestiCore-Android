package io.festicore.service.festivalcatalog

/** 깃발 상세 · 팔로우 · 신고 · 대표 깃발 서비스가 만드는 오류. 나머지는 [io.festicore.core.network.NetworkError] · [io.festicore.service.api.AuthSessionError] 그대로. */
sealed class FestivalFlagServiceError : Exception() {
    /** 깃발 ID가 `[A-Za-z0-9_-]` 1~64자가 아니다. 요청을 보내지 않았다. */
    class InvalidFlagId : FestivalFlagServiceError()

    /** 성공 응답이 요청과 맞지 않는다(다른 깃발 ID · 빈 신고 ID · 요청과 다른 대표 깃발). */
    class InvalidResponse : FestivalFlagServiceError()
}

/** 경로에 그대로 넣을 수 있는 깃발 ID(`[A-Za-z0-9_-]`, 1~64자). iOS `isValidID`. */
internal fun String.isValidFlagId(): Boolean =
    length in 1..MAX_FLAG_ID_LENGTH && all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

private const val MAX_FLAG_ID_LENGTH = 64
