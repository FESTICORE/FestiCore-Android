package io.festicore.service.device

import io.festicore.core.storage.SecureStorage
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 이 설치의 기기 ID(소문자 UUID). 처음 부를 때 만들어 [SecureStorage] 키 [DEVICE_ID_KEY]에 저장하고 이후 같은 값을 돌려준다.
 * 서버 등록은 하지 않는다. iOS `NetworkDeviceRegistrationService.installationDeviceID`.
 * 저장 실패는 `SecureStorageException`. 저장 값을 읽지 못하면(복호화 실패로 지워짐) 새로 만든다.
 */
class DeviceInstallationServiceImpl(private val storage: SecureStorage) : DeviceInstallationService {
    private val lock = Mutex()

    override suspend fun installationDeviceId(): String = lock.withLock {
        storage.read(DEVICE_ID_KEY) ?: UUID.randomUUID().toString().lowercase().also { storage.write(DEVICE_ID_KEY, it) }
    }

    companion object {
        /** iOS Keychain 키 `deviceID`와 같은 이름. */
        const val DEVICE_ID_KEY = "deviceID"
    }
}
