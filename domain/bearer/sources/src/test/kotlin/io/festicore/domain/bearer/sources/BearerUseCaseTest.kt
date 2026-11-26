package io.festicore.domain.bearer.sources

import io.festicore.domain.bearer.bearer.BearerRepository
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestivalTransfer
import io.festicore.domain.bearer.entity.BearerFestivalTransferRequest
import io.festicore.domain.bearer.entity.BearerFlagPhoto
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFollowedFlag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.domain.bearer.sources.bearer.BearerUseCaseImpl
import io.festicore.service.pack.CachedFestivalPack
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `BearerDomainTests.swift` `BearerUseCaseTests` 5건(같은 이름). 사진 스트림 · 인코더 5건은 BD2. */
class BearerUseCaseTest {
    @Test
    fun makeStatusRequest() {
        val now = Instant.ofEpochSecond(1_000)
        val useCase = useCase(
            newUuid = { "00000000-0000-0000-0000-00000000000A" },
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        val request = useCase.makeStatusRequest(BearerStatusChange.Rest)

        assertEquals(BearerStatusChange.Rest, request.change)
        assertEquals(now, request.clientAt)
        assertEquals("00000000-0000-0000-0000-00000000000a", request.idempotencyKey)
    }

    @Test
    fun normalizesCode() = runTest {
        val repository = ClaimRecordingRepository()

        useCase(repository).claim("fst_1", "abc234")

        assertEquals("ABC234", repository.received)
    }

    @Test
    fun invalidCode() = runTest {
        // iOS 인자 4종
        listOf("ABC23", "ABC2340", "ABCI23", "AB C23").forEach { code ->
            val repository = ClaimRecordingRepository()
            assertEquals(code, BearerError.InvalidRequest(), failure { useCase(repository).claim("fst_1", code) })
            assertNull(code, repository.received)
        }
    }

    @Test
    fun fetchFestival() = runTest {
        val pack =
            pack("""{"id":"fst_1","name":"원더리벳 2026","zones":[{"id":"zone_a","name":"리벳 스테이지 앞 왼쪽"},{"id":"zone_b"},{"name":"이름만"}]}""")

        val festival = useCase(pack = pack).fetchFestival("fst_1")

        assertEquals("원더리벳 2026", festival.name)
        assertEquals(listOf(BearerZone("zone_a", "리벳 스테이지 앞 왼쪽")), festival.zones)
        assertEquals("리벳 스테이지 앞 왼쪽", festival.zoneName("zone_a"))
        val empty = useCase(pack = null).fetchFestival("fst_2")
        assertTrue(empty.zones.isEmpty())
    }

    @Test
    fun fetchFestivalSets() = runTest {
        val pack = pack(
            """{"stages":[{"id":"stg_a","name":"리벳 스테이지"}],"timetable":[{"id":"set_2","artist":"실리카겔","stageId":"stg_a","startsAt":"2026-08-07T12:00:00.000Z","endsAt":"2026-08-07T13:00:00.000Z"},{"id":"set_1","artist":"새소년","stageName":"원더 스테이지","startsAt":"2026-08-07T10:00:00Z","endsAt":"2026-08-07T11:00:00Z"},{"id":"set_bad","artist":"시간 없음","startsAt":"2026-08-07T10:00:00Z"},{"id":"set_back","artist":"거꾸로","startsAt":"2026-08-07T11:00:00Z","endsAt":"2026-08-07T10:00:00Z"}]}""",
        )

        val festival = useCase(pack = pack).fetchFestival("fst_1")

        assertEquals(listOf("set_1", "set_2"), festival.sets.map { it.id })
        assertEquals(listOf("원더 스테이지", "리벳 스테이지"), festival.sets.map { it.stageName })
        assertEquals("새소년", festival.sets.first().artist)
        // timetable이 없는 예전 팩은 sets를 읽는다.
        val legacy = useCase(
            pack = pack("""{"sets":[{"id":"set_old","artist":"잔나비","startsAt":"2026-08-07T10:00:00Z","endsAt":"2026-08-07T11:00:00Z"}]}"""),
        ).fetchFestival("fst_1")
        assertEquals(listOf("set_old"), legacy.sets.map { it.id })
    }

    private fun pack(json: String) = CachedFestivalPack("fst_1", packVersion = 1, etag = "e", pack = json.encodeToByteArray())

    private fun useCase(
        repository: BearerRepository = ClaimRecordingRepository(),
        pack: CachedFestivalPack? = null,
        newUuid: () -> String = { "uuid" },
        clock: Clock = Clock.systemUTC(),
    ) = BearerUseCaseImpl(repository, StubPackSyncService(pack), newUuid, clock, outbox = UnusedOutbox())
}

/** 코드 인증만 기록하는 저장소. 나머지는 쓰지 않는다. iOS `ClaimRecordingRepository`. */
internal class ClaimRecordingRepository : BearerRepository {
    var received: String? = null

    override suspend fun claim(festivalId: String, code: String): BearerClaim {
        received = code
        return BearerClaim("flg_1", BearerFlagProfile("flg_1", "깃발"), isNew = true, coBearerIds = emptyList())
    }

    override suspend fun register(request: BearerRegisterRequest): BearerClaim = throw BearerError.Unavailable()

    override suspend fun fetchBearerFlags(festivalId: String?): List<BearerOwnedFlag> = throw BearerError.Unavailable()

    override suspend fun fetchPinnedFlag(): BearerPinnedFlag? = throw BearerError.Unavailable()

    override suspend fun fetchFollowedFlags(festivalId: String?): List<BearerFollowedFlag> = throw BearerError.Unavailable()

    override suspend fun fetchBoard(flagId: String): BearerBoard = throw BearerError.Unavailable()

    override suspend fun updateProfile(flagId: String, change: BearerProfileChange): BearerFlagProfile = throw BearerError.Unavailable()

    override suspend fun transferFestival(request: BearerFestivalTransferRequest): BearerFestivalTransfer = throw BearerError.Unavailable()

    override suspend fun uploadPhoto(
        flagId: String,
        jpeg: ByteArray,
        onProgress: (Double) -> Unit,
    ): BearerFlagPhoto = throw BearerError.Unavailable()

    override suspend fun sendStatus(flagId: String, request: BearerStatusRequest): BearerFlagStatus = throw BearerError.Unavailable()

    override suspend fun inviteCoBearer(flagId: String): BearerInvite = throw BearerError.Unavailable()

    override suspend fun removeCoBearer(flagId: String, userId: String) = throw BearerError.Unavailable()
}
