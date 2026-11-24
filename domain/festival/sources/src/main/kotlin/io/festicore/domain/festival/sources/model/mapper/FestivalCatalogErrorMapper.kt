package io.festicore.domain.festival.sources.model.mapper

import io.festicore.core.network.error.NetworkError
import io.festicore.domain.festival.entity.FestivalCatalogError
import io.festicore.domain.festival.sources.service.FestivalCatalogServiceError
import kotlin.coroutines.cancellation.CancellationException

/** 네트워크 · 카탈로그 서비스 오류를 [FestivalCatalogError]로 바꾼다. 취소는 그대로 둔다. */
internal fun Throwable.toFestivalCatalogError(): Throwable = when (this) {
    is CancellationException, is FestivalCatalogError -> this
    is NetworkError.Offline, is NetworkError.Timeout -> FestivalCatalogError.Offline()
    is FestivalCatalogServiceError.RepeatedCursor -> FestivalCatalogError.RepeatedCursor()
    is FestivalCatalogServiceError.TooManyPages -> FestivalCatalogError.TooManyPages()
    is FestivalCatalogServiceError.CacheUnreadable -> FestivalCatalogError.CacheUnreadable()
    else -> FestivalCatalogError.Unavailable()
}

internal inline fun <T> mapCatalogErrors(block: () -> T): T = try {
    block()
} catch (e: Exception) {
    throw e.toFestivalCatalogError()
}
