package io.festicore.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegalDocumentUrlsTest {
    private val urls = LegalDocumentUrls("https://festicore-dev.up.railway.app/api/v1/legal/")

    @Test
    fun `stage 약관 상세 주소 세 개`() {
        assertEquals("https://festicore-dev.up.railway.app/api/v1/legal/terms", urls.url(LegalDocument.Terms).toString())
        assertEquals("https://festicore-dev.up.railway.app/api/v1/legal/privacy", urls.url(LegalDocument.Privacy).toString())
        assertEquals("https://festicore-dev.up.railway.app/api/v1/legal/location", urls.url(LegalDocument.Location).toString())
    }
}
