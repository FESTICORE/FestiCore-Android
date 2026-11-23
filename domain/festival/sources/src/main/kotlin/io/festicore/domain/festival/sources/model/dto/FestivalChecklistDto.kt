package io.festicore.domain.festival.sources.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 기기에 저장하는 준비물 기록. 키는 iOS `FestivalChecklist` Codable과 같고, 세 키가 모두 있어야 읽는다(iOS와 같음). */
@Serializable
data class FestivalChecklistDto(
    @SerialName("checkedIDs") val checkedIds: List<String>,
    val customItems: List<CustomItemDto>,
    @SerialName("hiddenIDs") val hiddenIds: List<String>,
) {
    @Serializable
    data class CustomItemDto(
        val id: String,
        val label: String,
    )
}
