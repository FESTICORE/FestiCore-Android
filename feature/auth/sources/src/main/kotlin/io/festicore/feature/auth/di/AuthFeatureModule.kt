package io.festicore.feature.auth.di

import io.festicore.feature.auth.AuthCoordinator
import io.festicore.feature.auth.LaunchLoginViewModel
import io.festicore.feature.auth.LegalDocumentUrls
import io.festicore.feature.auth.SignUpViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** [legalBaseUrl]은 app이 `BuildConfig.LEGAL_BASE_URL`로 넘긴다. */
fun authFeatureModule(legalBaseUrl: String): Module = module {
    single { LegalDocumentUrls(legalBaseUrl) }
    viewModel { params -> AuthCoordinator(params.get()) }
    viewModelOf(::LaunchLoginViewModel)
    viewModel { params -> SignUpViewModel(params.get(), get()) }
}
