package io.festicore.domain.festival

/** 깃발 상세 화면의 조회 · 신고 · 대표 깃발. iOS `FestivalFlagDetailUseCaseInterface`. 대표 깃발은 라이브 액티비티 단계 전까지 화면에서 부르지 않는다. */
interface FestivalFlagDetailUseCase {
    /** 실패는 [FestivalFlagDetailError]. */
    suspend fun detail(flagId: String): FestivalFlagDetail

    /** 실패는 [FestivalFlagReportError]. */
    suspend fun report(flagId: String, reason: FestivalFlagReportReason)

    /** 실패는 [FestivalFlagPinError]. */
    suspend fun setPinnedFlag(flagId: String?): String?
}

/** 깃발 팔로우 · 언팔로우. 실패는 [FestivalFlagFollowError]. iOS `FestivalFlagUseCaseInterface.setFollowing`(라이브 보드는 [FestivalLiveBoardUseCase]). */
interface FestivalFlagUseCase {
    suspend fun setFollowing(following: Boolean, flagId: String): FestivalFlagFollowResult
}
