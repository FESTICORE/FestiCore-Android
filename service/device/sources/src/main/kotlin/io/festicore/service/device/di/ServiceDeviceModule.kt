package io.festicore.service.device.di

import io.festicore.service.device.DeviceInstallationService
import io.festicore.service.device.DeviceInstallationServiceImpl
import org.koin.dsl.module

val serviceDeviceModule = module {
    single<DeviceInstallationService> { DeviceInstallationServiceImpl(get()) }
}
