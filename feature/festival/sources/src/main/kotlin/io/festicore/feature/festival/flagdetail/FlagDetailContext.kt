package io.festicore.feature.festival.flagdetail

import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.liveboard.LiveBoardState
import java.time.Instant

/**
 * 깃발 상세의 표시 입력(iOS `FestivalFlagDetailContext`). 부모가 보드 · 팩 · 내 일정 · 세션에서 만들어 넘긴다.
 * [accountId]가 바뀌면 상세가 계정별 값(팔로우)을 다시 받는다. [pinnedFlagId]는 라이브 액티비티 단계에서 쓴다.
 */
data class FlagDetailContext(
    val zoneNames: Map<String, String> = emptyMap(),
    val performances: Map<String, Performance> = emptyMap(),
    val mySetIds: Set<String> = emptySet(),
    val hasSession: Boolean = false,
    val accountId: String? = null,
    val pinnedFlagId: String? = null,
) {
    /** 계획 행에 보일 팩 공연. */
    data class Performance(
        val artist: String,
        val stageName: String,
        val startsAt: Instant,
        val endsAt: Instant,
    )

    companion object {
        /**
         * 선택 축제 보드에서 표시 입력을 만든다(iOS `FestivalFlagDetailContext+Board`). 다른 축제의 팩 · 일정 · 대표 깃발은 섞지 않는다.
         *
         * @param board 깃발 탭이 따르는 라이브 보드 상태
         * @param snapshot 깃발 목록이 받은 라이브 보드(대표 깃발 ID)
         */
        fun board(board: LiveBoardState?, snapshot: FestivalLiveBoardSnapshot?): FlagDetailContext {
            val festivalId = board?.festivalId
            val pack = board?.displayDocument?.takeIf { it.id == festivalId }
            val schedule = board?.mySchedule?.takeIf { it.festivalId == festivalId }
            val pinnedSnapshot = snapshot?.takeIf { it.festivalId == festivalId }
            return FlagDetailContext(
                zoneNames = zoneNames(pack),
                performances = performances(pack),
                mySetIds = schedule?.setIds.orEmpty(),
                hasSession = board?.hasSession ?: false,
                accountId = board?.sessionUserId,
                pinnedFlagId = pinnedSnapshot?.pinnedFlagId,
            )
        }

        private fun zoneNames(pack: FestivalPackDocument?): Map<String, String> {
            val names = linkedMapOf<String, String>()
            pack?.zones.orEmpty().forEach { names.putIfAbsent(it.id, it.name) }
            return names
        }

        /** 시작 · 종료 시각을 읽을 수 있는 공연만 계획 행에 쓴다. 같은 ID는 처음 것을 쓴다. */
        private fun performances(pack: FestivalPackDocument?): Map<String, Performance> {
            val entries = linkedMapOf<String, Performance>()
            pack?.performances.orEmpty().forEach { performance ->
                val startsAt = FestivalIsoTime.parse(performance.startsAt) ?: return@forEach
                val endsAt = FestivalIsoTime.parse(performance.endsAt) ?: return@forEach
                entries.putIfAbsent(
                    performance.id,
                    Performance(
                        artist = performance.artist ?: "출연진 미정",
                        stageName = performance.stageName ?: "무대 미정",
                        startsAt = startsAt,
                        endsAt = endsAt,
                    ),
                )
            }
            return entries
        }
    }
}
