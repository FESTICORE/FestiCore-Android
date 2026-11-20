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
}
