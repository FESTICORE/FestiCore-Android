package io.festicore.domain.festival.sources.di

import io.festicore.domain.festival.FestivalCatalogRepository
import io.festicore.domain.festival.FestivalCatalogUseCase
import io.festicore.domain.festival.FestivalLibraryUseCase
import io.festicore.domain.festival.sources.FestivalCatalogRepositoryImpl
import io.festicore.domain.festival.sources.FestivalCatalogUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLibraryUseCaseImpl
import org.koin.dsl.module

val domainFestivalModule = module {
    single<FestivalCatalogRepository> { FestivalCatalogRepositoryImpl(get()) }
    factory<FestivalCatalogUseCase> { FestivalCatalogUseCaseImpl(get()) }
    factory<FestivalLibraryUseCase> { FestivalLibraryUseCaseImpl(get()) }
}
