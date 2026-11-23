package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.sources.model.dto.FestivalChecklistDto

internal fun FestivalChecklistDto.toDomain() = FestivalChecklist(
    checkedIds = checkedIds.toSet(),
    customItems = customItems.map { FestivalChecklist.CustomItem(it.id, it.label) },
    hiddenIds = hiddenIds.toSet(),
)

internal fun FestivalChecklist.toDto() = FestivalChecklistDto(
    checkedIds = checkedIds.toList(),
    customItems = customItems.map { FestivalChecklistDto.CustomItemDto(it.id, it.label) },
    hiddenIds = hiddenIds.toList(),
)
