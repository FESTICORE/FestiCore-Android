package io.festicore.feature.festival.flag

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** 깃발 카드 한 줄. iOS `FestivalFlagDirectory.Row`. */
data class FlagRow(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    /** null이면 상태를 알 수 없다(라이브 목록 상한). */
    val status: FlagStatus?,
    val followersText: String,
    val tags: List<String>,
    val zone: String,
    val isFollowing: Boolean,
    /** 팔로우를 누를 때 넘길 스냅샷의 팔로워 수. */
    val serverFollowerCount: Int?,
    val isFollowEnabled: Boolean,
)

/** 깃발 목록 표시 문구(iOS `FestivalFlagDirectory` 표시 행 · `offlineText`). */
internal object FlagPresentation {
    val FlagTag.title: String
        get() =
            when (this) {
                FlagTag.Slam -> "슬램"
                FlagTag.Sing -> "떼창"
                FlagTag.Train -> "기차놀이"
                FlagTag.Calm -> "차분"
                FlagTag.Newbie -> "초보 환영"
            }

    val FlagSort.title: String
        get() =
            when (this) {
                FlagSort.Active -> "활동 중 순"
                FlagSort.Followers -> "팔로워 많은 순"
                FlagSort.Recent -> "최근 업데이트 순"
            }

    /** "팔로워 1,284"(ko_KR 천단위). */
    fun followersText(count: Int): String = "팔로워 ${NumberFormat.getIntegerInstance(Locale.KOREA).format(count)}"

    /**
     * 필터 · 정렬을 적용한 카드 목록. 성공한 팔로우 값은 서버 확인 전까지 스냅샷보다 우선한다.
     * [isFailed]면 이전 목록을 보이는 중이므로 팔로우를 막는다.
     */
    fun rows(
        directory: FlagDirectoryState,
        snapshot: FestivalLiveBoardSnapshot,
        isFailed: Boolean,
    ): List<FlagRow> {
        val followedIds = snapshot.followedFlags.map { it.id }.toSet()
        return FlagDirectory.filteredSorted(directory, snapshot).map { flag ->
            val override = directory.followOverrides[flag.id]
            val zoneId = flag.zoneId ?: flag.homeZoneId
            FlagRow(
                id = flag.id,
                name = flag.name,
                thumbnailUrl = flag.thumbnailUrl,
                status = FlagDirectory.status(flag.status),
                followersText = followersText(FlagDirectory.displayCount(directory, flag)),
                tags = flag.tags.mapNotNull { FlagTag.of(it)?.title },
                zone = zoneId?.let { directory.zoneNames[it] } ?: "위치 미정",
                isFollowing = override?.isFollowing ?: followedIds.contains(flag.id),
                serverFollowerCount = flag.followerCount,
                isFollowEnabled = !isFailed && !directory.isPending(flag.id),
            )
        }
    }

    /** 마지막 확인 시각으로 오프라인 배지 문구를 만든다. 1분 미만 · 미래 · 시각 없음은 "방금 전". */
    fun offlineText(receivedAt: Instant?, now: Instant): String {
        val minutes = receivedAt?.let { Duration.between(it, now).seconds / 60 } ?: 0
        return if (minutes >= 1) "오프라인 · ${minutes}분 전 정보" else "오프라인 · 방금 전 정보"
    }
}
