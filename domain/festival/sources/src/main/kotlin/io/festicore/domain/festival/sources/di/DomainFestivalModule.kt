package io.festicore.domain.festival.sources.di

import android.content.Context
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.festival.catalog.FestivalCatalogRepository
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.crew.FestivalCrewRepository
import io.festicore.domain.festival.crew.FestivalCrewUseCase
import io.festicore.domain.festival.flagdetail.FestivalFlagDetailRepository
import io.festicore.domain.festival.flagdetail.FestivalFlagDetailUseCase
import io.festicore.domain.festival.flagdetail.FestivalFlagRepository
import io.festicore.domain.festival.flagdetail.FestivalFlagUseCase
import io.festicore.domain.festival.library.FestivalLibraryUseCase
import io.festicore.domain.festival.liveboard.FestivalLiveBoardPreferenceUseCase
import io.festicore.domain.festival.liveboard.FestivalLiveBoardRepository
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import io.festicore.domain.festival.pack.FestivalPackRepository
import io.festicore.domain.festival.pack.FestivalPackUseCase
import io.festicore.domain.festival.packdetail.FestivalPackChangesRepository
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.domain.festival.receivedpack.FestivalWiFiStatus
import io.festicore.domain.festival.schedule.FestivalScheduleRepository
import io.festicore.domain.festival.schedule.FestivalScheduleUseCase
import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import io.festicore.domain.festival.sources.catalog.FestivalCatalogUseCaseImpl
import io.festicore.domain.festival.sources.crew.FestivalCrewUseCaseImpl
import io.festicore.domain.festival.sources.flag.FestivalFlagDetailUseCaseImpl
import io.festicore.domain.festival.sources.flag.FestivalFlagUseCaseImpl
import io.festicore.domain.festival.sources.library.FestivalLibraryUseCaseImpl
import io.festicore.domain.festival.sources.liveboard.FestivalLiveBoardPreferenceUseCaseImpl
import io.festicore.domain.festival.sources.liveboard.FestivalLiveBoardUseCaseImpl
import io.festicore.domain.festival.sources.pack.FestivalPackUseCaseImpl
import io.festicore.domain.festival.sources.packdetail.FestivalPackDetailUseCaseImpl
import io.festicore.domain.festival.sources.receivedpack.ConnectivityFestivalWiFiStatus
import io.festicore.domain.festival.sources.receivedpack.FestivalReceivedPackUseCaseImpl
import io.festicore.domain.festival.sources.repository.FestivalCatalogRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalCrewRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalFlagDetailRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalFlagRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalLiveBoardRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalPackChangesRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalPackRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalScheduleRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalZoneMapRepositoryImpl
import io.festicore.domain.festival.sources.schedule.FestivalScheduleSavedEvents
import io.festicore.domain.festival.sources.schedule.FestivalScheduleUseCaseImpl
import io.festicore.domain.festival.sources.selection.FestivalBoardSelectionStore
import io.festicore.domain.festival.sources.zonemap.FestivalZoneMapUseCaseImpl
import io.festicore.domain.festival.zonemap.FestivalZoneMapRepository
import io.festicore.domain.festival.zonemap.FestivalZoneMapUseCase
import io.festicore.service.apiendpoint.crew.CrewRequest
import io.festicore.service.apiendpoint.flag.FlagRequest
import io.festicore.service.auth.AuthSession
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
    single<FestivalWiFiStatus> { ConnectivityFestivalWiFiStatus(get<Context>()) }
    factory<FestivalReceivedPackUseCase> { FestivalReceivedPackUseCaseImpl(get(), get(), get(), get()) }
    single<FestivalPackChangesRepository> { FestivalPackChangesRepositoryImpl(get()) }
    factory<FestivalPackDetailUseCase> { FestivalPackDetailUseCaseImpl(get(), get(), get(), get(), get()) }
    single<FestivalZoneMapRepository> { FestivalZoneMapRepositoryImpl(get()) }
    factory<FestivalZoneMapUseCase> { FestivalZoneMapUseCaseImpl(get(), get()) }
    single<FestivalScheduleRepository> { FestivalScheduleRepositoryImpl(get()) }
    // 저장 알림 · 보드 선택은 타임테이블 · 보드가 함께 보도록 하나만 둔다(설계 T1 · T2).
    single { FestivalScheduleSavedEvents() }
    factory<FestivalScheduleUseCase> { FestivalScheduleUseCaseImpl(get(), get()) }
    single<FestivalBoardSelectionUseCase> { FestivalBoardSelectionStore() }
    single<FestivalFlagDetailRepository> {
        FestivalFlagDetailRepositoryImpl(
            api = get<FlagRequest>(),
            catalogService = get(),
            currentSessionId = get<AuthSession>()::currentSessionId,
        )
    }
    factory<FestivalFlagDetailUseCase> { FestivalFlagDetailUseCaseImpl(get()) }
    single<FestivalFlagRepository> { FestivalFlagRepositoryImpl(get()) }
    factory<FestivalFlagUseCase> { FestivalFlagUseCaseImpl(get()) }
    single<FestivalCrewRepository> {
        val currentUser = get<CurrentUserProvider>()
        FestivalCrewRepositoryImpl(
            api = get<CrewRequest>(),
            currentSessionId = get<AuthSession>()::currentSessionId,
        ) { currentUser.currentUserId.value }
    }
    factory<FestivalCrewUseCase> { FestivalCrewUseCaseImpl(get()) }
}
