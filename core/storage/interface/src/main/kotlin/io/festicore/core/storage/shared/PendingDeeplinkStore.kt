package io.festicore.core.storage.shared

/**
 * 아직 열지 않은 딥링크(iOS `PendingDeeplinkStore` · `PendingDeeplinkSharedKey`). 최신 하나만 URL 문자열로 영속 저장한다.
 * 해석은 부르는 쪽(`FestiCoreDeeplinkParser`)이 한다. 키 이름은 iOS와 같다.
 */
interface PendingDeeplinkStore {
    /** 이전 대기 값을 덮어쓴다. */
    suspend fun save(encoded: String)

    /** 대기 값을 꺼내고 비운다. 없으면 `null`. */
    suspend fun consume(): String?
}
