package io.festicore.service.festivalcatalog.storage

import android.util.AtomicFile
import io.festicore.domain.festival.sources.model.dto.FestivalItemDto
import io.festicore.domain.festival.sources.service.FestivalCatalogServiceError
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 축제 목록 JSON 파일 캐시(`List<FestivalItemDto>`). 쓰기는 [AtomicFile]로 통째로 바꾼다. iOS `FestivalCatalogCache`.
 * 파일이 없으면 빈 목록, 읽기 · 해석 실패는 [FestivalCatalogServiceError.CacheUnreadable].
 */
class FestivalCatalogCache(
    file: File,
    private val json: Json,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val atomicFile = AtomicFile(file)
    private val serializer = ListSerializer(FestivalItemDto.serializer())

    suspend fun load(): List<FestivalItemDto> = withContext(io) {
        val bytes = try {
            atomicFile.readFully()
        } catch (_: FileNotFoundException) {
            return@withContext emptyList()
        } catch (e: IOException) {
            throw FestivalCatalogServiceError.CacheUnreadable(e)
        }
        try {
            json.decodeFromString(serializer, bytes.decodeToString())
        } catch (e: IllegalArgumentException) { // SerializationException 포함
            throw FestivalCatalogServiceError.CacheUnreadable(e)
        }
    }

    suspend fun save(items: List<FestivalItemDto>) = withContext(io) {
        val bytes = json.encodeToString(serializer, items).encodeToByteArray()
        try {
            atomicFile.baseFile.parentFile?.mkdirs()
            val out = atomicFile.startWrite()
            try {
                out.write(bytes)
                atomicFile.finishWrite(out)
            } catch (e: IOException) {
                atomicFile.failWrite(out)
                throw e
            }
        } catch (e: IOException) {
            throw FestivalCatalogServiceError.CacheWriteFailed(e)
        }
    }
}
