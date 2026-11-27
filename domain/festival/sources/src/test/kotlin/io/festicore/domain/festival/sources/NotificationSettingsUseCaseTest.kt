package io.festicore.domain.festival.sources

import io.festicore.domain.festival.notificationsettings.NotificationInvalidRegistration
import io.festicore.domain.festival.notificationsettings.NotificationRegistrationUnavailable
import io.festicore.domain.festival.notificationsettings.NotificationSetting
import io.festicore.domain.festival.sources.notificationsettings.FestivalNotificationSettingsUseCaseImpl
import io.festicore.service.device.DeviceInvalidRegistration
import io.festicore.service.device.DeviceNotificationSettingsService
import io.festicore.service.device.DeviceRegistrationUnavailable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsUseCaseTest {
    @Test fun mapsRegistrationErrorsAndPreservesPartialUpdates() = runTest {
        var error: Exception? = null
        var patch = emptyMap<String, Boolean>()
        val service = object : DeviceNotificationSettingsService {
            override suspend fun load(): Map<String, Boolean> {
                error?.let { throw it }
                return mapOf("flagStatus" to true)
            }
            override suspend fun save(changes: Map<String, Boolean>): Map<String, Boolean> {
                patch = changes
                return changes
            }
        }
        val useCase = FestivalNotificationSettingsUseCaseImpl(service)
        assertEquals(mapOf(NotificationSetting.FlagStatus to true), useCase.load())
        useCase.save(mapOf(NotificationSetting.OfficialNews to false))
        assertEquals(mapOf("officialNews" to false), patch)
        error = DeviceRegistrationUnavailable()
        assertTrue(runCatching { useCase.load() }.exceptionOrNull() is NotificationRegistrationUnavailable)
        error = DeviceInvalidRegistration()
        assertTrue(runCatching { useCase.load() }.exceptionOrNull() is NotificationInvalidRegistration)
        error = kotlinx.coroutines.CancellationException("cancel")
        assertTrue(runCatching { useCase.load() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
}
