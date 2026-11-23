package io.festicore.domain.festival.packdetail

import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail

/** 팩 상세 · 업데이트 · 삭제 · 준비물 · 자동 받기 설정. 팩 실패는 `FestivalPackError`. iOS `FestivalPackDetailUseCaseInterface`. */
interface FestivalPackDetailUseCase {
    /** 기기에 받은 팩. 없으면 null. 네트워크에 연결하지 않는다. 상세 키를 읽지 못하면 빈 상세. */
    suspend fun packDetail(festivalId: String): FestivalPackDetail?

    /** 받은 버전과 현재 발행 팩의 차이. */
    suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges

    /** 서버와 버전을 맞추고 목록도 새로 받아 바뀐 상세를 돌려준다. 재시도 지연 · 재연결은 `RetryLater`, 전송 실패는 `Offline`. */
    suspend fun updatePack(festivalId: String): FestivalPackDetail?

    /** 기기에서 팩을 지운다. 내 준비물은 남긴다. */
    suspend fun deletePack(festivalId: String)

    suspend fun checklist(festivalId: String): FestivalChecklist

    suspend fun saveChecklist(checklist: FestivalChecklist, festivalId: String)

    /** 와이파이 자동 받기 설정. 처음에는 켜져 있다. */
    suspend fun autoDownloadOnWiFi(): Boolean

    suspend fun setAutoDownloadOnWiFi(isOn: Boolean)
}
