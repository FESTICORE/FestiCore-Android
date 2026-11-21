package io.festicore.domain.festival

/** 축제 목록 오류. 목록 화면은 종류를 구분하지 않고 모두 실패로 보인다(iOS `catalogFailed`). */
sealed class FestivalCatalogError : Exception() {
    /** 연결 실패 · 시간 초과. */
    class Offline : FestivalCatalogError()

    /** 서버가 이미 받은 커서를 다시 돌려줬다. */
    class RepeatedCursor : FestivalCatalogError()

    /** 전체 갱신이 쪽 상한 안에 끝나지 않았다. */
    class TooManyPages : FestivalCatalogError()

    /** 저장된 목록 파일을 읽지 못했다. */
    class CacheUnreadable : FestivalCatalogError()

    /** 그 외(서버 오류 · 잘못된 응답 · 캐시 쓰기 실패). */
    class Unavailable : FestivalCatalogError()
}
