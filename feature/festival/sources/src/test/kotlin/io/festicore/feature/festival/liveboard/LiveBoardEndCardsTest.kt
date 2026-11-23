package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.date
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.flag
import io.festicore.feature.festival.liveboard.LiveBoardFixtures.performance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `FestivalLiveBoardEndCardsTests`: 종료 직전 귀가 카드와 종료 후 요약 카드의 부분 · 잘못된 데이터 처리. */
class LiveBoardEndCardsTest {
    private fun model(directions: FestivalPackDocument.Directions): LiveBoardScreenModel {
        val document = FestivalPackDocument(id = "fst", name = "원더리벳 2026", directions = directions)
        val pack = CachedFestivalPack("fst", 1, "v1", checkedAt = null, document = document)
        return LiveBoardScreenModel(LiveBoardState(festivalId = "fst", selectedPack = document, cachedPacks = mapOf("fst" to pack)))
    }

    private fun at(time: String) = date("2026-11-08T$time:00+09:00")

    @Test
    fun `귀가 카드는 빠졌거나 잘못된 시각의 줄을 숨기고 출발편으로 고르지 않으며, 탈 수 있는 편이 없으면 숨긴다`() {
        val trainOnly = model(FestivalPackDocument.Directions(lastTrainAt = "2026-11-08T23:52:00+09:00")).homeRide(at("23:00"))
        assertEquals("막차까지 52분", trainOnly?.remain)
        assertNull(trainOnly?.shuttle)
        assertEquals("막차 23:52", trainOnly?.train)

        val invalidTrain =
            model(FestivalPackDocument.Directions(lastShuttleAt = "2026-11-08T23:40:00+09:00", lastTrainAt = "막차 미정"))
                .homeRide(at("23:00"))
        assertEquals("셔틀까지 40분", invalidTrain?.remain)
        assertEquals("마지막 셔틀 23:40", invalidTrain?.shuttle)
        assertNull(invalidTrain?.train)

        assertNull(model(FestivalPackDocument.Directions(lastShuttleAt = "잘못된 시각")).homeRide(at("23:00")))
        assertNull(model(FestivalPackDocument.Directions()).homeRide(at("23:00")))
        assertNull(model(FestivalPackDocument.Directions(lastTrainAt = "2026-11-08T23:52:00+09:00")).homeRide(at("23:55")))
    }

    @Test
    fun `귀가 카드는 서버 도보 분을 역 줄에 붙이고 걸어서 못 닿는 막차 대신 셔틀을 고른다`() {
        val directions =
            FestivalPackDocument.Directions(
                lastShuttleAt = "23:40",
                lastTrainAt = "2026-11-08T23:10:00+09:00",
                shuttleStopName = "정문",
                transitStationName = "잠실역",
                transitWalkMinutes = 15,
            )
        val ride = model(directions).homeRide(at("23:00"))
        // 막차까지 10분인데 역까지 걸어서 15분이라 셔틀을 고른다. `HH:mm` 셔틀 시각은 줄에는 보이지 않는다(iOS와 같음).
        assertEquals("셔틀까지 40분", ride?.remain)
        assertNull(ride?.shuttle)
        assertEquals("막차 23:10 · 잠실역 (도보 15분)", ride?.train)
    }

    @Test
    fun `저녁에 본 새벽 HH mm 출발편은 다음 날로 본다`() {
        val ride = model(FestivalPackDocument.Directions(lastShuttleAt = "00:30")).homeRide(at("23:00"))
        assertEquals("셔틀까지 90분", ride?.remain)
    }

    @Test
    fun `종료 요약의 본 공연은 마지막 공연일에 끝난 내 일정만 세고 다른 날 · 진행 중 · 잘못된 시각 공연은 뺀다`() {
        val pack =
            FestivalPackDocument(
                id = "fst",
                name = "이틀 축제",
                days =
                    listOf(
                        FestivalPackDocument.Day("2026-10-20", "2026-10-20T13:00:00+09:00", "2026-10-20T23:00:00+09:00"),
                        FestivalPackDocument.Day("2026-10-21", "2026-10-21T12:00:00+09:00", "2026-10-21T22:30:00+09:00"),
                    ),
                stages = listOf(FestivalPackDocument.Stage("stg_main", "메인")),
                sets =
                    listOf(
                        performance("set_day1", "stg_main", "첫날", "2026-10-20T19:00:00+09:00", "2026-10-20T20:00:00+09:00"),
                        performance("set_done", "stg_main", "끝난 공연", "2026-10-21T19:00:00+09:00", "2026-10-21T20:00:00+09:00"),
                        performance("set_other", "stg_main", "남의 공연", "2026-10-21T20:00:00+09:00", "2026-10-21T21:00:00+09:00"),
                        performance("set_playing", "stg_main", "진행 중", "2026-10-21T21:00:00+09:00", "2026-10-21T22:00:00+09:00"),
                        performance("set_bad", "stg_main", "잘못된 시각", "어제 저녁", "2026-10-21T18:00:00+09:00"),
                    ),
            )
        val state =
            LiveBoardState(
                festivalId = "fst",
                selectedPack = pack,
                hasSession = true,
                mySchedule = LiveBoardMySchedule("fst", setOf("set_day1", "set_done", "set_playing", "set_bad")),
                snapshot =
                    FestivalLiveBoardSnapshot(
                        festivalId = "fst",
                        activeFlags = emptyList(),
                        followedFlags = listOf(flag("flg_1"), flag("flg_2")),
                        crew =
                            FestivalLiveBoardSnapshot.Crew(
                                id = "crw",
                                name = "크루",
                                members =
                                    listOf(
                                        FestivalLiveBoardSnapshot.Crew.Member("usr_me", "나", null, null),
                                        FestivalLiveBoardSnapshot.Crew.Member("usr_friend", "친구", null, null),
                                    ),
                            ),
                    ),
            )
        val now = date("2026-10-21T21:30:00+09:00")
        val summary = LiveBoardScreenModel(state).doneSummary(now)
        assertEquals(LiveBoardScreenModel.DoneSummary(seenSetCount = 1, crewCount = 2, followedFlagCount = 2), summary)
        assertNull(LiveBoardScreenModel(state.copy(hasSession = false)).doneSummary(now))
    }
}
