package io.festicore.service.pack

import android.util.AtomicFile
import io.festicore.core.network.FestiCoreJson
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 받은 팩 파일 저장소. 축제마다 `{directory}/{festivalId}.json` 하나를 [AtomicFile]로 통째로 바꾼다.
 * 형식은 iOS `PackCache` 파일과 같고(`pack`은 base64), 확인 시각은 같은 JSON의 `checkedAt`(epoch ms)에 둔다(설계 D2).
 * 읽을 때 sha256 · 팩 검증을 하고, 손상 파일은 [PackSyncError.InvalidPack]. 파일 · 폴더 접근 오류는 `IOException` 그대로 던진다.
 */
class PackCache(
    private val directory: File,
    private val json: Json = FestiCoreJson,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(festivalId: String): CachedFestivalPack? = withContext(io) { read(festivalId) }

    /** [load]와 같지만 손상 파일은 없는 것으로 본다. 다시 받아 덮어쓸 때 쓴다. */
    suspend fun loadIntact(festivalId: String): CachedFestivalPack? = withContext(io) { readIntact(festivalId) }

    /** 검증을 통과한 팩 목록(축제 ID 순). 손상 파일 · 이름이 틀린 파일 · 링크 · 16MB 넘는 파일은 건너뛴다. */
    suspend fun loadAll(): List<CachedFestivalPack> = withContext(io) {
        if (!directory.exists()) return@withContext emptyList()
        val files = directory.listFiles() ?: throw IOException("팩 폴더를 읽지 못했습니다")
        files.asSequence()
            .filter { it.name.endsWith(FILE_SUFFIX) && !it.name.startsWith(".") }
            .filter { Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) && it.length() <= MAX_FILE_BYTES }
            .mapNotNull { file ->
                try {
                    read(file.name.removeSuffix(FILE_SUFFIX))
                } catch (_: PackSyncError.InvalidPack) {
                    null
                } catch (_: PackSyncError.InvalidFestivalId) {
                    null
                }
            }
            .sortedBy { it.festivalId }
            .toList()
    }

    /**
     * 검증한 뒤 저장하고 확인 시각을 지금으로 둔 값을 돌려준다. 저장된 팩이 더 높은 버전이면 덮지 않고 그 팩을 돌려준다.
     * 8MB를 넘으면 [PackSyncError.PackTooLarge], 팩 · ETag가 틀리면 [PackSyncError.InvalidPack]. 손상된 이전 파일은 덮어쓴다.
     */
    suspend fun save(value: CachedFestivalPack): CachedFestivalPack = withContext(io) {
        if (value.pack.size > PackWire.MAX_PACK_BYTES) throw PackSyncError.PackTooLarge()
        if (!PackWire.isValidPack(value.pack, value.festivalId, value.packVersion) || !PackWire.isValidEtag(value.etag)) {
            throw PackSyncError.InvalidPack()
        }
        val previous = readIntact(value.festivalId)
        if (previous != null && previous.packVersion > value.packVersion) return@withContext previous
        value.withCheckedAt(now()).also(::write)
    }

    /** 서버가 같은 버전이라고 답하면 확인 시각을 지금으로 바꾼다. 저장된 팩이 없으면 아무것도 하지 않는다. */
    suspend fun markChecked(festivalId: String) = withContext(io) {
        read(festivalId)?.let { write(it.withCheckedAt(now())) }
    }

    /** 없으면 아무것도 하지 않는다. */
    suspend fun delete(festivalId: String) = withContext(io) {
        val file = fileFor(festivalId)
        AtomicFile(file).delete()
        if (file.exists()) throw IOException("팩 파일을 지우지 못했습니다")
    }

    private fun readIntact(festivalId: String): CachedFestivalPack? = try {
        read(festivalId)
    } catch (_: PackSyncError.InvalidPack) {
        null
    }

    private fun read(festivalId: String): CachedFestivalPack? {
        val bytes = try {
            AtomicFile(fileFor(festivalId)).readFully()
        } catch (_: FileNotFoundException) {
            return null
        }
        if (bytes.size > MAX_FILE_BYTES) throw PackSyncError.InvalidPack()
        val stored = try {
            json.decodeFromString(StoredPack.serializer(), bytes.decodeToString())
        } catch (_: IllegalArgumentException) { // SerializationException 포함
            throw PackSyncError.InvalidPack()
        }
        val pack = try {
            Base64.getDecoder().decode(stored.pack)
        } catch (_: IllegalArgumentException) {
            throw PackSyncError.InvalidPack()
        }
        val valid = stored.festivalID == festivalId &&
            pack.size <= PackWire.MAX_PACK_BYTES &&
            PackWire.isValidPack(pack, festivalId, stored.packVersion) &&
            PackWire.isValidEtag(stored.etag) &&
            PackWire.sha256Hex(pack) == stored.sha256
        if (!valid) throw PackSyncError.InvalidPack()
        return CachedFestivalPack(festivalId, stored.packVersion, stored.etag, pack, stored.checkedAt)
    }

    private fun write(value: CachedFestivalPack) {
        val stored = StoredPack(
            festivalID = value.festivalId,
            packVersion = value.packVersion,
            etag = value.etag,
            pack = Base64.getEncoder().encodeToString(value.pack),
            sha256 = PackWire.sha256Hex(value.pack),
            checkedAt = value.checkedAtMillis,
        )
        val bytes = json.encodeToString(StoredPack.serializer(), stored).encodeToByteArray()
        directory.mkdirs()
        val file = AtomicFile(fileFor(value.festivalId))
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    private fun fileFor(festivalId: String): File {
        if (!isValidFestivalId(festivalId)) throw PackSyncError.InvalidFestivalId()
        return File(directory, festivalId + FILE_SUFFIX)
    }

    /** iOS 파일과 같은 키. `pack`은 base64 문자열. */
    @Serializable
    private data class StoredPack(
        val festivalID: String,
        val packVersion: Int,
        val etag: String,
        val pack: String,
        val sha256: String,
        val checkedAt: Long? = null,
    )

    private companion object {
        const val FILE_SUFFIX = ".json"

        /** base64 + 봉투를 감안한 파일 상한(iOS `maxBytes * 2`). */
        const val MAX_FILE_BYTES = PackWire.MAX_PACK_BYTES * 2L
    }
}

/** 축제 ID는 `[A-Za-z0-9_-]`, 1~128자. 파일 이름과 요청에 그대로 쓴다. */
internal fun isValidFestivalId(id: String): Boolean =
    id.isNotEmpty() && id.length <= 128 && id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
