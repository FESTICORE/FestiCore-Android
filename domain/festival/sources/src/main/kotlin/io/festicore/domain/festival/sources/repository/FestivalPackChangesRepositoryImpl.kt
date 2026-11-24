package io.festicore.domain.festival.sources.repository

import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.packdetail.FestivalPackChangesRepository
import io.festicore.domain.festival.sources.model.dto.FestivalPackChangesDto
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.service.apiendpoint.festival.FestivalPackRequest
import kotlin.coroutines.cancellation.CancellationException

/** `GET festivals/{id}/pack/changes`. 네트워크 실패는 [toPackError]로 바꾼다(전송 실패 → Offline). iOS `FestivalPackRepositoryImpl`. */
internal class FestivalPackChangesRepositoryImpl(private val api: FestivalPackRequest) : FestivalPackChangesRepository {
    override suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges = try {
        api.packChanges(festivalId, fromVersion, FestivalPackChangesDto.serializer()).toDomain()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toPackError()
    }
}
