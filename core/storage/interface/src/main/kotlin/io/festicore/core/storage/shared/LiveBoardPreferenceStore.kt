package io.festicore.core.storage.shared

/**
 * 라이브 보드 설정. 마지막으로 고른 축제(iOS `FestivalSelectionStore`)와 팔로우 안내를 숨긴 축제(iOS `FestivalGuideVisibilityStore`).
 * 키 이름은 iOS `UserDefaults` 키와 같다.
 */
interface LiveBoardPreferenceStore {
    suspend fun selectedFestivalId(): String?

    suspend fun saveSelectedFestivalId(festivalId: String)

    suspend fun isFollowedFlagGuideHidden(festivalId: String): Boolean

    /** 빈 ID는 저장하지 않는다(iOS와 같음). */
    suspend fun hideFollowedFlagGuide(festivalId: String)
}
