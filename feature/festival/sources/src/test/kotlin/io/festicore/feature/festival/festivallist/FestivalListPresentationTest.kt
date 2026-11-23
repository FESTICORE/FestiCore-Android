package io.festicore.feature.festival.festivallist

import io.festicore.domain.festival.entity.FestivalCatalogArtist
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPerformance
import io.festicore.domain.festival.entity.FestivalOverview
import io.festicore.feature.festival.festivallist.FestivalListPresentation.CardStatus
import io.festicore.feature.festival.festivallist.FestivalListPresentation.PackCopy
import io.festicore.feature.festival.festivallist.FestivalListPresentation.Timing
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalListPresentationTests`를 같은 이름으로 옮긴다. 분석(`listAppearanceTracksScreenView`) · 팩만 있는 행(`packOnlyRow…`)은 해당 단계에서 옮긴다. */
class FestivalListPresentationTest {
    @Test
    fun searchAndBookedFilterUseActualCatalogFields() {
        val festivals = listOf(festival("one", name = "원더리벳", venue = "고양"), festival("two", name = "펜타포트", venue = "인천"))

        assertEquals(listOf("two"), FestivalListPresentation.visible(festivals, "인천", setOf("two"), onlyBooked = false).map { it.id })
        assertEquals(listOf("one"), FestivalListPresentation.visible(festivals, "", setOf("one"), onlyBooked = true).map { it.id })
        assertTrue(FestivalListPresentation.visible(festivals, "펜타", setOf("one"), onlyBooked = true).isEmpty())
    }

    @Test
    fun searchMatchesCaseDiacriticsAndFullWidthCharacters() {
        val festivals = listOf(festival("one", name = "Ｃａｆé Festival", venue = "서울"))

        assertEquals(listOf("one"), FestivalListPresentation.visible(festivals, "cafe", emptySet(), onlyBooked = false).map { it.id })
        assertEquals(listOf("one"), FestivalListPresentation.visible(festivals, "ＣＡＦＥ", emptySet(), onlyBooked = false).map { it.id })
    }

    @Test
    fun `한글은 음절 경계를 넘어 일치하지 않는다`() {
        val festivals = listOf(festival("one", name = "각성"))

        assertTrue(FestivalListPresentation.visible(festivals, "가", emptySet(), onlyBooked = false).isEmpty())
        assertEquals(listOf("one"), FestivalListPresentation.visible(festivals, "각", emptySet(), onlyBooked = false).map { it.id })
    }

    @Test
    fun artistSearchUsesServerProjectionAndKeepsFestivalResultSeparate() {
        val item =
            FestivalCatalogItem(
                id = "one",
                name = "원더리벳",
                venue = "고양",
                packVersion = 1,
                artists =
                    listOf(
                        FestivalCatalogArtist(
                            name = "삼보마스터",
                            performances = listOf(
                                FestivalCatalogPerformance(startsAt = "2026-11-21T09:00:00Z", date = "2026-11-21", stageName = "리벳 스테이지"),
                            ),
                        ),
                    ),
            )
        val festival = FestivalListPresentation.overview(item)

        assertTrue(FestivalListPresentation.visible(listOf(festival), "삼보", emptySet(), onlyBooked = false).isEmpty())
        val matches = FestivalListPresentation.matchingArtists(listOf(festival), "삼보")
        assertEquals(listOf("삼보마스터"), matches.map { it.artist.name })
        assertEquals(listOf("one"), matches.map { it.festival.id })
        assertEquals("원더리벳 · 11/21(토) 리벳 스테이지", FestivalListPresentation.artistMeta(matches[0], utc("2026-11-01T03:00:00Z")))
        assertTrue(FestivalListPresentation.matchingArtists(listOf(festival), " ").isEmpty())
        assertNull(FestivalListPresentation.overview(FestivalCatalogItem(id = "old", name = "옛 데이터", packVersion = 1)).artists)
    }

    @Test
    fun dDayLiveAndEndedUseKoreanCalendarDays() {
        val festival = festival("one", name = "공연", firstDate = "2026-10-30", lastDate = "2026-10-31")

        assertEquals(Timing.DaysUntil(2), FestivalListPresentation.timing(festival, utc("2026-10-27T15:00:00Z")))
        assertEquals(Timing.Live, FestivalListPresentation.timing(festival, utc("2026-10-30T00:00:00Z")))
        assertEquals(Timing.Ended, FestivalListPresentation.timing(festival, utc("2026-10-31T15:00:00Z")))
    }

    @Test
    fun listDateUsesKoreanDayAndRejectsInvalidServerDate() {
        assertEquals("10/30(금)", FestivalListPresentation.parseDay("2026-10-30")?.let(FestivalListPresentation::festivalDay))
        assertNull(FestivalListPresentation.parseDay("2026-02-30"))
    }

    @Test
    fun `카드 메타는 장소와 날짜를 잇고 없는 값은 뺀다`() {
        assertEquals(
            "KINTEX HALL 7/8/9/10 · 11/20(금) – 11/22(일)",
            FestivalListPresentation.meta(festival("a", venue = "KINTEX HALL 7/8/9/10", firstDate = "2026-11-20", lastDate = "2026-11-22")),
        )
        assertEquals("11/20(금) – 11/20(금)", FestivalListPresentation.meta(festival("b", firstDate = "2026-11-20", lastDate = "2026-11-20")))
        assertEquals("고양", FestivalListPresentation.meta(festival("c", venue = "고양", firstDate = "2026-02-30")))
    }

    @Test
    fun cardStatusFollowsFigmaPrecedenceAndRedThreshold() {
        val now = utc("2026-11-01T03:00:00Z")
        assertEquals(CardStatus.Closing("D-3"), FestivalListPresentation.cardStatus(festival("d3", firstDate = "2026-11-04"), now))
        assertEquals(CardStatus.DDay("D-4"), FestivalListPresentation.cardStatus(festival("d4", firstDate = "2026-11-05"), now))
        assertEquals(CardStatus.DDay("진행 중"), FestivalListPresentation.cardStatus(festival("live", firstDate = "2026-11-01"), now))
        assertEquals(CardStatus.DDay("일정 확인"), FestivalListPresentation.cardStatus(festival("none"), now))
        assertEquals(
            CardStatus.Closing("D-1"),
            FestivalListPresentation.cardStatus(festival("soon", firstDate = "2026-11-02", status = "discovered"), now),
        )
        assertEquals(
            CardStatus.Upcoming,
            FestivalListPresentation.cardStatus(festival("v0", firstDate = "2026-11-02", packVersion = 0), now),
        )
        assertEquals(
            CardStatus.Ended,
            FestivalListPresentation.cardStatus(
                festival("end", firstDate = "2026-10-01", lastDate = "2026-10-02", status = "discovered"),
                now,
            ),
        )
        assertEquals("D-1", FestivalListPresentation.searchBadge(festival("soon", firstDate = "2026-11-02", status = "discovered"), now))
        assertNull(FestivalListPresentation.searchBadge(festival("end", firstDate = "2026-10-01", lastDate = "2026-10-02"), now))
    }

    @Test
    fun pastLastDateEndsFestivalEvenWithoutValidFirstDate() {
        val now = utc("2026-11-01T03:00:00Z")
        assertEquals(Timing.Ended, FestivalListPresentation.timing(festival("a", lastDate = "2026-10-02"), now))
        assertEquals(Timing.Ended, FestivalListPresentation.timing(festival("b", firstDate = "2026-02-30", lastDate = "2026-10-02"), now))
        assertEquals(Timing.Undated, FestivalListPresentation.timing(festival("c", lastDate = "2026-12-02"), now))
    }

    @Test
    fun endedOrderFallsBackToFirstDateWhenLastDateIsInvalid() {
        val festivals =
            listOf(
                festival("early", firstDate = "2026-10-01", lastDate = "2026-10-02"),
                festival("lateInvalidEnd", firstDate = "2026-10-31", lastDate = "2026-02-30"),
            )

        assertEquals(
            listOf("lateInvalidEnd", "early"),
            FestivalListPresentation.sorted(festivals, utc("2026-11-10T03:00:00Z")).map {
                it.id
            },
        )
    }

    @Test
    fun packCopyPreservesReceivedPacksAndAllowsPublishedEndedDownloads() {
        fun copy(festival: FestivalOverview) = FestivalListPresentation.packCopy(festival)
        val received = PackCopy.Received("팩 받음 · 최신 버전")
        val unpublished = PackCopy.Unavailable("팩 발행을 기다리고 있어요")

        assertEquals(PackCopy.Ready("팩 받기"), copy(festival("end", firstDate = "2026-10-01")))
        assertEquals(
            PackCopy.Ready("팩 업데이트"),
            copy(festival("endedStale", firstDate = "2026-10-01", packVersion = 3, cachedPackVersion = 2)),
        )
        assertEquals(unpublished, copy(festival("endedUnpublished", firstDate = "2026-10-01", packVersion = 0)))
        assertEquals(received, copy(festival("endedReceived", firstDate = "2026-10-01", packVersion = 2, cachedPackVersion = 2)))
        assertEquals(received, copy(festival("endedNewer", firstDate = "2026-10-01", packVersion = 2, cachedPackVersion = 3)))
        assertEquals(PackCopy.Ready("팩 받기"), copy(festival("lastDay", firstDate = "2026-11-01", lastDate = "2026-11-01")))
        assertEquals(PackCopy.Ready("팩 받기"), copy(festival("soon", firstDate = "2026-11-20", status = "discovered")))
        assertEquals(unpublished, copy(festival("unpublished", firstDate = "2026-11-20", packVersion = 0)))
        assertEquals(PackCopy.Ready("팩 받기"), copy(festival("published", firstDate = "2026-11-20", status = "upcoming")))
        assertEquals(received, copy(festival("same", firstDate = "2026-11-20", packVersion = 2, cachedPackVersion = 2)))
        assertEquals(received, copy(festival("newer", firstDate = "2026-11-20", packVersion = 2, cachedPackVersion = 3)))
        assertEquals(PackCopy.Ready("팩 업데이트"), copy(festival("stale", firstDate = "2026-11-20", packVersion = 3, cachedPackVersion = 2)))
    }

    @Test
    fun packCopyDependsOnlyOnPublishedAndCachedVersions() {
        listOf(null, 1, 2, 3).forEach { cachedVersion ->
            val copy = FestivalListPresentation.packCopy(
                festival("festival", firstDate = "2026-11-20", packVersion = 2, cachedPackVersion = cachedVersion),
            )
            val expected =
                if (cachedVersion != null && cachedVersion >= 2) {
                    PackCopy.Received("팩 받음 · 최신 버전")
                } else {
                    PackCopy.Ready(if (cachedVersion == null) "팩 받기" else "팩 업데이트")
                }
            assertEquals(expected, copy)
        }
    }

    @Test
    fun sortedUsesScheduleDatesBeforeUndatedAndEndedRegardlessOfPackPublication() {
        val festivals =
            listOf(
                festival("endedOld", firstDate = "2026-10-01", lastDate = "2026-10-02"),
                festival("far", firstDate = "2026-12-01"),
                festival("soon", firstDate = "2026-11-03", status = "discovered", packVersion = 0),
                festival("endedNew", firstDate = "2026-10-20", lastDate = "2026-10-21"),
                festival("near", firstDate = "2026-11-05"),
                festival("live", firstDate = "2026-10-31", lastDate = "2026-11-02"),
                festival("undated"),
            )

        assertEquals(
            listOf("live", "soon", "near", "far", "undated", "endedNew", "endedOld"),
            FestivalListPresentation.sorted(festivals, utc("2026-11-01T03:00:00Z")).map { it.id },
        )
    }

    @Test
    fun appendedPagesKeepEndedLastAndUseStableIDsForSameDates() {
        val firstPage = listOf(festival("ended", firstDate = "2026-10-01"), festival("z", firstDate = "2026-12-01"))
        val nextPage = listOf(festival("near", firstDate = "2026-11-02", packVersion = 0), festival("a", firstDate = "2026-12-01"))

        assertEquals(
            listOf("near", "a", "z", "ended"),
            FestivalListPresentation.sorted(firstPage + nextPage, utc("2026-11-01T03:00:00Z")).map {
                it.id
            },
        )
    }

    @Test
    fun `다가오는 페스티벌은 진행 중과 날짜 있는 예정만 일정 순으로 고른다`() {
        val festivals =
            listOf(
                festival("far", firstDate = "2026-12-01"),
                festival("ended", firstDate = "2026-10-01"),
                festival("undated"),
                festival("live", firstDate = "2026-11-01"),
            )

        assertEquals(listOf("live", "far"), FestivalListPresentation.upcoming(festivals, utc("2026-11-01T03:00:00Z")).map { it.id })
    }

    private fun festival(
        id: String,
        name: String? = null,
        venue: String? = null,
        firstDate: String? = null,
        lastDate: String? = null,
        status: String? = null,
        packVersion: Int = 1,
        cachedPackVersion: Int? = null,
    ): FestivalOverview = FestivalListPresentation
        .overview(
            FestivalCatalogItem(
                id = id,
                name = name ?: id,
                venue = venue,
                firstDate = firstDate,
                lastDate = lastDate,
                packVersion = packVersion,
                status = status,
            ),
        ).copy(isCached = cachedPackVersion != null, cachedPackVersion = cachedPackVersion)

    private fun utc(value: String): Instant = Instant.parse(value)
}
