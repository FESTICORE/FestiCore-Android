package io.festicore.domain.festival.sources.di

import io.festicore.domain.festival.FestivalCatalogRepository
import io.festicore.domain.festival.FestivalCatalogUseCase
import io.festicore.domain.festival.FestivalLibraryUseCase
import io.festicore.domain.festival.FestivalLiveBoardRepository
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import io.festicore.domain.festival.FestivalPackRepository
import io.festicore.domain.festival.FestivalPackUseCase
import io.festicore.domain.festival.FestivalScheduleRepository
import io.festicore.domain.festival.FestivalScheduleUseCase
import io.festicore.domain.festival.sources.FestivalCatalogRepositoryImpl
import io.festicore.domain.festival.sources.FestivalCatalogUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLibraryUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLiveBoardRepositoryImpl
import io.festicore.domain.festival.sources.FestivalLiveBoardUseCaseImpl
import io.festicore.domain.festival.sources.FestivalPackRepositoryImpl
import io.festicore.domain.festival.sources.FestivalPackUseCaseImpl
import io.festicore.domain.festival.sources.FestivalScheduleRepositoryImpl
import io.festicore.domain.festival.sources.FestivalScheduleUseCaseImpl
import org.koin.dsl.module

val domainFestivalModule = module {
    single<FestivalCatalogRepository> { FestivalCatalogRepositoryImpl(get()) }
    factory<FestivalCatalogUseCase> { FestivalCatalogUseCaseImpl(get()) }
    factory<FestivalLibraryUseCase> { FestivalLibraryUseCaseImpl(get()) }
    single<FestivalLiveBoardRepository> { FestivalLiveBoardRepositoryImpl(get(), get(), get()) }
    factory<FestivalLiveBoardUseCase> { FestivalLiveBoardUseCaseImpl(get()) }
    // 목록 · 보드가 같은 팩 상태를 보도록 하나만 둔다(설계 D1).
    single<FestivalPackRepository> { FestivalPackRepositoryImpl(get(), get()) }
    factory<FestivalPackUseCase> { FestivalPackUseCaseImpl(get()) }
    single<FestivalScheduleRepository> { FestivalScheduleRepositoryImpl(get()) }
    factory<FestivalScheduleUseCase> { FestivalScheduleUseCaseImpl(get()) }
}
