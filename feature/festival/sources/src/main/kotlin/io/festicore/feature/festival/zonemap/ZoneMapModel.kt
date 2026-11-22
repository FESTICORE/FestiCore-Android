package io.festicore.feature.festival.zonemap

import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalZoneMap
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.media.FCAvatarTint
import java.time.Duration
import java.time.Instant

/** S06 존 지도에 그릴 존별 마커 · 존 카드 · 안내 띠. 상태에서 계산만 한다. iOS `FestivalZoneMapModel`. */
internal data class ZoneMapModel(
    val markers: Map<String, List<Marker>>,
    val flagCounts: Map<String, Int>,
    val crewCounts: Map<String, Int>,
    val hasFollowedFlags: Boolean,
    val hasCrew: Boolean,
    val flagsEnabled: Boolean,
) {
    sealed interface MarkerKind {
        data object FollowedFlag : MarkerKind

        data object OtherFlag : MarkerKind

        /** 크루원 원 안의 첫 글자와 색. */
        data class Crew(
            val initial: String,
            val tint: FCAvatarTint,
        ) : MarkerKind
    }

    data class Marker(
        val id: String,
        val name: String,
        val kind: MarkerKind,
        val isStale: Boolean,
    )

    /** 존 카드(S06 B~E). */
    data class Card(
        val zoneId: String,
        val name: String,
        val meta: String,
        val flags: List<FlagRow>,
        val crew: List<CrewRow>,
    ) {
        val isEmpty: Boolean get() = flags.isEmpty() && crew.isEmpty()

        val hasStaleCrew: Boolean get() = crew.any { it.isStale }
    }

    data class FlagRow(
        val id: String,
        val name: String,
        val meta: String,
        val time: String,
        val status: FCStatusChipStatus,
    )

    data class CrewRow(
        val id: String,
        val nickname: String,
        val zone: String,
        val status: String,
        val time: String,
        val isMe: Boolean,
        val tint: FCAvatarTint,
        val isStale: Boolean,
    )

    /** 아래 안내 띠. 존 카드가 없을 때만 보인다. */
    enum class BottomBanner {
        /** S06 F */
        FollowGuide,

        /** S06 H */
        FlagsDisabled,
    }

    val bottomBanner: BottomBanner?
        get() = when {
            !flagsEnabled -> BottomBanner.FlagsDisabled
            !hasFollowedFlags && !hasCrew -> BottomBanner.FollowGuide
            else -> null
        }

    /** 존 목록(S06 I)의 오른쪽 값. 없으면 `–`. */
    fun countText(zoneId: String): String {
        val parts = listOfNotNull(flagCounts[zoneId]?.let { "깃발 $it" }, crewCounts[zoneId]?.let { "크루 $it" })
        return if (parts.isEmpty()) "–" else parts.joinToString(" · ")
    }

    companion object {
        /** 30분 넘게 소식이 없으면 흐리게 그린다(S06 E). */
        val STALE_INTERVAL: Duration = Duration.ofMinutes(30)

        val CREW_TINTS =
            listOf(FCAvatarTint.Blue, FCAvatarTint.Pink, FCAvatarTint.Orange, FCAvatarTint.Green, FCAvatarTint.Purple, FCAvatarTint.Yellow)

        fun of(
            map: FestivalZoneMap,
            snapshot: FestivalLiveBoardSnapshot?,
            now: Instant,
        ): ZoneMapModel {
            val zoneIds = map.zones.map { it.id }.toSet()
            val followedIds = snapshot?.followedFlags.orEmpty().map { it.id }.toSet()
            val flags = if (map.flagsEnabled) placedFlags(snapshot, zoneIds) else emptyList()
            val members = snapshot?.crew?.members.orEmpty().withIndex().filter { it.value.zoneId in zoneIds }
            val markers = mutableMapOf<String, MutableList<Marker>>()
            val flagCounts = mutableMapOf<String, Int>()
            val crewCounts = mutableMapOf<String, Int>()
            // 시안 순서: 팔로우 깃발 → 크루 → 다른 깃발
            for (flag in flags.filter { it.id in followedIds }) {
                markers.getOrPut(flag.zoneId.orEmpty()) { mutableListOf() } +=
                    Marker(flag.id, flag.name, MarkerKind.FollowedFlag, isStale(flag.updatedAt, now))
            }
            for ((index, member) in members) {
                val zoneId = member.zoneId.orEmpty()
                markers.getOrPut(zoneId) { mutableListOf() } +=
                    Marker(
                        id = member.id,
                        name = member.nickname,
                        kind = MarkerKind.Crew(initial(member.nickname), CREW_TINTS[index % CREW_TINTS.size]),
                        isStale = isStale(member.updatedAt, now),
                    )
                crewCounts[zoneId] = (crewCounts[zoneId] ?: 0) + 1
            }
            for (flag in flags) {
                val zoneId = flag.zoneId.orEmpty()
                flagCounts[zoneId] = (flagCounts[zoneId] ?: 0) + 1
                if (flag.id in followedIds) continue
                markers.getOrPut(zoneId) { mutableListOf() } +=
                    Marker(flag.id, flag.name, MarkerKind.OtherFlag, isStale(flag.updatedAt, now))
            }
            return ZoneMapModel(
                markers = markers,
                flagCounts = flagCounts,
                crewCounts = crewCounts,
                hasFollowedFlags = flags.any { it.id in followedIds },
                hasCrew = members.isNotEmpty(),
                flagsEnabled = map.flagsEnabled,
            )
        }

        fun card(
            zone: FestivalZoneMap.Zone,
            map: FestivalZoneMap,
            snapshot: FestivalLiveBoardSnapshot?,
            viewerUserId: String?,
            now: Instant,
        ): Card {
            val followedIds = snapshot?.followedFlags.orEmpty().map { it.id }.toSet()
            val flags = if (map.flagsEnabled) placedFlags(snapshot, setOf(zone.id)) else emptyList()
            val ordered = flags.filter { it.id in followedIds } + flags.filterNot { it.id in followedIds }
            val flagRows =
                ordered.map { flag ->
                    val activity = flag.activity?.let(LiveBoardPresentation::tagText)
                    val followed = if (flag.id in followedIds) "팔로우 중" else null
                    FlagRow(
                        id = flag.id,
                        name = flag.name,
                        meta = listOfNotNull(followed, activity).joinToString(" · "),
                        time = LiveBoardPresentation.relativeTime(flag.updatedAt, now).orEmpty(),
                        status = if (flag.status == "resting") FCStatusChipStatus.Resting else FCStatusChipStatus.Active,
                    )
                }
            val crewRows =
                snapshot?.crew?.members.orEmpty().withIndex().mapNotNull { (index, member) ->
                    if (member.zoneId != zone.id) return@mapNotNull null
                    CrewRow(
                        id = member.id,
                        nickname = member.nickname,
                        zone = zone.name,
                        status = member.state?.let(LiveBoardPresentation::crewStateText).orEmpty(),
                        time = LiveBoardPresentation.relativeTime(member.updatedAt, now).orEmpty(),
                        isMe = viewerUserId != null && member.id == viewerUserId,
                        tint = CREW_TINTS[index % CREW_TINTS.size],
                        isStale = isStale(member.updatedAt, now),
                    )
                }
            val counts =
                listOfNotNull(
                    flagRows.takeIf { it.isNotEmpty() }?.let { "깃발 ${it.size}" },
                    crewRows.takeIf { it.isNotEmpty() }?.let { "크루 ${it.size}" },
                )
            val presence = counts.ifEmpty { listOf("지금 아무도 없어요") }
            return Card(
                zoneId = zone.id,
                name = zone.name,
                meta = (listOfNotNull(zone.kind) + presence).joinToString(" · "),
                flags = flagRows,
                crew = crewRows,
            )
        }

        private fun placedFlags(snapshot: FestivalLiveBoardSnapshot?, zoneIds: Set<String>): List<FestivalLiveBoardSnapshot.Flag> =
            snapshot?.activeFlags.orEmpty().filter { it.status in PLACED_STATUSES && it.zoneId in zoneIds }

        private fun isStale(date: Instant?, now: Instant): Boolean = date != null && Duration.between(date, now) > STALE_INTERVAL

        /** 닉네임 첫 글자. iOS `prefix(1)`은 문자소 하나이고 여기는 코드 포인트 하나다(서로게이트 쌍은 자르지 않는다). */
        private fun initial(nickname: String): String =
            if (nickname.isEmpty()) "" else nickname.substring(0, nickname.offsetByCodePoints(0, 1))

        private val PLACED_STATUSES = setOf("active", "resting")
    }
}
