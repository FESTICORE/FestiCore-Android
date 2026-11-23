package io.festicore.domain.festival.sources.di

import io.festicore.domain.auth.CurrentUserProvider
import io.festicore.domain.festival.FestivalBoardSelectionUseCase
import io.festicore.domain.festival.FestivalCatalogRepository
import io.festicore.domain.festival.FestivalCatalogUseCase
import io.festicore.domain.festival.FestivalCrewRepository
import io.festicore.domain.festival.FestivalCrewUseCase
import io.festicore.domain.festival.FestivalFlagDetailRepository
import io.festicore.domain.festival.FestivalFlagDetailUseCase
import io.festicore.domain.festival.FestivalFlagRepository
import io.festicore.domain.festival.FestivalFlagUseCase
import io.festicore.domain.festival.FestivalLibraryUseCase
import io.festicore.domain.festival.FestivalLiveBoardPreferenceUseCase
import io.festicore.domain.festival.FestivalLiveBoardRepository
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import io.festicore.domain.festival.FestivalPackRepository
import io.festicore.domain.festival.FestivalPackUseCase
import io.festicore.domain.festival.FestivalScheduleRepository
import io.festicore.domain.festival.FestivalScheduleUseCase
import io.festicore.domain.festival.FestivalZoneMapRepository
import io.festicore.domain.festival.FestivalZoneMapUseCase
import io.festicore.domain.festival.sources.FestivalBoardSelectionStore
import io.festicore.domain.festival.sources.FestivalCatalogRepositoryImpl
import io.festicore.domain.festival.sources.FestivalCatalogUseCaseImpl
import io.festicore.domain.festival.sources.FestivalCrewRepositoryImpl
import io.festicore.domain.festival.sources.FestivalCrewUseCaseImpl
import io.festicore.domain.festival.sources.FestivalFlagDetailRepositoryImpl
import io.festicore.domain.festival.sources.FestivalFlagDetailUseCaseImpl
import io.festicore.domain.festival.sources.FestivalFlagRepositoryImpl
import io.festicore.domain.festival.sources.FestivalFlagUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLibraryUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLiveBoardPreferenceUseCaseImpl
import io.festicore.domain.festival.sources.FestivalLiveBoardRepositoryImpl
import io.festicore.domain.festival.sources.FestivalLiveBoardUseCaseImpl
import io.festicore.domain.festival.sources.FestivalPackRepositoryImpl
import io.festicore.domain.festival.sources.FestivalPackUseCaseImpl
import io.festicore.domain.festival.sources.FestivalScheduleRepositoryImpl
import io.festicore.domain.festival.sources.FestivalScheduleSavedEvents
import io.festicore.domain.festival.sources.FestivalScheduleUseCaseImpl
import io.festicore.domain.festival.sources.FestivalZoneMapRepositoryImpl
import io.festicore.domain.festival.sources.FestivalZoneMapUseCaseImpl
import org.koin.dsl.module

val domainFestivalModule = module {
    single<FestivalCatalogRepository> { FestivalCatalogRepositoryImpl(get()) }
    factory<FestivalCatalogUseCase> { FestivalCatalogUseCaseImpl(get()) }
    factory<FestivalLibraryUseCase> { FestivalLibraryUseCaseImpl(get()) }
    single<FestivalLiveBoardRepository> { FestivalLiveBoardRepositoryImpl(get(), get(), get()) }
    factory<FestivalLiveBoardUseCase> { FestivalLiveBoardUseCaseImpl(get()) }
    factory<FestivalLiveBoardPreferenceUseCase> { FestivalLiveBoardPreferenceUseCaseImpl(get()) }
    // 목록 · 보드가 같은 팩 상태를 보도록 하나만 둔다(설계 D1).
    single<FestivalPackRepository> { FestivalPackRepositoryImpl(get(), get()) }
    factory<FestivalPackUseCase> { FestivalPackUseCaseImpl(get(), get()) }
    single<FestivalZoneMapRepository> { FestivalZoneMapRepositoryImpl(get()) }
    factory<FestivalZoneMapUseCase> { FestivalZoneMapUseCaseImpl(get(), get()) }
    single<FestivalScheduleRepository> { FestivalScheduleRepositoryImpl(get()) }
    // 저장 알림 · 보드 선택은 타임테이블 · 보드가 함께 보도록 하나만 둔다(설계 T1 · T2).
    single { FestivalScheduleSavedEvents() }
    factory<FestivalScheduleUseCase> { FestivalScheduleUseCaseImpl(get(), get()) }
    single<FestivalBoardSelectionUseCase> { FestivalBoardSelectionStore() }
    single<FestivalFlagDetailRepository> { FestivalFlagDetailRepositoryImpl(get(), get()) }
    factory<FestivalFlagDetailUseCase> { FestivalFlagDetailUseCaseImpl(get()) }
    single<FestivalFlagRepository> { FestivalFlagRepositoryImpl(get()) }
    factory<FestivalFlagUseCase> { FestivalFlagUseCaseImpl(get()) }
    single<FestivalCrewRepository> {
        val currentUser = get<CurrentUserProvider>()
        FestivalCrewRepositoryImpl(get()) { currentUser.currentUserId.value }
    }
    factory<FestivalCrewUseCase> { FestivalCrewUseCaseImpl(get()) }
}
