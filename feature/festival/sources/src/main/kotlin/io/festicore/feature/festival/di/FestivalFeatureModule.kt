package io.festicore.feature.festival.di

import io.festicore.feature.festival.FestivalCoordinator
import io.festicore.feature.festival.FestivalListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val festivalFeatureModule = module {
    viewModelOf(::FestivalCoordinator)
    viewModelOf(::FestivalListViewModel)
}
