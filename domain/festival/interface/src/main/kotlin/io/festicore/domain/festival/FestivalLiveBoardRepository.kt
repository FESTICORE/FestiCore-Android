package io.festicore.domain.festival

/** 라이브 보드 조회 저장소. 실패는 [FestivalLiveBoardError]. iOS `FestivalLiveBoardService` · `FestivalOfficialNewsService` · `festivalDetail`. */
interface FestivalLiveBoardRepository {
    /** 라이브 상태 + 공개 깃발 프로필을 받아 합친다. */
    suspend fun liveBoard(festivalId: String): FestivalLiveBoardSnapshot

    suspend fun officialNews(festivalId: String): FestivalOfficialNews

    /** 축제 상세(온라인 팩 문서). 저장하지 않는다. */
    suspend fun festivalDetail(festivalId: String): FestivalPackDocument
}
