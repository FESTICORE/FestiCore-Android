package io.festicore.domain.festival.sources.notificationsettings
import io.festicore.domain.festival.notificationsettings.FestivalNotificationSettingsUseCase
import io.festicore.domain.festival.notificationsettings.NotificationInvalidRegistration
import io.festicore.domain.festival.notificationsettings.NotificationRegistrationUnavailable
import io.festicore.domain.festival.notificationsettings.NotificationSetting
import io.festicore.service.device.DeviceInvalidRegistration
import io.festicore.service.device.DeviceNotificationSettingsService
import io.festicore.service.device.DeviceRegistrationUnavailable
class FestivalNotificationSettingsUseCaseImpl(private val service: DeviceNotificationSettingsService) :
    FestivalNotificationSettingsUseCase {
    override suspend fun load() = mapError { decode(service.load()) }
    override suspend fun save(changes: Map<NotificationSetting, Boolean>) =
        mapError { decode(service.save(changes.mapKeys { keys.getValue(it.key) })) }
    private val keys = mapOf(
        NotificationSetting.FlagStatus to "flagStatus",
        NotificationSetting.CrewStatus to "crewStatus",
        NotificationSetting.CrewGather to "crewGather",
        NotificationSetting.SetReminders to "setReminders",
        NotificationSetting.Homecoming to "homecoming",
        NotificationSetting.PackUpdates to "packUpdates",
        NotificationSetting.OfficialNews to "officialNews",
    )
    private fun decode(values: Map<String, Boolean>) = keys.mapNotNull { (kind, key) -> values[key]?.let { kind to it } }.toMap()
    private suspend fun <T> mapError(block: suspend () -> T): T = try {
        block()
    } catch (_: DeviceInvalidRegistration) {
        throw NotificationInvalidRegistration()
    } catch (_: DeviceRegistrationUnavailable) {
        throw NotificationRegistrationUnavailable()
    }
}
