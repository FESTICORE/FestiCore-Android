package io.festicore.feature.festival.accessibility

/** 축제 화면의 UI 테스트 · 접근성 식별자(iOS `FestivalAccessibilityID`). Compose `testTag`로 쓴다. */
internal object FestivalAccessibilityId {
    const val LIST_SKELETON = "festivalListSkeleton"
    const val SEARCH_SKELETON = "festivalSearchSkeleton"
    const val LIVE_BOARD_SKELETON = "festivalLiveBoardSkeleton"
    const val FLAG_DETAIL_HEADER = "flagDetailHeader"
    const val FLAG_DETAIL_HERO = "flagDetailHero"
    const val FLAG_DETAIL_SCROLL = "flagDetailScroll"
    const val FLAG_DETAIL_SKELETON = "festivalFlagDetailSkeleton"

    fun card(festivalId: String): String = "festivalCard_$festivalId"
}
