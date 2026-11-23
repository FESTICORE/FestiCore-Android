package io.festicore.domain.festival.flagdetail

import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.domain.festival.entity.FestivalFlagDetailError
import io.festicore.domain.festival.entity.FestivalFlagFollowError
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.entity.FestivalFlagPinError
import io.festicore.domain.festival.entity.FestivalFlagReportError
import io.festicore.domain.festival.entity.FestivalFlagReportReason

/** 깃발 상세 · 신고 · 대표 깃발 저장소. iOS `FestivalFlagDetailRepositoryInterface`. */
interface FestivalFlagDetailRepository {
    /** 실패는 [FestivalFlagDetailError]. */
    suspend fun detail(flagId: String): FestivalFlagDetail

    /** 실패는 [FestivalFlagReportError]. */
    suspend fun report(flagId: String, reason: FestivalFlagReportReason)

    /** 대표 깃발을 [flagId]로 바꾸고(null이면 해제) 서버가 저장한 값을 돌려준다. 실패는 [FestivalFlagPinError]. */
    suspend fun setPinnedFlag(flagId: String?): String?
}

/** 깃발 팔로우 저장소. 실패는 [FestivalFlagFollowError]. iOS `FestivalFlagFollowService`. */
interface FestivalFlagRepository {
    suspend fun setFollowing(following: Boolean, flagId: String): FestivalFlagFollowResult
}
