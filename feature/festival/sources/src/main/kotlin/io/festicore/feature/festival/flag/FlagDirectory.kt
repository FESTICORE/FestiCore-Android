package io.festicore.feature.festival.flag

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
/** 깃발 성향 태그. [rawValue]는 서버 값이다. iOS `FestivalFlagDirectory.Tag`. */
enum class FlagTag(val rawValue: String) {
    Slam("slam"),
    Sing("sing"),
    Train("train"),
    Calm("calm"),
    Newbie("newbie"),
    ;

    companion object {
        fun of(rawValue: String): FlagTag? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/** 정렬. "새로 생긴 순"은 서버에 생성 시각이 없어 두지 않는다. iOS `FestivalFlagDirectory.Sort`. */
enum class FlagSort { Active, Followers, Recent }

/** 카드 상태. iOS `FestivalFlagDirectory.Status`. */
enum class FlagStatus { Active, Resting, Scheduled, EndedToday }

/**
 * 팔로우 성공 응답. 팔로우 여부와 팔로워 수는 따로 서버 값으로 돌아간다. iOS `FestivalFlagDirectory.FollowOverride`.
 * [baseCount]는 요청 직전 표시하던 팔로워 수다. 같은 값이 다시 오면 공개 캐시가 아직 바뀌지 않은 것이다.
 * [skipsNextLoad]: 성공 시점에 진행 중이던 조회는 팔로우 전 상태일 수 있어 한 번 건너뛴다.
 */
data class FlagFollowOverride(
    val isFollowing: Boolean?,
    val followerCount: Int?,
    val baseCount: Int?,
    val skipsNextLoad: Boolean = false,
)

/**
 * S04 깃발 디렉토리의 필터 · 정렬 · 팔로우 상태. iOS `FestivalFlagDirectory.State`.
 * [pendingFollows]는 요청 중인 깃발 → 요청 직전 팔로워 수(null 가능)다. 키가 있으면 요청 중이다.
 */
data class FlagDirectoryState(
    val tag: FlagTag? = null,
    val sort: FlagSort = FlagSort.Active,
    val isSortMenuPresented: Boolean = false,
    val zoneNames: Map<String, String> = emptyMap(),
    val toast: String? = null,
    val pendingFollows: Map<String, Int?> = emptyMap(),
    val followOverrides: Map<String, FlagFollowOverride> = emptyMap(),
) {
    fun isPending(id: String): Boolean = pendingFollows.containsKey(id)
}

/** 디렉토리의 정렬 · 필터 · reconcile 순수 함수(iOS `FestivalFlagDirectory` 확장). */
internal object FlagDirectory {
    /** 표시할 팔로워 수. 성공한 팔로우 값이 스냅샷보다 우선한다. */
    fun displayCount(state: FlagDirectoryState, flag: FestivalLiveBoardSnapshot.Flag): Int =
        state.followOverrides[flag.id]?.followerCount ?: flag.followerCount ?: 0

    fun status(value: String): FlagStatus? = when (value) {
        "active" -> FlagStatus.Active
        "resting" -> FlagStatus.Resting
        "scheduled" -> FlagStatus.Scheduled
        "ended" -> FlagStatus.EndedToday
        else -> null
    }

    /** 활동 중 순 순위: `active < resting < 모름 < scheduled < endedToday`. */
    fun rank(status: FlagStatus?): Int = when (status) {
        FlagStatus.Active -> 0
        FlagStatus.Resting -> 1
        null -> 2
        FlagStatus.Scheduled -> 3
        FlagStatus.EndedToday -> 4
    }

    /** 태그로 거르고 [FlagDirectoryState.sort]로 정렬한다. 같으면 서버 순서를 지킨다(안정 정렬). */
    fun filteredSorted(state: FlagDirectoryState, snapshot: FestivalLiveBoardSnapshot): List<FestivalLiveBoardSnapshot.Flag> {
        val filtered = snapshot.directoryFlags.filter { flag -> state.tag?.let { flag.tags.contains(it.rawValue) } ?: true }
        val byCount = compareByDescending<FestivalLiveBoardSnapshot.Flag> { displayCount(state, it) }
        val comparator =
            when (state.sort) {
                FlagSort.Active -> compareBy<FestivalLiveBoardSnapshot.Flag> { rank(status(it.status)) }.then(byCount)

                FlagSort.Followers -> byCount

                // 시각이 없는 깃발은 뒤로 간다.
                FlagSort.Recent -> compareBy<FestivalLiveBoardSnapshot.Flag> { it.updatedAt == null }.thenByDescending { it.updatedAt }
            }
        return filtered.sortedWith(comparator)
    }

    /** 부모가 받아들인 새 스냅샷으로 덮어쓰기 값을 서버 값에 넘긴다(iOS `reconcile(with:)`). */
    fun reconcile(state: FlagDirectoryState, snapshot: FestivalLiveBoardSnapshot): FlagDirectoryState {
        val followedIds = snapshot.followedFlags.map { it.id }.toSet()
        val serverCounts = snapshot.directoryFlags.reversed().associate { it.id to it.followerCount }
        val overrides =
            state.followOverrides.mapNotNull { (id, override) ->
                val next =
                    if (override.skipsNextLoad) {
                        override.copy(skipsNextLoad = false)
                    } else {
                        // 304 재사용 응답은 팔로우 전 상태일 수 있으므로 일치할 때만 서버 값에 넘긴다.
                        val isFollowing = if (override.isFollowing == followedIds.contains(id)) null else override.isFollowing
                        // 공개 목록은 최대 10분 캐시된다. 팔로우 전과 같은 수면 아직 바뀌지 않은 응답이다.
                        val count = if (serverCounts[id] != override.baseCount) null else override.followerCount
                        override.copy(isFollowing = isFollowing, followerCount = count)
                    }
                if (next.isFollowing == null && next.followerCount == null) null else id to next
            }.toMap()
        return state.copy(followOverrides = overrides)
    }

    /** 새 조회가 이전 조회를 취소하면 건너뛸 이유가 사라진다. */
    fun clearLoadSkips(state: FlagDirectoryState): FlagDirectoryState =
        state.copy(followOverrides = state.followOverrides.mapValues { (_, override) -> override.copy(skipsNextLoad = false) })

    /** 탭을 떠나면 진행 중인 요청과 표시만 비우고 성공값은 다음 확인까지 남긴다. */
    fun leaveTab(state: FlagDirectoryState): FlagDirectoryState =
        clearLoadSkips(state).copy(pendingFollows = emptyMap(), toast = null, isSortMenuPresented = false)

    /** 축제 · 계정이 바뀌면 이전 문맥의 팔로우 값을 모두 버린다. */
    fun resetContext(state: FlagDirectoryState): FlagDirectoryState = leaveTab(state).copy(followOverrides = emptyMap())
}
