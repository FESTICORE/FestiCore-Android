package io.festicore.feature.festival.flagdetail

import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.feature.festival.liveboard.LiveBoardMySchedule
import io.festicore.feature.festival.liveboard.LiveBoardState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `FestivalFlagDetailContext+Board`: 선택 축제의 팩 · 일정 · 대표 깃발만 쓴다. */
class FlagDetailContextTest {
    private val pack =
        FestivalPackDocument(
            id = "festival",
            zones = listOf(FestivalPackDocument.Zone("zone_a", "리벳 스테이지 앞 왼쪽"), FestivalPackDocument.Zone("zone_a", "중복")),
            timetable =
                listOf(
                    FestivalPackDocument.Performance(
                        id = "set_1",
                        artist = "모쉬핏",
                        stageName = "TENT",
                        startsAt = "2026-11-07T14:00:00Z",
                        endsAt = "2026-11-07T15:00:00+09:00",
                    ),
                    FestivalPackDocument.Performance(id = "set_2", startsAt = "2026-11-07T14:00:00Z", endsAt = "2026-11-07T15:00:00Z"),
                    FestivalPackDocument.Performance(
                        id = "set_bad",
                        artist = "시각 없음",
                        startsAt = "2026-11-07 14:00",
                        endsAt = "2026-11-07T15:00:00Z",
                    ),
                    FestivalPackDocument.Performance(id = "set_open", artist = "끝 없음", startsAt = "2026-11-07T14:00:00Z"),
                ),
        )

    private fun snapshot(festivalId: String) = FestivalLiveBoardSnapshot(
        festivalId = festivalId,
        activeFlags = emptyList(),
        followedFlags = emptyList(),
        crew = null,
        pinnedFlagId = "flg_pin",
    )

    @Test
    fun selectedFestivalBoardBuildsContext() {
        val board =
            LiveBoardState(
                festivalId = "festival",
                selectedPack = pack,
                hasSession = true,
                sessionUserId = "user-a",
                mySchedule = LiveBoardMySchedule("festival", setOf("set_1")),
            )
        val context = FlagDetailContext.board(board, snapshot("festival"))
        assertEquals(mapOf("zone_a" to "리벳 스테이지 앞 왼쪽"), context.zoneNames)
        assertEquals(
            mapOf(
                "set_1" to
                    FlagDetailContext.Performance(
                        "모쉬핏",
                        "TENT",
                        Instant.parse("2026-11-07T14:00:00Z"),
                        Instant.parse("2026-11-07T06:00:00Z"),
                    ),
                "set_2" to
                    FlagDetailContext.Performance(
                        "출연진 미정",
                        "무대 미정",
                        Instant.parse("2026-11-07T14:00:00Z"),
                        Instant.parse("2026-11-07T15:00:00Z"),
                    ),
            ),
            context.performances,
        )
        assertEquals(setOf("set_1"), context.mySetIds)
        assertEquals(true, context.hasSession)
        assertEquals("user-a", context.accountId)
        assertEquals("flg_pin", context.pinnedFlagId)
    }

    @Test
    fun otherFestivalPackScheduleAndSnapshotAreNotMixed() {
        val board =
            LiveBoardState(
                festivalId = "festival",
                selectedPack = pack.copy(id = "other"),
                mySchedule = LiveBoardMySchedule("other", setOf("set_1")),
            )
        assertEquals(FlagDetailContext(), FlagDetailContext.board(board, snapshot("other")))
        assertEquals(FlagDetailContext(), FlagDetailContext.board(null, null))
    }
}
