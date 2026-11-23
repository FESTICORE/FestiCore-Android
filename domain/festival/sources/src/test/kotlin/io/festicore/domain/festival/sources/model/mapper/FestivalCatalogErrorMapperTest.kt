package io.festicore.domain.festival.sources.model.mapper

import io.festicore.core.network.NetworkError
import io.festicore.domain.festival.FestivalCatalogError
import io.festicore.domain.festival.sources.service.FestivalCatalogServiceError
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalCatalogErrorMapperTest {
    @Test
    fun `연결 실패 · 시간 초과는 Offline이다`() {
        assertTrue(NetworkError.Offline(IOException()).toFestivalCatalogError() is FestivalCatalogError.Offline)
        assertTrue(NetworkError.Timeout(IOException()).toFestivalCatalogError() is FestivalCatalogError.Offline)
    }

    @Test
    fun `서버 · 응답 · 해석 오류는 Unavailable이다`() {
        listOf(
            NetworkError.Http(400, "VALIDATION", null),
            NetworkError.InvalidResponse("FAIL", "x"),
            NetworkError.Decoding(IllegalArgumentException()),
            NetworkError.Unknown(IllegalStateException()),
            FestivalCatalogServiceError.CacheWriteFailed(IOException()),
        ).forEach { assertTrue(it.toFestivalCatalogError() is FestivalCatalogError.Unavailable) }
    }

    @Test
    fun `커서 반복 · 쪽 상한 · 캐시 읽기 실패는 같은 종류로 바꾼다`() {
        assertTrue(FestivalCatalogServiceError.RepeatedCursor().toFestivalCatalogError() is FestivalCatalogError.RepeatedCursor)
        assertTrue(FestivalCatalogServiceError.TooManyPages().toFestivalCatalogError() is FestivalCatalogError.TooManyPages)
        assertTrue(
            FestivalCatalogServiceError.CacheUnreadable(IOException()).toFestivalCatalogError() is FestivalCatalogError.CacheUnreadable,
        )
    }

    @Test
    fun `취소는 그대로 둔다`() {
        val cancel = CancellationException()

        assertSame(cancel, cancel.toFestivalCatalogError())
    }
}
