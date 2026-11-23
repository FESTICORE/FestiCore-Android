package io.festicore.domain.festival.sources

import io.festicore.domain.festival.sources.pack.FestivalPackUseCaseImpl
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalPackExpiryTests` 5건. 마지막 날 종료 시각 2026-11-01T14:00:00Z. */
class FestivalPackExpiryTest {
    private val lastDayEnd = Instant.parse("2026-11-01T14:00:00Z")

    private fun days(n: Long) = Duration.ofDays(n)

    @Test
    fun `마지막 날이 끝나고 30일이 될 때까지 팩을 남긴다`() = runTest {
        val service = MutablePackService(listOf(pack("fst_a", "2026-10-31T14:00:00Z", "2026-11-01T14:00:00Z")))

        assertEquals(emptyList<String>(), deleteExpired(service, lastDayEnd + days(29)))
        assertEquals(emptyList<String>(), deleteExpired(service, lastDayEnd + days(30) - Duration.ofSeconds(1)))
        assertTrue(service.deleted.isEmpty())

        assertEquals(listOf("fst_a"), deleteExpired(service, lastDayEnd + days(30)))
        assertEquals(listOf("fst_a"), service.deleted)
    }

    @Test
    fun `일자 순서와 관계없이 마지막 날 종료 시각을 쓴다`() = runTest {
        val service = MutablePackService(listOf(pack("fst_a", "2026-11-01T14:00:00.000Z", "2026-10-20T14:00:00.000Z")))

        // 첫날 기준으로는 30일이 지났지만 마지막 날 기준으로는 아직이다.
        assertEquals(emptyList<String>(), deleteExpired(service, lastDayEnd + days(29)))
    }

    @Test
    fun `종료 시각을 모르는 팩은 남긴다`() = runTest {
        val service = MutablePackService(
            listOf(
                raw("no_days", """{"id":"no_days"}"""),
                raw("empty_days", """{"id":"empty_days","days":[]}"""),
                raw("missing_last", """{"id":"missing_last","days":[{"endsAt":"2026-10-01T14:00:00Z"},{"date":"2026-10-02"}]}"""),
                raw("broken", "not json"),
            ),
        )

        assertEquals(emptyList<String>(), deleteExpired(service, lastDayEnd + days(365)))
        assertTrue(service.deleted.isEmpty())
    }

    @Test
    fun `팩만 지우고 준비물 기록은 남긴다`() = runTest {
        val service = MutablePackService(listOf(pack("fst_old", "2026-11-01T14:00:00Z"), pack("fst_new", "2026-12-01T14:00:00Z")))
        val store = FakePackLocalStore().apply { checklists["fst_old"] = """{"checkedIDs":["water"]}""" }

        val deleted = deleteExpired(service, lastDayEnd + days(30))

        assertEquals(listOf("fst_old"), deleted)
        assertEquals(listOf("fst_old"), service.deleted)
        assertEquals("""{"checkedIDs":["water"]}""", store.checklists["fst_old"])
    }

    @Test
    fun `한 팩을 지우지 못해도 나머지는 지운다`() = runTest {
        val service = MutablePackService(listOf(pack("fst_fail", "2026-11-01T14:00:00Z"), pack("fst_ok", "2026-11-01T14:00:00Z")))
        service.failingDeletes = setOf("fst_fail")

        assertEquals(listOf("fst_ok"), deleteExpired(service, lastDayEnd + days(31)))
    }

    private suspend fun deleteExpired(service: MutablePackService, now: Instant): List<String> =
        FestivalPackUseCaseImpl(service.repository(), FakeCatalogRepository()) { now }.deleteExpiredPacks()

    private fun pack(id: String, vararg ends: String): ServicePack =
        raw(id, """{"id":"$id","days":[${ends.joinToString(",") { """{"endsAt":"$it"}""" }}]}""")

    private fun raw(id: String, json: String) = ServicePack(id, 1, "e", json.encodeToByteArray())
}
