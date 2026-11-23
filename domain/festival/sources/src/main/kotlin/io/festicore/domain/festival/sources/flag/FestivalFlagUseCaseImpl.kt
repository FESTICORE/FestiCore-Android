package io.festicore.domain.festival.sources.flag

import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.entity.FestivalFlagReportReason
import io.festicore.domain.festival.flagdetail.FestivalFlagDetailRepository
import io.festicore.domain.festival.flagdetail.FestivalFlagDetailUseCase
import io.festicore.domain.festival.flagdetail.FestivalFlagRepository
import io.festicore.domain.festival.flagdetail.FestivalFlagUseCase
/** iOS `FestivalFlagDetailUseCaseImpl`. 저장소 결과를 그대로 돌려준다. */
internal class FestivalFlagDetailUseCaseImpl(private val repository: FestivalFlagDetailRepository) : FestivalFlagDetailUseCase {
    override suspend fun detail(flagId: String): FestivalFlagDetail = repository.detail(flagId)

    override suspend fun report(flagId: String, reason: FestivalFlagReportReason) = repository.report(flagId, reason)

    override suspend fun setPinnedFlag(flagId: String?): String? = repository.setPinnedFlag(flagId)
}

/** iOS `FestivalFlagUseCaseImpl.setFollowing`. */
internal class FestivalFlagUseCaseImpl(private val repository: FestivalFlagRepository) : FestivalFlagUseCase {
    override suspend fun setFollowing(following: Boolean, flagId: String): FestivalFlagFollowResult =
        repository.setFollowing(following, flagId)
}
