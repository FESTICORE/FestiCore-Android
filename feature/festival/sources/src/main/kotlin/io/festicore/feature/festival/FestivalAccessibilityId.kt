package io.festicore.feature.festival

/** 축제 화면의 UI 테스트 · 접근성 식별자(iOS `FestivalAccessibilityID`). Compose `testTag`로 쓴다. */
internal object FestivalAccessibilityId {
    const val LIST_SKELETON = "festivalListSkeleton"
    const val SEARCH_SKELETON = "festivalSearchSkeleton"

    fun card(festivalId: String): String = "festivalCard_$festivalId"
}
