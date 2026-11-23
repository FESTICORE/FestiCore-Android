package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.sources.model.dto.FestivalPackChangesDto

internal fun FestivalPackChangesDto.toDomain() = FestivalPackChanges(
    fromVersion = fromVersion,
    toVersion = toVersion,
    sizeBytes = sizeBytes,
    changedSets = changedSets,
    lastShuttleAt = lastShuttleAt?.let { FestivalPackChanges.ValueChange(it.before, it.after) },
    lastTrainAt = lastTrainAt?.let { FestivalPackChanges.ValueChange(it.before, it.after) },
)
