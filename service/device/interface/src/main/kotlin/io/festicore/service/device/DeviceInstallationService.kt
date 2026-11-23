package io.festicore.service.device

/** 이 설치의 기기 ID(소문자 UUID). 구현은 `DeviceInstallationServiceImpl`. iOS Device Interface. */
interface DeviceInstallationService {
    suspend fun installationDeviceId(): String
}
