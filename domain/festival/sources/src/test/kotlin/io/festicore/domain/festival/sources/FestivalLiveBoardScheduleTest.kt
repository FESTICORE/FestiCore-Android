package io.festicore.domain.festival.sources

import io.festicore.core.network.FestiCoreJson
import io.festicore.domain.festival.FestivalLiveBoardSchedule
import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.service.api.FestivalPackDocumentDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS Domain `FestivalLiveBoardScheduleTests` + 팩 문서 매핑. */
class FestivalLiveBoardScheduleTest {
    private fun document(json: String): FestivalPackDocument =
        FestiCoreJson.decodeFromString(FestivalPackDocumentDto.serializer(), json).toDomain()

    private fun at(value: String) = Instant.parse(value)

    @Test
    fun `잘못된 공연은 빼고 시작 시각이 같으면 ID 순이다`() {
        val pack = document(
            """{"id":"festival","stages":[{"id":"main","name":"메인"},{"id":"main","name":"중복"}],"sets":[""" +
                """{"id":"b","stageId":"main","artist":" B ","startsAt":"2026-10-30T03:00:00Z"},""" +
                """{"id":"a","stageId":"main","artist":"A","startsAt":"2026-10-30T03:00:00Z"},""" +
                """{"id":"bad","stageId":"main","artist":"X","startsAt":"2026-10-30T03:00:00Z","endsAt":"2026-10-30T02:00:00Z"},""" +
                """{"id":"naive","stageId":"main","artist":"X","startsAt":"2026-10-30T03:00:00"},""" +
                """{"id":"blank","stageId":"main","artist":" ","startsAt":"2026-10-30T03:00:00Z"}]}""",
        )

        val entries = FestivalLiveBoardSchedule.entries(pack)

        assertEquals(listOf("a", "b"), entries.map { it.slot.id })
        assertEquals(listOf("메인", "메인"), entries.map { it.stageName })
        assertEquals("B", entries.last().slot.artist)
    }

    @Test
    fun `무대 순서를 지키고 종료 시각은 제외한다`() {
        val pack = document(
            """{"id":"festival","sets":[""" +
                """{"id":"sub","stageName":"서브","artist":"S","startsAt":"2026-10-30T04:00:00Z"},""" +
                """{"id":"one","stageName":"메인","artist":"A","startsAt":"2026-10-30T03:00:00Z","endsAt":"2026-10-30T04:00:00Z"},""" +
                """{"id":"two","stageName":"메인","artist":"B","startsAt":"2026-10-30T04:00:00Z","endsAt":"2026-10-30T05:00:00Z"}]}""",
        )

        val stages = FestivalLiveBoardSchedule.stages(pack, at("2026-10-30T03:00:00Z"))
        assertEquals(listOf("서브", "메인"), stages.map { it.name })
        assertEquals("one", stages[1].current?.id)
        assertEquals("two", stages[1].next?.id)

        val handoff = FestivalLiveBoardSchedule.stages(pack, at("2026-10-30T04:00:00Z"))
        assertEquals(listOf("메인"), handoff.map { it.name })
        assertEquals("two", handoff.first().current?.id)

        assertTrue(FestivalLiveBoardSchedule.stages(pack, at("2026-10-30T05:00:00Z")).isEmpty())
    }

    @Test
    fun `종료를 모르는 공연은 현재가 되지 않지만 이전으로 남는다`() {
        val pack = document(
            """{"id":"festival","sets":[{"id":"one","stageName":"메인","artist":"A","startsAt":"2026-10-30T03:00:00Z"},""" +
                """{"id":"two","stageName":"메인","artist":"B","startsAt":"2026-10-30T04:00:00Z"}]}""",
        )

        val stage = FestivalLiveBoardSchedule.stages(pack, at("2026-10-30T03:30:00Z")).first()

        assertNull(stage.current)
        assertEquals("one", stage.previous?.id)
        assertEquals("two", stage.next?.id)
    }

    @Test
    fun `서울 오프셋 시각과 소수초를 받는다`() {
        val pack = document(
            """{"id":"festival","timetable":[{"id":"a","stageId":"s1","stageName":"LIVET STAGE","artist":"A",""" +
                """"startsAt":"2026-11-20T12:00:00+09:00","endsAt":"2026-11-20T12:30:00.500+09:00"}]}""",
        )

        val slot = FestivalLiveBoardSchedule.entries(pack).single().slot

        assertEquals(at("2026-11-20T03:00:00Z"), slot.startsAt)
        assertEquals(at("2026-11-20T03:30:00.500Z"), slot.endsAt)
    }

    @Test
    fun `timetable이 없으면 sets에 무대 이름을 채우고 메모 공백은 지운다`() {
        val pack = document(
            """{"id":"festival","timetable":[],"stages":[{"id":"s1","name":"메인"}],""" +
                """"sets":[{"id":"a","stageId":"s1","artist":"A","note":"  메모  "},{"id":"b","stageId":"s2","artist":"B","note":"  "}],""" +
                """"zones":[{"id":"z1","name":"앞"}]}""",
        )

        assertEquals(listOf("메인", null), pack.performances.map { it.stageName })
        assertEquals(listOf("메모", null), pack.performances.map { it.note })
        assertEquals("앞", pack.zoneName("z1"))
        assertNull(pack.zoneName(null))
    }
}
