package io.festicore.service.device

interface DeviceNotificationSettingsService {
    suspend fun load(): Map<String, Boolean>
    suspend fun save(changes: Map<String, Boolean>): Map<String, Boolean>
}
class DeviceRegistrationUnavailable : Exception("기기 설정을 확인할 수 없습니다")

class DeviceInvalidRegistration : Exception("기기 등록 정보가 올바르지 않습니다")
