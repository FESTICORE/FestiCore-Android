package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalFlagDetail
import io.festicore.domain.festival.FestivalFlagDetailRepository
import io.festicore.domain.festival.FestivalFlagReportReason
import io.festicore.service.festivalcatalog.FestivalCatalogService
import io.festicore.service.festivalcatalog.FestivalFlagDetailService

/** iOS `FestivalFlagDetailRepositoryImpl`. 이미지 주소는 카탈로그 서비스로 결합한다. */
internal class FestivalFlagDetailRepositoryImpl(
    private val service: FestivalFlagDetailService,
    private val catalogService: FestivalCatalogService,
) : FestivalFlagDetailRepository {
    override suspend fun detail(flagId: String): FestivalFlagDetail = try {
        service.detail(flagId).toDomain(catalogService::imageUrl)
    } catch (e: Exception) {
        throw e.toFestivalFlagDetailError()
    }

    override suspend fun report(flagId: String, reason: FestivalFlagReportReason) = try {
        service.report(flagId, reason.value)
    } catch (e: Exception) {
        throw e.toFestivalFlagReportError()
    }

    override suspend fun setPinnedFlag(flagId: String?): String? = try {
        service.setPinnedFlag(flagId)
    } catch (e: Exception) {
        throw e.toFestivalFlagPinError()
    }
}
