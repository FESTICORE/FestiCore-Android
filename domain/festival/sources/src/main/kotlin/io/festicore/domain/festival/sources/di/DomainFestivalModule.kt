package io.festicore.domain.festival.sources.di

import io.festicore.domain.festival.FestivalCatalogRepository
import io.festicore.domain.festival.FestivalCatalogUseCase
import io.festicore.domain.festival.FestivalLibraryUseCase
import io.festicore.domain.festival.FestivalLiveBoardRepository
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import io.festicore.domain.festival.FestivalScheduleRepository
import io.festicore.domain.festival.FestivalScheduleUseCase
import io.festicore.domain.festival.sources.FestivalCatalogRepositoryImpl
import io.festicore.domain.festival.sources.FestivalCatalogUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLibraryUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLiveBoardRepositoryImpl
import io.festicore.domain.festival.sources.FestivalLiveBoardUseCaseImpl
import io.festicore.domain.festival.sources.FestivalScheduleRepositoryImpl
import io.festicore.domain.festival.sources.FestivalScheduleUseCaseImpl
import org.koin.dsl.module

val domainFestivalModule = module {
    single<FestivalCatalogRepository> { FestivalCatalogRepositoryImpl(get()) }
    factory<FestivalCatalogUseCase> { FestivalCatalogUseCaseImpl(get()) }
    factory<FestivalLibraryUseCase> { FestivalLibraryUseCaseImpl(get()) }
    single<FestivalLiveBoardRepository> { FestivalLiveBoardRepositoryImpl(get(), get(), get()) }
    factory<FestivalLiveBoardUseCase> { FestivalLiveBoardUseCaseImpl(get()) }
    single<FestivalScheduleRepository> { FestivalScheduleRepositoryImpl(get()) }
    factory<FestivalScheduleUseCase> { FestivalScheduleUseCaseImpl(get()) }
}
