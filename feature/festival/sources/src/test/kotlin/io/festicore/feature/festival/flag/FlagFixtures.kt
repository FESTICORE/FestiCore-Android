package io.festicore.feature.festival.flag

import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import java.time.Instant

/** iOS `FestivalFlagDirectoryTests`의 `flag` · `board` · `loadedState`. */
internal object FlagFixtures {
    fun flag(
        id: String,
        status: String = "active",
        tags: List<String> = emptyList(),
        count: Int? = null,
        zone: String? = null,
        home: String? = null,
        updatedAt: Instant? = null,
    ) = FestivalLiveBoardSnapshot.Flag(
        id = id,
        name = id,
        status = status,
        zoneId = zone,
        activity = null,
        thumbnailUrl = null,
        tags = tags,
        updatedAt = updatedAt,
        followerCount = count,
        homeZoneId = home,
    )

    fun board(
        flags: List<FestivalLiveBoardSnapshot.Flag>,
        followed: List<String> = emptyList(),
        festivalId: String = "festival",
    ) = FestivalLiveBoardSnapshot(
        festivalId = festivalId,
        activeFlags = emptyList(),
        followedFlags = followed.map { flag(it) },
        crew = null,
        directoryFlags = flags,
    )

    fun loadedState(snapshot: FestivalLiveBoardSnapshot) =
        FlagState(festivalId = "festival", snapshot = snapshot, viewState = FlagViewState.Loaded, isVisible = true)
}
