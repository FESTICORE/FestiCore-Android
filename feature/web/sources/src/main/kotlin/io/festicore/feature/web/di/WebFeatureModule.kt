package io.festicore.feature.web.di

import io.festicore.feature.web.WebViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val webFeatureModule: Module =
    module {
        viewModel { params -> WebViewModel(params.get()) }
    }
