package io.festicore.service.device.di

import android.content.Context
import io.festicore.service.device.DeviceInstallationService
import io.festicore.service.device.DeviceInstallationServiceImpl
import io.festicore.service.device.DeviceNotificationSettingsService
import io.festicore.service.device.DeviceNotificationSettingsServiceImpl
import io.festicore.service.device.deviceRegistrationMetadata
import org.koin.dsl.module

val serviceDeviceModule = module {
    single<DeviceNotificationSettingsService> {
        val context = get<Context>()
        DeviceNotificationSettingsServiceImpl(get(), get(), get()) { deviceRegistrationMetadata(context) }
    }
    single<DeviceInstallationService> { DeviceInstallationServiceImpl(get()) }
}
