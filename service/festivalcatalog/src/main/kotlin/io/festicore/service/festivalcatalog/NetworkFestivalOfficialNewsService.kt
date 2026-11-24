package io.festicore.service.festivalcatalog

import io.festicore.core.network.error.NetworkError
import io.festicore.domain.festival.sources.model.dto.FestivalOfficialNewsDto
import io.festicore.domain.festival.sources.service.FestivalLiveBoardServiceError
import io.festicore.domain.festival.sources.service.FestivalOfficialNewsService
import io.festicore.domain.festival.sources.service.isValidFestivalId
import io.festicore.service.apiendpoint.festival.FestivalRequest

/**
 * 축제 공식 인스타그램 최근 게시물(서버가 5분마다 받아 둔 것). 인증 없음. iOS `NetworkFestivalOfficialNewsService`.
 * 해석 실패 · 다른 축제 응답은 [FestivalLiveBoardServiceError.InvalidResponse], 그 밖은 [NetworkError].
 */
class NetworkFestivalOfficialNewsService(private val api: FestivalRequest) : FestivalOfficialNewsService {
    override suspend fun officialNews(festivalId: String): FestivalOfficialNewsDto {
        if (!festivalId.isValidFestivalId()) throw FestivalLiveBoardServiceError.InvalidFestivalId()
        val news = try {
            api.instagram<FestivalOfficialNewsDto>(festivalId)
        } catch (_: NetworkError.Decoding) {
            throw FestivalLiveBoardServiceError.InvalidResponse()
        } catch (_: NetworkError.InvalidResponse) {
            throw FestivalLiveBoardServiceError.InvalidResponse()
        }
        if (news.festivalId != festivalId) throw FestivalLiveBoardServiceError.InvalidResponse()
        return news
    }
}
