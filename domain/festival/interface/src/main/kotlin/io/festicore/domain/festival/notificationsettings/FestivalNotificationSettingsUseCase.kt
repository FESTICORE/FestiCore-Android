package io.festicore.domain.festival.notificationsettings
interface FestivalNotificationSettingsUseCase {
    suspend fun load(): Map<NotificationSetting, Boolean>
    suspend fun save(changes: Map<NotificationSetting, Boolean>): Map<NotificationSetting, Boolean>
}
class NotificationRegistrationUnavailable : Exception("기기 알림 설정을 사용할 수 없습니다")

class NotificationInvalidRegistration : Exception("기기 등록 정보가 올바르지 않습니다")

enum class NotificationSetting { FlagStatus, CrewStatus, CrewGather, SetReminders, Homecoming, PackUpdates, OfficialNews }
