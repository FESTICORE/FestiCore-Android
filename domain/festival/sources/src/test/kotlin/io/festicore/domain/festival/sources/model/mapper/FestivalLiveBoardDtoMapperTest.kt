package io.festicore.domain.festival.sources.model.mapper

import io.festicore.core.network.FestiCoreJson
import io.festicore.domain.festival.FestivalCrewGatherResponse
import io.festicore.domain.festival.FestivalLiveBoardError
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.sources.model.dto.FestivalFlagsDto
import io.festicore.domain.festival.sources.model.dto.FestivalLiveBoardDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalLiveBoardMapperTests` + 깃발 병합 규칙. */
class FestivalLiveBoardDtoMapperTest {
    private fun board(
        activeFlags: String,
        me: String = "null",
        serverAt: String? = "2026-10-20T06:00:00Z",
    ) = FestiCoreJson.decodeFromString(
        FestivalLiveBoardDto.serializer(),
        """{"festivalId":"fst","activeFlags":[$activeFlags],"me":$me${serverAt?.let { ""","serverAt":"$it"""" } ?: ""}}""",
    )

    private fun profiles(items: String) = FestiCoreJson.decodeFromString(FestivalFlagsDto.serializer(), """{"items":[$items]}""").items

    private fun active(
        id: String,
        status: String = "active",
        updatedAt: String? = "2026-10-20T05:00:00Z",
    ) = """{"flagId":"$id","status":"$status","zoneId":"zon_1","activity":"slam"${updatedAt?.let { ""","updatedAt":"$it"""" } ?: ""}}"""

    private val image: (String?) -> String? = { it?.let { path -> "https://example.test$path" } }

    @Test
    fun `활성 깃발은 깃발 목록의 공개 관람 계획을 붙이고 없거나 형식이 틀리면 빈 배열이다`() {
        val snapshot = board("${active("flg_a")},${active("flg_b")},${active("flg_c")}").toDomain(
            profiles(
                """{"id":"flg_a","name":"A","plan":["set_1","set_2"]},{"id":"flg_b","name":"B"},{"id":"flg_c","name":"C","plan":"wrong"}""",
            ),
            imageUrl = { null },
        )

        assertEquals(listOf(listOf("set_1", "set_2"), emptyList(), emptyList()), snapshot.activeFlags.map { it.plannedSetIds })
    }

    @Test
    fun `활성 깃발에 프로필 이름 · 태그 · 썸네일을 합치고 프로필이 없으면 ID를 이름으로 쓴다`() {
        val received = Instant.parse("2026-10-20T06:00:01Z")
        val snapshot = board("${active("flg_a")},${active("flg_x")}").toDomain(
            profiles("""{"id":"flg_a","name":"가짜 깃발","thumbUrl":"/img/a.webp","tags":["slam"],"followerCount":3,"homeZoneId":"zon_h"}"""),
            image,
            received,
        )

        val first = snapshot.activeFlags[0]
        assertEquals("가짜 깃발", first.name)
        assertEquals("https://example.test/img/a.webp", first.thumbnailUrl)
        assertEquals(listOf("slam"), first.tags)
        assertEquals(3, first.followerCount)
        assertEquals("zon_h", first.homeZoneId)
        assertEquals(Instant.parse("2026-10-20T05:00:00Z"), first.updatedAt)
        assertEquals("flg_x", snapshot.activeFlags[1].name)
        assertNull(snapshot.activeFlags[1].thumbnailUrl)
        assertEquals(Instant.parse("2026-10-20T06:00:00Z"), snapshot.serverAt)
        assertEquals(received, snapshot.receivedAt)
    }

    @Test
    fun `공개 목록은 프로필 순서에 라이브 상태를 합치고 빠진 깃발은 상한 미만이면 scheduled다`() {
        val snapshot = board(active("flg_b", status = "resting")).toDomain(
            profiles("""{"id":"flg_a","name":"A"},{"id":"flg_b","name":"B"},{"id":"flg_a","name":"중복"}"""),
            image,
        )

        assertEquals(listOf("flg_a", "flg_b"), snapshot.directoryFlags.map { it.id })
        assertEquals(listOf("scheduled", "resting"), snapshot.directoryFlags.map { it.status })
        assertNull(snapshot.directoryFlags[0].updatedAt)
    }

    @Test
    fun `활성 목록이 상한 50개면 빠진 깃발 상태는 unknown이다`() {
        val flags = (1..50).joinToString(",") { active("flg_$it") }

        val snapshot = board(flags).toDomain(profiles("""{"id":"flg_z","name":"Z"}"""), image)

        assertEquals("unknown", snapshot.directoryFlags.single { it.id == "flg_z" }.status)
    }

    @Test
    fun `팔로우는 활성 깃발을 그대로 쓰고 없으면 프로필로 만든다`() {
        val snapshot = board(
            active("flg_a"),
            me = """{"following":["flg_a","flg_b"],"pinnedFlagId":"flg_a","bearerOf":["flg_b"]}""",
        ).toDomain(profiles("""{"id":"flg_b","name":"B"}"""), image)

        assertEquals(snapshot.activeFlags[0], snapshot.followedFlags[0])
        assertEquals(FestivalLiveBoardSnapshot.Flag("flg_b", "B", "unknown", null, null, null), snapshot.followedFlags[1])
        assertEquals("flg_a", snapshot.pinnedFlagId)
        assertEquals(listOf("flg_b"), snapshot.bearerFlagIds)
    }

    @Test
    fun `serverAt이 없거나 형식이 틀리면 잘못된 응답이다`() {
        assertThrows(FestivalLiveBoardError.InvalidResponse::class.java) { board("", serverAt = null).toDomain(emptyList(), image) }
        assertThrows(FestivalLiveBoardError.InvalidResponse::class.java) {
            board("", serverAt = "2026-10-20T06:00:00").toDomain(emptyList(), image)
        }
    }

    @Test
    fun `활성 깃발의 updatedAt이 없으면 잘못된 응답이다`() {
        assertThrows(FestivalLiveBoardError.InvalidResponse::class.java) {
            board(active("flg_a", updatedAt = null)).toDomain(emptyList(), image)
        }
    }

    @Test
    fun `크루 모여 응답은 모르는 값과 틀린 날짜만 버리고 요약이 없으면 센다`() {
        val crew = """{"id":"crw","name":"가짜 크루","ownerId":"u1","gatherAvailableAt":"2026-10-20T06:10:00Z",
            "members":[{"userId":"u1","nickname":"가","state":"here","zoneId":"zon_1","updatedAt":"2026-10-20T05:59:00Z"}],
            "gatherRequest":{"id":"g1","zoneId":"zon_1","byUserId":"u1","at":"2026-10-20T06:00:00Z","responses":[
              {"userId":"u2","nickname":"나","response":"going","respondedAt":"2026-10-20T06:01:00Z"},
              {"userId":"u3","nickname":"다","response":"maybe","respondedAt":"2026-10-20T06:01:00Z"},
              {"userId":"u4","nickname":"라","response":"ack","respondedAt":"bad"}]}}"""

        val result = board("", me = """{"following":[],"crew":$crew}""").toDomain(emptyList(), image).crew

        val gather = result?.gatherRequest
        assertEquals("가짜 크루", result?.name)
        assertEquals(Instant.parse("2026-10-20T05:59:00Z"), result?.members?.single()?.updatedAt)
        assertEquals(listOf(FestivalCrewGatherResponse.GOING), gather?.responses?.map { it.response })
        assertEquals(FestivalLiveBoardSnapshot.Crew.GatherRequest.Summary(going = 1, ack = 0), gather?.summary)
        assertEquals(Instant.parse("2026-10-20T06:10:00Z"), result?.gatherAvailableAt)
    }

    @Test
    fun `모여 시각이 틀리면 모여 요청은 없다`() {
        val crew = """{"id":"crw","name":"C","members":[],""" +
            """"gatherRequest":{"byUserId":"u1","at":"bad","responseSummary":{"going":2,"ack":1}}}"""

        val result = board("", me = """{"following":[],"crew":$crew}""").toDomain(emptyList(), image).crew

        assertNull(result?.gatherRequest)
        assertTrue(result?.members?.isEmpty() == true)
    }
}
