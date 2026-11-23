package io.festicore.domain.festival.sources.model.dto

import kotlinx.serialization.Serializable

/** `GET festivals/{id}/pack/changes`의 `data`. `lastDepartures`는 iOS도 읽지 않는다. iOS `FestivalPackChangesDataDTO`. */
@Serializable
data class FestivalPackChangesDto(
    val fromVersion: Int,
    val toVersion: Int,
    val sizeBytes: Int,
    val changedSets: Int? = null,
    val lastShuttleAt: ValueChangeDto? = null,
    val lastTrainAt: ValueChangeDto? = null,
) {
    @Serializable
    data class ValueChangeDto(
        val before: String? = null,
        val after: String? = null,
    )
}
