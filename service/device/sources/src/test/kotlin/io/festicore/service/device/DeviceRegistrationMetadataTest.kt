package io.festicore.service.device

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRegistrationMetadataTest {
    @Test fun stripsExtensionsAndVariantsFromRegistrationLocale() {
        assertEquals("ko-KR", registrationLocale(Locale.forLanguageTag("ko-KR-u-ca-gregory-x-private")))
        assertEquals("zh-Hant-TW", registrationLocale(Locale.forLanguageTag("zh-Hant-TW-x-private")))
        assertEquals("en-US", registrationLocale(Locale.forLanguageTag("en-US-POSIX")))
        assertEquals("en", registrationLocale(Locale.ROOT))
        @Suppress("DEPRECATION")
        val malformed = Locale("ko", "INVALID_REGION")
        assertEquals("ko", registrationLocale(malformed))
    }
}
