package io.festicore.feature.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `LegalDocumentURLTests` 2건(같은 이름)과 stage 문서 주소. */
class LegalDocumentUrlsTest {
    @Test
    fun `stage 약관 상세 주소`() {
        val urls = LegalDocumentUrls("https://festicore-dev.up.railway.app/api/v1/legal/")
        assertEquals("https://festicore-dev.up.railway.app/api/v1/legal/terms", urls.url("terms"))
        assertEquals("https://festicore-dev.up.railway.app/api/v1/legal/privacy", urls.url("privacy"))
        assertEquals("https://festicore-dev.up.railway.app/api/v1/legal/location", urls.url("location"))
        assertEquals("https://festicore-dev.up.railway.app/api/v1/legal/sources", urls.url("sources"))
    }

    @Test
    fun readsHTTPSBaseURLFromInfoPlist() {
        assertEquals("https://festicore.app/api/v1/legal/terms", LegalDocumentUrls("https://festicore.app/api/v1/legal/").url("terms"))
    }

    @Test
    fun missingOrInvalidValueIsNil() {
        assertNull(LegalDocumentUrls("").url("terms"))
        // 빌드 설정에 API 호스트가 비어 있으면 호스트 없는 주소가 된다.
        assertNull(LegalDocumentUrls("https:///api/v1/legal/").url("terms"))
        assertNull(LegalDocumentUrls("http://festicore.app/api/v1/legal/").url("terms"))
        assertNull(LegalDocumentUrls("not a url").url("terms"))
    }
}
