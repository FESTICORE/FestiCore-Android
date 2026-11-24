package io.festicore.service.analytics.event

/** iOS `FlagFollowData`. [isFollowing]은 팔로우하면 true, 해제하면 false. */
data class FlagFollowData(
    val flagId: String,
    val isFollowing: Boolean,
)
