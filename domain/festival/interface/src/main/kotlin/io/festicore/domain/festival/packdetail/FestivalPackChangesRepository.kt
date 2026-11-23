package io.festicore.domain.festival.packdetail

import io.festicore.domain.festival.entity.FestivalPackChanges

/** 팩 변경 요약 서버 API. 실패는 `NetworkError`를 감싼 `FestivalPackError`. iOS `FestivalPackRepositoryInterface`. */
interface FestivalPackChangesRepository {
    suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges
}
