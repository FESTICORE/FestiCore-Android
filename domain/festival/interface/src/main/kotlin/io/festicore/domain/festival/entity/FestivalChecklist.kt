package io.festicore.domain.festival.entity

/** 축제별 내 준비물 기록. 팩을 지워도 남는다. iOS `FestivalChecklist`. */
data class FestivalChecklist(
    val checkedIds: Set<String> = emptySet(),
    /** 내가 추가한 항목. */
    val customItems: List<CustomItem> = emptyList(),
    /** 목록에서 지운 팩 항목. 필수 항목은 지울 수 없다. */
    val hiddenIds: Set<String> = emptySet(),
) {
    data class CustomItem(
        val id: String,
        val label: String,
    )
}
