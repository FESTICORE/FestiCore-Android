package io.festicore.domain.festival.receivedpack

import io.festicore.domain.festival.entity.FestivalReceivedPack

/** 마이 화면의 받은 팩 목록. 실패는 `FestivalPackError` · `FestivalCatalogError`. iOS `FestivalReceivedPackUseCaseInterface`. */
interface FestivalReceivedPackUseCase {
    /** 기기에 받은 팩(최근 확인 순). 새 버전 여부는 저장된 축제 목록으로 판단하고 네트워크에 연결하지 않는다. */
    suspend fun receivedPacks(): List<FestivalReceivedPack>

    /** 축제 목록을 서버에서 다시 받아 새 버전 여부를 갱신한다. 와이파이 자동 받기가 켜져 있고 와이파이면 새 버전을 받는다. */
    suspend fun refreshReceivedPacks(): List<FestivalReceivedPack>
}
