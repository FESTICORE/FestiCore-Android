package io.festicore.feature.festival.flag

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.FlagTabInput
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.liveboard.LiveBoardMySchedule
import io.festicore.feature.festival.liveboard.LiveBoardState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `FlagRoutingTests` 중 깃발 탭 동기화(`syncFlag` · `flagDetailContext`)가 만드는 목록 입력과 상세 문맥. */
class FlagBoardTest {
    private val pack =
        FestivalPackDocument(
            id = "festival-a",
            zones = listOf(FestivalPackDocument.Zone("zone_a", "리벳 스테이지 앞 왼쪽")),
            timetable =
                listOf(
                    FestivalPackDocument.Performance(
                        id = "set_a",
                        artist = "윤마치",
                        stageName = "리벳 스테이지",
                        startsAt = "2026-11-07T08:00:00Z",
                        endsAt = "2026-11-07T08:50:00Z",
                    ),
                    FestivalPackDocument.Performance(id = "set_broken", artist = "시간 없음", stageName = "리벳 스테이지"),
                ),
        )

    private val board =
        FlagBoard.of(
            LiveBoardState(
                festivalId = "festival-a",
                selectedPack = pack,
                hasSession = true,
                sessionUserId = "user-a",
                mySchedule = LiveBoardMySchedule("festival-a", setOf("set_a")),
            ),
        )

    private fun snapshot(festivalId: String) = FestivalLiveBoardSnapshot(
        festivalId = festivalId,
        activeFlags = emptyList(),
        followedFlags = emptyList(),
        crew = null,
        pinnedFlagId = "flg_1",
    )

    private val expectedContext =
        FlagDetailContext(
            zoneNames = mapOf("zone_a" to "리벳 스테이지 앞 왼쪽"),
            performances =
                mapOf(
                    "set_a" to
                        FlagDetailContext.Performance(
                            artist = "윤마치",
                            stageName = "리벳 스테이지",
                            startsAt = Instant.parse("2026-11-07T08:00:00Z"),
                            endsAt = Instant.parse("2026-11-07T08:50:00Z"),
                        ),
                ),
            mySetIds = setOf("set_a"),
            hasSession = true,
            accountId = "user-a",
            pinnedFlagId = "flg_1",
        )

    /** iOS `cardTapOpensDetailWithContext` · `flagDeeplinkOpensDetail`의 `expectedContext`. */
    @Test
    fun cardTapOpensDetailWithContext() {
        assertEquals(expectedContext, board.flagDetailContext(snapshot("festival-a"), accountId = "user-a"))
    }

    @Test
    fun `깃발 목록 입력은 보드 축제 · 존 이름 · 현재 계정을 따르고 보드가 없으면 비운다`() {
        assertEquals(
            FlagTabInput("festival-a", "user-a", mapOf("zone_a" to "리벳 스테이지 앞 왼쪽")),
            board.flagTabInput(accountId = "user-a"),
        )
        assertEquals(FlagTabInput(accountId = "user-a"), null.flagTabInput(accountId = "user-a"))
    }

    /** iOS `sessionChangeUpdatesOpenDetail` · `accountSwitchWhileDetailOpenReloadsDetail`: 계정이 바뀌면 새 계정 문맥을 만든다. */
    @Test
    fun sessionChangeUpdatesOpenDetail() {
        val switched = board.flagDetailContext(snapshot("festival-a"), accountId = "user-b")
        assertEquals("user-b", switched.accountId)
        assertEquals(true, switched.hasSession)

        val loggedOut = board.flagDetailContext(snapshot("festival-a"), accountId = null)
        assertEquals(null, loggedOut.accountId)
        assertEquals(false, loggedOut.hasSession)
    }

    @Test
    fun `다른 축제 스냅샷의 대표 깃발은 섞지 않고 보드가 없으면 빈 문맥에 세션만 둔다`() {
        assertEquals(null, board.flagDetailContext(snapshot("festival-b"), accountId = "user-a").pinnedFlagId)
        assertEquals(
            FlagDetailContext(hasSession = true, accountId = "user-a"),
            null.flagDetailContext(snapshot("festival-a"), accountId = "user-a"),
        )
    }
}
