package io.festicore.service.pack

import android.app.Application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PackCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000L
    private val directory: File get() = File(folder.root, "festicore/packs")
    private val cache by lazy { PackCache(directory, io = Dispatchers.Unconfined, now = { clock }) }

    private fun pack(
        version: Int,
        id: String = FESTIVAL_ID,
        etag: String = "\"v$version\"",
    ) = CachedFestivalPack(id, version, etag, packBytes(version, id))

    @Test
    fun `저장한 팩을 확인 시각과 함께 그대로 읽는다`() = runTest {
        val saved = cache.save(pack(1))

        val loaded = PackCache(directory, io = Dispatchers.Unconfined).load(FESTIVAL_ID)

        assertEquals(saved, loaded)
        assertArrayEquals(packBytes(1), loaded?.pack)
        assertEquals(1_000L, loaded?.checkedAtMillis)
        assertTrue(File(directory, "$FESTIVAL_ID.json").readText().contains("\"festivalID\":\"$FESTIVAL_ID\""))
    }

    @Test
    fun `폴더가 없으면 목록은 비고 없는 팩은 null이다`() = runTest {
        assertTrue(cache.loadAll().isEmpty())
        assertNull(cache.load(FESTIVAL_ID))
    }

    @Test
    fun `목록은 검증된 팩만 축제 ID 순으로 돌려주고 손상 파일 하나가 나머지를 지우지 않는다`() = runTest {
        cache.save(pack(1, id = "fst_b"))
        cache.save(pack(2, id = "fst_a"))
        File(directory, "fst_broken.json").writeText("{not json")
        File(directory, "bad id!.json").writeText("{}")
        File(directory, "notes.txt").writeText("x")

        assertEquals(listOf("fst_a", "fst_b"), cache.loadAll().map { it.festivalId })
    }

    @Test
    fun `손상 파일은 읽기에서 InvalidPack이고 다음 저장이 덮어쓴다`() = runTest {
        cache.save(pack(1))
        val file = File(directory, "$FESTIVAL_ID.json")
        file.writeText(file.readText().replace(PackWire.sha256Hex(packBytes(1)), "0".repeat(64)))

        assertThrowsSuspend<PackSyncError.InvalidPack> { cache.load(FESTIVAL_ID) }
        assertNull(cache.loadIntact(FESTIVAL_ID))

        cache.save(pack(1))
        assertEquals(1, cache.load(FESTIVAL_ID)?.packVersion)
    }

    @Test
    fun `파일 안 축제 ID가 파일 이름과 다르면 손상으로 본다`() = runTest {
        cache.save(pack(1, id = "fst_other"))
        File(directory, "fst_other.json").copyTo(File(directory, "$FESTIVAL_ID.json"))

        assertThrowsSuspend<PackSyncError.InvalidPack> { cache.load(FESTIVAL_ID) }
    }

    @Test
    fun `더 낮은 버전으로 덮어쓰지 않는다`() = runTest {
        cache.save(pack(3))

        val result = cache.save(pack(2))

        assertEquals(3, result.packVersion)
        assertEquals(3, cache.load(FESTIVAL_ID)?.packVersion)
    }

    @Test
    fun `8MB를 넘는 팩 · 틀린 팩 · 틀린 축제 ID는 저장하지 않는다`() = runTest {
        val big = CachedFestivalPack(FESTIVAL_ID, 1, "\"e\"", packBytes(1, padding = PackWire.MAX_PACK_BYTES))
        assertThrowsSuspend<PackSyncError.PackTooLarge> { cache.save(big) }
        assertThrowsSuspend<PackSyncError.InvalidPack> { cache.save(CachedFestivalPack(FESTIVAL_ID, 2, "\"e\"", packBytes(1))) }
        assertThrowsSuspend<PackSyncError.InvalidPack> { cache.save(pack(1, etag = "")) }
        assertThrowsSuspend<PackSyncError.InvalidFestivalId> { cache.save(pack(1, id = "../escape")) }
        assertThrowsSuspend<PackSyncError.InvalidFestivalId> { cache.load("a".repeat(129)) }
    }

    @Test
    fun `확인 시각을 갱신하고 지우면 목록에서 사라진다`() = runTest {
        cache.save(pack(1))
        clock = 5_000L

        cache.markChecked(FESTIVAL_ID)
        assertEquals(5_000L, cache.load(FESTIVAL_ID)?.checkedAtMillis)

        cache.delete(FESTIVAL_ID)
        assertNull(cache.load(FESTIVAL_ID))
        assertTrue(cache.loadAll().isEmpty())
        cache.delete(FESTIVAL_ID) // 없어도 오류 없음
    }
}
