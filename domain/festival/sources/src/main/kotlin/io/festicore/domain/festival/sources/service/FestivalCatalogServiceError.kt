package io.festicore.domain.festival.sources.service

/** 카탈로그 서비스 오류. 네트워크 실패는 [io.festicore.core.network.NetworkError]로 그대로 던진다. iOS `FestivalCatalogError`. */
sealed class FestivalCatalogServiceError(cause: Throwable? = null) : Exception(cause) {
    /** 서버가 이미 받은 커서를 다시 돌려줬다. */
    class RepeatedCursor : FestivalCatalogServiceError()

    /** 전체 갱신이 [FestivalCatalogService.MAX_PAGES]쪽 안에 끝나지 않았다. */
    class TooManyPages : FestivalCatalogServiceError()

    /** 캐시 파일을 읽지 못했거나 해석하지 못했다. */
    class CacheUnreadable(cause: Throwable) : FestivalCatalogServiceError(cause)

    /** 캐시 파일을 쓰지 못했다. */
    class CacheWriteFailed(cause: Throwable) : FestivalCatalogServiceError(cause)
}
