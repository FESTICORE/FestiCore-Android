package io.festicore.domain.festival.sources

import io.festicore.core.storage.shared.WidgetSnapshotRecord
import io.festicore.core.storage.shared.WidgetSnapshotStore
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.sources.widget.FestivalWidgetRepository
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalWidgetRepositoryTest {
    private var selected: String? = "a"
    private var owner: String? = "owner-a"
    private val packs = mutableMapOf("a" to pack("a"), "b" to pack("b"))
    private val store = object : WidgetSnapshotStore {
        var value = WidgetSnapshotRecord()
        var failWrites = false
        override suspend fun read() = value
        override suspend fun compareAndSet(
            expectedRevision: Long,
            payload: String?,
            invalidate: Boolean,
        ): Boolean {
            if (failWrites) error("disk unavailable")
            if (value.revision != expectedRevision) return false
            value = WidgetSnapshotRecord(value.revision + 1, value.generation + if (invalidate) 1 else 0, payload)
            return true
        }
    }
    private fun repository() = FestivalWidgetRepository(store, { selected }, { owner }, { emptyFlow() }, { packs[it] })

    @Test fun `upgrade missing snapshot restores public disk pack without inventing empty schedule`() = runTest {
        val result = repository().read().snapshot
        assertEquals("a", result?.festivalId)
        assertFalse(result?.scheduleKnownForOwner ?: true)
    }

    @Test fun `late account A completion cannot enter account B snapshot`() = runTest {
        val repo = repository()
        val a = repo.capture("a")
        owner = "owner-b"
        repo.schedule(a, listOf("set"))
        assertFalse(repo.read().snapshot?.performances?.any { it.isSaved } ?: true)
        repo.schedule(repo.capture("a"), emptyList())
        assertTrue(repo.read().snapshot?.scheduleKnownForOwner ?: false)
    }

    @Test fun `saved selection mismatch and deleted disk pack override stale payload`() = runTest {
        val repo = repository()
        repo.schedule(repo.capture("a"), listOf("set"))
        selected = "b"
        assertEquals("b", repo.read().snapshot?.festivalId)
        packs.remove("b")
        assertNull(repo.read().snapshot)
    }

    @Test fun `confirmed schedule survives repository recreation and unchanged save skips write`() = runTest {
        val repo = repository()
        repo.schedule(repo.capture("a"), listOf("set"))
        val before = store.value.revision
        repo.schedule(repo.capture("a"), listOf("set"))
        assertEquals(before, store.value.revision)
        assertTrue(repository().read().snapshot?.performances?.single()?.isSaved ?: false)
    }

    @Test fun `schedule and crew board responses merge in reverse completion order`() = runTest {
        val repo = repository()
        val olderBoard = repo.capture("a")
        repo.schedule(repo.capture("a"), listOf("set"))
        repo.board(olderBoard, board())
        assertEquals("크루", repo.read().snapshot?.crewSummary)
        assertTrue(repo.read().snapshot?.performances?.single()?.isSaved ?: false)
    }

    @Test fun `independent pin and crew mutations cannot suppress each other`() = runTest {
        val repo = repository()
        repo.board(repo.capture("a"), board())
        val olderCrew = repo.capture("a")
        repo.pin(repo.capture("a"), null)
        repo.crewChanged(olderCrew)
        assertNull(repo.read().snapshot?.pinnedFlagSummary)
        assertNull(repo.read().snapshot?.crewSummary)
    }

    @Test fun `new pin stays pending until board establishes festival membership`() = runTest {
        val repo = repository()
        repo.board(repo.capture("a"), board())
        repo.pin(repo.capture("a"), "new")
        assertNull(repo.read().snapshot?.pinnedFlagSummary)
        repo.board(repo.capture("a"), board(flags = listOf(flag("old"), flag("new"))).copy(pinnedFlagId = "new"))
        assertEquals("깃발 new", repo.read().snapshot?.pinnedFlagSummary)
    }

    @Test fun `older board refresh preserves newer pin but still updates crew`() = runTest {
        val repo = repository()
        repo.board(repo.capture("a"), board(flags = listOf(flag("old"), flag("new"))))
        val boardRequest = repo.capture("a")
        repo.pin(repo.capture("a"), "new")
        repo.board(boardRequest, board(crewName = "새 크루"))
        assertEquals("깃발 new", repo.read().snapshot?.pinnedFlagSummary)
        assertEquals("새 크루", repo.read().snapshot?.crewSummary)
    }

    @Test fun `account B patch resets account A schedule pin and crew`() = runTest {
        val repo = repository()
        repo.schedule(repo.capture("a"), listOf("set"))
        repo.board(repo.capture("a"), board())
        owner = "owner-b"
        repo.schedule(repo.capture("a"), emptyList())
        val snapshot = repo.read().snapshot
        assertTrue(snapshot?.scheduleKnownForOwner ?: false)
        assertNull(snapshot?.crewSummary)
        assertNull(snapshot?.pinnedFlagSummary)
        assertFalse(snapshot?.performances?.any { it.isSaved } ?: true)
    }

    @Test fun `logout and selection round trip reject captured old completions`() = runTest {
        val repo = repository()
        val old = repo.capture("a")
        owner = null
        repo.schedule(old, listOf("set"))
        assertFalse(repo.read().snapshot?.scheduleKnownForOwner ?: true)
        owner = "owner-a"
        selected = "b"
        repo.selectionChanged("a", "b")
        selected = "a"
        repo.selectionChanged("b", "a")
        repo.schedule(old, listOf("set"))
        assertFalse(repo.read().snapshot?.scheduleKnownForOwner ?: true)
    }

    @Test fun `same selection preserves snapshot generation and pending requests`() = runTest {
        val repo = repository()
        repo.schedule(repo.capture("a"), listOf("set"))
        val before = store.value
        repo.selectionChanged("a", "a")
        assertEquals(before, store.value)
    }

    @Test fun `cross festival pin response cannot change selected festival`() = runTest {
        val repo = repository()
        repo.board(repo.capture("a"), board())
        repo.pin(repo.capture("b"), "foreign")
        assertEquals("깃발 old", repo.read().snapshot?.pinnedFlagSummary)
    }

    @Test fun `reading crew detail does not invalidate a confirmed widget crew summary`() = runTest {
        val widget = repository()
        widget.board(widget.capture("a"), board())
        val client = io.festicore.core.network.client.createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = io.ktor.client.engine.mock.MockEngine {
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{"id":"crw_a","festivalId":"a","name":"우리","ownerId":"usr_a","members":[{"userId":"usr_a","nickname":"로이","joinedAt":"2026-11-23T01:02:03Z"}],"inviteCode":"ABCD23","inviteExpiresAt":"2026-11-25T01:02:03Z","archivedAt":null}}""",
                    io.ktor.http.HttpStatusCode.OK,
                    io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            io.festicore.domain.festival.sources.repository.FestivalCrewRepositoryImpl(
                io.festicore.service.apiendpoint.crew.CrewRequest(client),
                currentSessionId = { owner },
                widget = widget,
                currentUserId = { "usr_a" },
            ).detail("crw_a")
            assertEquals("크루", widget.read().snapshot?.crewSummary)
        } finally {
            client.close()
        }
    }

    @Test fun `WI4 updated pack keeps same owner personal projection and filters removed sets`() = runTest {
        val repo = repository()
        repo.schedule(repo.capture("a"), listOf("set", "removed"))
        repo.board(repo.capture("a"), board())
        packs["a"] = pack("a").copy(packVersion = 2, etag = "v2")
        repo.refreshPublic()
        val updated = repo.read().snapshot
        assertTrue(updated?.scheduleKnownForOwner ?: false)
        assertTrue(updated?.performances?.single()?.isSaved ?: false)
        assertEquals("크루", updated?.crewSummary)
        assertEquals("깃발 old", updated?.pinnedFlagSummary)
        repo.crewChanged(repo.capture("a"))
        assertTrue(repo.read().snapshot?.performances?.single()?.isSaved ?: false)
    }

    @Test fun `WR3 corrupt payload keeps visible warning beside recovered public pack`() = runTest {
        owner = null
        store.value = WidgetSnapshotRecord(7, 3, null, unavailable = true)
        val repo = repository()
        assertEquals("a", repo.read().snapshot?.festivalId)
        val next = repo.read()
        assertTrue(next.unavailable)
        assertEquals("a", next.snapshot?.festivalId)
        assertEquals(3L, store.value.generation)
    }

    @Test fun `WI3 public snapshot carries supplied shuttle and station names`() = runTest {
        val pack = pack("a")
        packs["a"] = pack.copy(
            document = pack.document?.copy(
                directions = FestivalPackDocument.Directions(
                    lastShuttleAt = "22:20",
                    lastTrainAt = "23:10",
                    shuttleStopName = "동문",
                    transitStationName = "축제역",
                ),
            ),
        )
        assertEquals("동문", repository().read().snapshot?.shuttleStopName)
        assertEquals("축제역", repository().read().snapshot?.transitStationName)
    }

    @Test fun `WI7 optional previous selection read failure cannot block primary save`() = runTest {
        val preferences = object : io.festicore.core.storage.shared.LiveBoardPreferenceStore {
            var saved: String? = null
            override suspend fun selectedFestivalId(): String? = error("read failed")
            override suspend fun saveSelectedFestivalId(festivalId: String) {
                saved = festivalId
            }
            override suspend fun isFollowedFlagGuideHidden(festivalId: String) = false
            override suspend fun hideFollowedFlagGuide(festivalId: String) = Unit
        }
        val useCase = io.festicore.domain.festival.sources.liveboard.FestivalLiveBoardPreferenceUseCaseImpl(preferences)
        assertTrue(runCatching { useCase.saveSelectedFestivalId("b") }.isSuccess)
        assertEquals("b", preferences.saved)
    }

    @Test fun `WI6 explicit user loss invalidates personal payload and rejects captured request`() = runTest {
        val repo = repository()
        repo.schedule(repo.capture("a"), listOf("set"))
        repo.board(repo.capture("a"), board())
        val old = repo.capture("a")
        repo.invalidatePersonal()
        repo.schedule(old, listOf("set"))
        val snapshot = repo.read().snapshot
        assertFalse(snapshot?.scheduleKnownForOwner ?: true)
        assertNull(snapshot?.crewSummary)
        assertNull(snapshot?.pinnedFlagSummary)
    }

    @Test fun `WI6 failed invalidation suppresses retained owner data and new writes`() = runTest {
        val repo = repository()
        repo.schedule(repo.capture("a"), listOf("set"))
        store.failWrites = true
        assertTrue(runCatching { repo.invalidatePersonal() }.isFailure)
        assertFalse(repo.read().snapshot?.scheduleKnownForOwner ?: true)
        store.failWrites = false
        repo.schedule(repo.capture("a"), listOf("set"))
        assertFalse(repo.read().snapshot?.scheduleKnownForOwner ?: true)
        owner = "same-account-new-login-session"
        repo.schedule(repo.capture("a"), listOf("set"))
        assertTrue(repo.read().snapshot?.scheduleKnownForOwner ?: false)
    }

    private fun flag(id: String) = FestivalLiveBoardSnapshot.Flag(id, "깃발 $id", "active", null, null, null)
    private fun board(flags: List<FestivalLiveBoardSnapshot.Flag> = listOf(flag("old")), crewName: String = "크루") =
        FestivalLiveBoardSnapshot(
            "a",
            flags,
            flags,
            FestivalLiveBoardSnapshot.Crew("crew", crewName, emptyList()),
            pinnedFlagId = "old",
            directoryFlags = flags,
        )

    private fun pack(id: String) = CachedFestivalPack(
        id,
        1,
        "etag",
        null,
        FestivalPackDocument(
            id = id,
            name = "Festival $id",
            timetable = listOf(
                FestivalPackDocument.Performance(
                    id = "set",
                    artist = "Artist",
                    stageId = "main",
                    stageName = "Main",
                    startsAt = "2026-11-26T10:00:00Z",
                    endsAt = "2026-11-26T11:00:00Z",
                ),
            ),
        ),
    )
}
