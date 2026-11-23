package io.festicore.feature.auth.di

import io.festicore.feature.auth.AuthCoordinator
import io.festicore.feature.auth.LaunchLoginViewModel
import io.festicore.feature.auth.SignUpViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** 약관 주소(`LegalDocumentUrls`)는 `webFeatureModule`이 등록한다. */
val authFeatureModule: Module = module {
    viewModel { params -> AuthCoordinator(params.get()) }
    viewModelOf(::LaunchLoginViewModel)
    viewModel { params -> SignUpViewModel(params.get(), get()) }
}
