package io.festicore.domain.bearer.entity

/** 깃발 축제 이동 한 건. 응답을 받지 못해 다시 보낼 때는 같은 값(같은 멱등키)을 쓴다. iOS에 아직 없다(백엔드 #409). */
data class BearerFestivalTransferRequest(
    val flagId: String,
    val fromFestivalId: String,
    val toFestivalId: String,
    val idempotencyKey: String,
)

/** 옮긴 결과. flagId · 이름 · 사진 · 팔로워는 그대로이고 계획 · 주로 있는 존 · 현장 상태는 서버가 비운다. */
data class BearerFestivalTransfer(
    val flagId: String,
    val previousFestivalId: String,
    val festivalId: String,
    val profile: BearerFlagProfile,
)
