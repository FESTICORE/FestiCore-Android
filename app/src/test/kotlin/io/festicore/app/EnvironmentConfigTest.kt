package io.festicore.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentConfigTest {
    @Test
    fun `flavor와 IS_STAGE가 일치한다`() {
        assertEquals(BuildConfig.FLAVOR == "stage", BuildConfig.IS_STAGE)
    }

    @Test
    fun `API 주소는 https로 시작하고 슬래시로 끝난다`() {
        assertTrue(BuildConfig.BASE_URL, BuildConfig.BASE_URL.startsWith("https://"))
        assertTrue(BuildConfig.BASE_URL, BuildConfig.BASE_URL.endsWith("/"))
    }

    @Test
    fun `Google 서버 클라이언트 ID는 웹 OAuth 클라이언트 형식이다`() {
        val id = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        assertTrue(id, Regex("""^\d+-[a-z0-9]+\.apps\.googleusercontent\.com$""").matches(id))
    }

    @Test
    fun `약관 주소는 API 주소 아래 api v1 legal 이고 슬래시로 끝난다`() {
        assertEquals(BuildConfig.BASE_URL + "api/v1/legal/", BuildConfig.LEGAL_BASE_URL)
    }
}
