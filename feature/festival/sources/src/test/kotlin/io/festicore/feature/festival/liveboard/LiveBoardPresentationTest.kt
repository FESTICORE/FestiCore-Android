package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.FestivalLiveBoardSchedule
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.date
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.festival
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.flag
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.pack
import io.festicore.feature.festival.liveboard.LiveBoardPresentation.Countdown
import io.festicore.feature.festival.liveboard.LiveBoardPresentation.Phase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalLiveBoardPresentationTests` 15건. */
class LiveBoardPresentationTest {
    private val festival = festival()

    @Test
    fun `활성 깃발은 최신 갱신 시각 순으로 정렬한다`() {
        val older = flag("old", updatedAt = Instant.ofEpochSecond(100))
        val newer = flag("new", updatedAt = Instant.ofEpochSecond(200))
        assertEquals(listOf("new", "old"), LiveBoardPresentation.sortedActiveFlags(listOf(older, newer)).map { it.id })
    }

    @Test
    fun `개장 전 활성 깃발은 앞으로 가장 이른 계획 공연 순, 계획 없는 깃발은 뒤에 최근 업데이트 순`() {
        val now = date("2026-10-20T15:10:00+09:00")
        val flags =
            listOf(
                flag("side", updatedAt = Instant.ofEpochSecond(500), plan = listOf("set_side")),
                // 이미 시작한 공연(set_now)은 도착 예정에서 뺀다.
                flag("next", updatedAt = Instant.ofEpochSecond(100), plan = listOf("set_now", "set_next")),
                flag("none_old", updatedAt = Instant.ofEpochSecond(200)),
                flag("none_new", updatedAt = Instant.ofEpochSecond(300), plan = listOf("set_unknown")),
            )
        assertEquals(
            listOf("next", "side", "none_new", "none_old"),
            LiveBoardPresentation.sortedActiveFlagsByArrival(flags, pack, now).map { it.id },
        )
    }

    @Test
    fun `서버 시각은 수신 후 흐른 기기 시간만큼 앞으로 간다`() {
        val serverAt = date("2026-10-20T15:00:00+09:00")
        // 기기 시계가 10분 빠르다.
        val receivedAt = serverAt.plusSeconds(600)
        assertEquals(serverAt, LiveBoardPresentation.serverNow(serverAt, receivedAt, receivedAt))
        assertEquals(serverAt.plusSeconds(120), LiveBoardPresentation.serverNow(serverAt, receivedAt, receivedAt.plusSeconds(120)))
        // 기기 시계가 10분 느려도 같다.
        val slow = serverAt.minusSeconds(600)
        assertEquals(serverAt.plusSeconds(60), LiveBoardPresentation.serverNow(serverAt, slow, slow.plusSeconds(60)))
        assertEquals(slow, LiveBoardPresentation.serverNow(null, slow, slow))
    }

    @Test
    fun `상대 시간과 오프라인 문구`() {
        val now = date("2026-10-20T15:00:00+09:00")
        assertEquals("방금", LiveBoardPresentation.relativeTime(now.minusSeconds(30), now))
        assertEquals("3분 전", LiveBoardPresentation.relativeTime(now.minusSeconds(180), now))
        assertEquals("2시간 전", LiveBoardPresentation.relativeTime(now.minusSeconds(7_200), now))
        assertNull(LiveBoardPresentation.relativeTime(null, now))
        assertEquals("오프라인 · 3분 전 정보", LiveBoardPresentation.offlineText(now.minusSeconds(180), now))
        assertEquals("오프라인", LiveBoardPresentation.offlineText(null, now))
        // 갱신 문구는 수신 시각과 같은 기기 시각으로 재므로 서버 시각 보정과 무관하다.
        assertEquals("2분 전 업데이트", LiveBoardPresentation.updatedText(now.minusSeconds(120), now))
        assertEquals("시간표 기준", LiveBoardPresentation.updatedText(null, now))
    }

    @Test
    fun `무대 행은 지금 · 다음 중 있는 쪽만 보이고 내 일정이면 표시한다`() {
        val rows =
            LiveBoardPresentation.stageRows(FestivalLiveBoardSchedule.stages(pack, date("2026-10-20T15:30:00+09:00")), setOf("set_next"))
        val main = rows.first { it.id == "stg_main" }
        assertEquals(LiveBoardPresentation.StageRow.Slot("스모킹구스", "15:35 종료"), main.now)
        assertEquals(LiveBoardPresentation.StageRow.Slot("노이즈캔디", "15:40 시작"), main.next)
        assertTrue(main.isMine)
        val side = rows.first { it.id == "stg_side" }
        assertNull(side.now)
        assertEquals(LiveBoardPresentation.StageRow.Slot("블루퓨즈", "16:00 시작"), side.next)
        assertFalse(side.isMine)

        val late = LiveBoardPresentation.stageRows(FestivalLiveBoardSchedule.stages(pack, date("2026-10-20T16:10:00+09:00")), emptySet())
        assertNull(late.first { it.id == "stg_main" }.next)
        assertEquals("노이즈캔디", late.first { it.id == "stg_main" }.now?.artist)
    }

    @Test
    fun `내 다음 일정이 있으면 먼저, 없으면 전체 다음 공연`() {
        val now = date("2026-10-20T15:30:00+09:00")
        val mine = LiveBoardPresentation.setCard(pack, setOf("set_side"), isBeforeOpening = false, now = now)
        assertEquals("내 다음 일정", mine?.label)
        assertEquals("블루퓨즈", mine?.artist)
        assertEquals("사이드 · 16:00–16:40", mine?.meta)
        assertEquals("30분 후", mine?.remain)
        assertEquals(0.5, mine?.progress ?: -1.0, 0.001)

        val next = LiveBoardPresentation.setCard(pack, emptySet(), isBeforeOpening = false, now = now)
        assertEquals("다음 공연", next?.label)
        assertEquals("노이즈캔디", next?.artist)

        val playing = LiveBoardPresentation.setCard(pack, setOf("set_now"), isBeforeOpening = false, now = now)
        assertEquals("공연 중", playing?.remain)
        val progress = playing?.progress ?: -1.0
        assertTrue(progress > 0 && progress < 1)
    }

    @Test
    fun `개장 전에는 카운트다운과 첫 공연 시작 시각을 보인다`() {
        val now = date("2026-10-20T10:46:00+09:00")
        val countdown = LiveBoardPresentation.countdown(pack, festival, now)
        assertEquals("2시간 14분", countdown?.time)
        assertEquals("18일 2시간", LiveBoardPresentation.countdown(pack, festival, date("2026-10-02T10:46:00+09:00"))?.time)
        assertEquals("13:00 개장 · 첫 공연 15:00 메인", countdown?.sub)

        val mine = LiveBoardPresentation.setCard(pack, setOf("set_next"), isBeforeOpening = true, now = now)
        assertEquals("내 첫 공연", mine?.label)
        assertEquals("15:40", mine?.remain)
        val first = LiveBoardPresentation.setCard(pack, emptySet(), isBeforeOpening = true, now = now)
        assertEquals("첫 공연", first?.label)
        assertEquals("15:00", first?.remain)

        assertNull(LiveBoardPresentation.countdown(pack, festival, date("2026-10-20T13:00:00+09:00")))
        assertEquals("DAY 1 · 10.20 (화) · 13:00–23:00", LiveBoardPresentation.dayLine(pack, festival, now))
    }

    @Test
    fun `개장 시각과 마지막 1시간 및 종료 시각의 경계를 팩 시간으로 판정한다`() {
        val opening = date("2026-10-20T13:00:00+09:00")
        val finalHour = date("2026-10-20T22:00:00+09:00")
        val end = date("2026-10-20T23:00:00+09:00")
        assertEquals(
            Phase.BeforeOpening(Countdown("0분", "13:00 개장 · 첫 공연 15:00 메인")),
            LiveBoardPresentation.phase(pack, festival, opening.minusSeconds(1)),
        )
        assertEquals(Phase.Live, LiveBoardPresentation.phase(pack, festival, opening))
        assertEquals(Phase.Live, LiveBoardPresentation.phase(pack, festival, finalHour.minusSeconds(1)))
        assertEquals(Phase.FinalHour(Countdown("1시간 0분", "23:00 종료")), LiveBoardPresentation.phase(pack, festival, finalHour))
        assertEquals(Phase.FinalHour(Countdown("0분", "23:00 종료")), LiveBoardPresentation.phase(pack, festival, end.minusSeconds(1)))
        assertEquals(Phase.Ended, LiveBoardPresentation.phase(pack, festival, end))
    }

    @Test
    fun `여러 날 축제의 중간날 종료는 축제 종료로 간주하지 않는다`() {
        val twoDays =
            FestivalPackDocument(
                id = "fst",
                days =
                    listOf(
                        FestivalPackDocument.Day("2026-10-20", "2026-10-20T13:00:00+09:00", "2026-10-20T23:00:00+09:00"),
                        FestivalPackDocument.Day("2026-10-21", "2026-10-21T12:00:00+09:00", "2026-10-21T22:00:00+09:00"),
                    ),
            )
        val festival = festival(name = "이틀 축제", lastDate = "2026-10-21")
        assertEquals(Phase.Live, LiveBoardPresentation.phase(twoDays, festival, date("2026-10-20T22:30:00+09:00")))
        assertEquals(
            Phase.BeforeOpening(Countdown("2시간 0분", "12:00 개장")),
            LiveBoardPresentation.phase(twoDays, festival, date("2026-10-21T10:00:00+09:00")),
        )
        assertEquals(Phase.Ended, LiveBoardPresentation.phase(twoDays, festival, date("2026-10-21T22:00:00+09:00")))
    }

    @Test
    fun `게시된 마지막 날짜나 종료 시각이 없으면 종료 상태를 추정하지 않는다`() {
        val noEnd =
            FestivalPackDocument(id = "fst", days = listOf(FestivalPackDocument.Day("2026-10-20", "2026-10-20T13:00:00+09:00", null)))
        val now = date("2026-10-20T23:30:00+09:00")
        assertEquals(Phase.Unknown, LiveBoardPresentation.phase(null, festival, now))
        assertEquals(Phase.Live, LiveBoardPresentation.phase(noEnd, festival, now))
        val missingFinalDay = festival(name = "이틀 축제", lastDate = "2026-10-21")
        assertEquals(Phase.Live, LiveBoardPresentation.phase(pack, missingFinalDay, now))
    }

    @Test
    fun `크루 부제는 가장 많은 구역을 고르고 본인 · 귀가 중을 표시한다`() {
        val now = date("2026-10-20T15:30:00+09:00")
        val crew =
            FestivalLiveBoardSnapshot.Crew(
                id = "crew",
                name = "락린이들",
                members =
                    listOf(
                        FestivalLiveBoardSnapshot.Crew.Member("me", "로이", "here", "zon_main", now.minusSeconds(20)),
                        FestivalLiveBoardSnapshot.Crew.Member("a", "민지", "eating", "zon_food", now.minusSeconds(420)),
                        FestivalLiveBoardSnapshot.Crew.Member("b", "준호", null, "zon_main", null),
                        FestivalLiveBoardSnapshot.Crew.Member("c", "한솔", "leaving", null, now.minusSeconds(3_600)),
                    ),
            )
        val summary = LiveBoardPresentation.crew(crew, "me", { pack.zoneName(it) }, now)
        assertEquals("내 크루 · 락린이들", summary.title)
        assertEquals("4명 · 2명이 메인 앞 근처", summary.subtitle)
        assertEquals(listOf(true, false, false, false), summary.members.map { it.isMe })
        assertEquals(listOf("나 여기", "밥", null, "귀가 중"), summary.members.map { it.status })
        assertEquals(listOf("방금", "7분 전", null, "1시간 전"), summary.members.map { it.time })
        assertEquals(listOf("메인 앞", "푸드존", "메인 앞", null), summary.members.map { it.zone })
        assertEquals(listOf(false, false, false, true), summary.members.map { it.isLeaving })
        assertEquals("4명", LiveBoardPresentation.crew(crew, null, { null }, now).subtitle)
    }

    @Test
    fun `깃발 상태 칩 · 태그 · 메타 문구`() {
        val now = date("2026-10-20T15:30:00+09:00")
        val flag =
            flag(
                "flg",
                status = "ended",
                zoneId = "zon_main",
                activity = "sing",
                tags = listOf("slam", "sing", "unknown"),
                updatedAt = now.minusSeconds(300),
            )
        val shown = LiveBoardPresentation.flag(flag, { pack.zoneName(it) }, now)
        assertEquals(LiveBoardPresentation.FlagStatus.Ended, shown.status)
        assertEquals("슬램 · 떼창", shown.tags)
        assertEquals("메인 앞 · 떼창", shown.meta)
        assertEquals("5분 전", shown.time)
        assertNull(LiveBoardPresentation.flagStatus("scheduled"))
        assertEquals(LiveBoardPresentation.FlagStatus.Resting, LiveBoardPresentation.flagStatus("resting"))
        assertNull(LiveBoardPresentation.tagText("resting"))
    }

    @Test
    fun `출처 줄은 이름이 있을 때만 그리고 확인 시각을 붙인다`() {
        assertEquals("출처: 주최측 공지 · 확인 10/16 14:00", LiveBoardPresentation.sourceLine(pack.source))
        assertNull(
            LiveBoardPresentation.sourceLine(FestivalPackDocument.Source(name = null, url = null, verifiedAt = "2026-10-16T05:00:00Z")),
        )
        assertEquals("출처: 운영팀", LiveBoardPresentation.sourceLine(FestivalPackDocument.Source(name = "운영팀", url = null, verifiedAt = null)))
    }

    @Test
    fun `바꾸기 시트 부제는 오늘이면 회차와 진행 상태를 보인다`() {
        for ((time, status) in listOf("12:00:00" to "시작 전", "15:00:00" to "진행 중", "23:00:00" to "종료")) {
            assertEquals("오늘 · DAY 1 $status", LiveBoardPresentation.switcherSubtitle(pack, festival, date("2026-10-20T$time+09:00")))
        }
    }

    @Test
    fun `종료 요약은 날짜 없이 축제 이름과 회차 및 운영 시간을 표시한다`() {
        assertEquals(
            "${festival.name} DAY 1 · 13:00–23:00",
            LiveBoardPresentation.doneSubtitle(pack, festival, date("2026-10-20T23:00:00+09:00")),
        )
    }

    @Test
    fun `팔로우 깃발은 검증된 축제 최종 종료 경계에서만 오늘 종료로 표시한다`() {
        val flag = flag("flg")
        val multiDayFestival = festival(name = "이틀 축제", lastDate = "2026-10-21")
        val cases =
            listOf(
                Triple(festival, "2026-10-20T22:59:59+09:00", LiveBoardPresentation.FlagStatus.Active),
                Triple(festival, "2026-10-20T23:00:00+09:00", LiveBoardPresentation.FlagStatus.Ended),
                Triple(multiDayFestival, "2026-10-20T23:00:00+09:00", LiveBoardPresentation.FlagStatus.Active),
            )
        for ((overview, time, expected) in cases) {
            val now = date(time)
            val ended = LiveBoardPresentation.phase(pack, overview, now) == Phase.Ended
            assertEquals(expected, LiveBoardPresentation.flag(flag, { null }, now, festivalEnded = ended).status)
        }
        assertEquals("active", flag.status)
    }
}
