package io.festicore.core.storage.shared

/**
 * 받은 팩의 기기 설정. 축제별 준비물 기록(JSON 문자열, 해석은 domain이 한다)과 와이파이 자동 받기.
 * 팩을 지워도 남는다. 키 이름은 iOS `FestivalPackLocalStore`의 `UserDefaults` 키와 같다.
 */
interface FestivalPackLocalStore {
    /** 저장한 준비물 JSON. 없으면 null. */
    suspend fun checklistJson(festivalId: String): String?

    suspend fun saveChecklistJson(festivalId: String, json: String)

    /** 처음에는 켜져 있다(iOS와 같음). */
    suspend fun autoDownloadOnWiFi(): Boolean

    suspend fun setAutoDownloadOnWiFi(isOn: Boolean)
}
