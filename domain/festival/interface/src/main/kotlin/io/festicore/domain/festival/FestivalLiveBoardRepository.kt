package io.festicore.domain.festival

/** 라이브 보드 조회 저장소. 실패는 [FestivalLiveBoardError]. iOS `FestivalLiveBoardService` · `FestivalOfficialNewsService` · `festivalDetail`. */
interface FestivalLiveBoardRepository {
    /** 라이브 상태 + 공개 깃발 프로필을 받아 합친다. */
    suspend fun liveBoard(festivalId: String): FestivalLiveBoardSnapshot

    /** 현재 세션에서 마지막으로 [liveBoard]가 받은 결과를 같은 매핑으로 돌려준다. 없거나 세션이 바뀌었으면 null. 네트워크를 쓰지 않는다. */
    suspend fun cachedLiveBoard(festivalId: String): FestivalLiveBoardSnapshot?

    suspend fun officialNews(festivalId: String): FestivalOfficialNews

    /** 축제 상세(온라인 팩 문서). 저장하지 않는다. */
    suspend fun festivalDetail(festivalId: String): FestivalPackDocument
}
