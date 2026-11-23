package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalFlagDetail
import io.festicore.domain.festival.FestivalFlagDetailRepository
import io.festicore.domain.festival.FestivalFlagReportReason
import io.festicore.domain.festival.sources.model.dto.FlagDetailDto
import io.festicore.domain.festival.sources.model.dto.FlagReportDto
import io.festicore.domain.festival.sources.model.dto.PinnedFlagDto
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.domain.festival.sources.model.mapper.toFestivalFlagDetailError
import io.festicore.domain.festival.sources.model.mapper.toFestivalFlagPinError
import io.festicore.domain.festival.sources.model.mapper.toFestivalFlagReportError
import io.festicore.domain.festival.sources.service.FestivalCatalogService
import io.festicore.domain.festival.sources.service.FestivalFlagServiceError
import io.festicore.domain.festival.sources.service.isValidFlagId
import io.festicore.service.apiendpoint.flag.FlagReportBody
import io.festicore.service.apiendpoint.flag.FlagRequest
import io.festicore.service.apiendpoint.flag.PinnedFlagBody

/**
 * iOS `FestivalFlagDetailRepositoryImpl`. 이미지 주소는 카탈로그 서비스로 결합한다.
 * 잘못된 ID는 [FestivalFlagServiceError.InvalidFlagId](요청하지 않음), 요청과 다른 응답은 [FestivalFlagServiceError.InvalidResponse].
 */
internal class FestivalFlagDetailRepositoryImpl(
    private val api: FlagRequest,
    private val catalogService: FestivalCatalogService,
    private val currentSessionId: suspend () -> String? = { null },
) : FestivalFlagDetailRepository {
    /** 로그인 중이면 그 세션의 Bearer를 붙인다. 익명이면 `isFollowing` · `isBearer` · `isPinned`가 null이다. */
    override suspend fun detail(flagId: String): FestivalFlagDetail = try {
        if (!flagId.isValidFlagId()) throw FestivalFlagServiceError.InvalidFlagId()
        val detail = api.detail<FlagDetailDto>(flagId, currentSessionId())
        if (detail.id != flagId) throw FestivalFlagServiceError.InvalidResponse()
        detail.toDomain(catalogService::imageUrl)
    } catch (e: Exception) {
        throw e.toFestivalFlagDetailError()
    }

    override suspend fun report(flagId: String, reason: FestivalFlagReportReason) {
        try {
            if (!flagId.isValidFlagId()) throw FestivalFlagServiceError.InvalidFlagId()
            val result = api.report<FlagReportDto>(flagId, FlagReportBody(reason.value), currentSessionId())
            if (result.reportId.isEmpty()) throw FestivalFlagServiceError.InvalidResponse()
        } catch (e: Exception) {
            throw e.toFestivalFlagReportError()
        }
    }

    /** 대표 깃발을 [flagId]로 바꾸고(null이면 해제) 서버가 저장한 값을 돌려준다. */
    override suspend fun setPinnedFlag(flagId: String?): String? = try {
        if (flagId != null && !flagId.isValidFlagId()) throw FestivalFlagServiceError.InvalidFlagId()
        val result = api.setPinnedFlag<PinnedFlagDto>(PinnedFlagBody(flagId), currentSessionId())
        if (result.pinnedFlagId != flagId) throw FestivalFlagServiceError.InvalidResponse()
        result.pinnedFlagId
    } catch (e: Exception) {
        throw e.toFestivalFlagPinError()
    }
}
