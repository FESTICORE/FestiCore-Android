package io.festicore.domain.festival.sources.service

/** 라이브 상태 조회 + 공개 깃발 프로필 조회. iOS `FestivalLiveBoardService`. */
interface FestivalLiveBoardService {
    suspend fun refresh(festivalId: String): FestivalLiveBoardFetch

    /** 마지막으로 성공한 조회 결과(현재 세션). 없으면 null. */
    suspend fun cachedFetch(festivalId: String): FestivalLiveBoardFetch?
}
