package io.festicore.service.device.di

import io.festicore.service.device.DeviceInstallationService
import org.koin.dsl.module

val serviceDeviceModule = module {
    single { DeviceInstallationService(get()) }
}
