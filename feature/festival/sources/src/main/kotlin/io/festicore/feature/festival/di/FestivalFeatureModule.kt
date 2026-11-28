package io.festicore.feature.festival.di

import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import io.festicore.feature.festival.FestivalCoordinator
import io.festicore.feature.festival.crew.CrewJoinViewModel
import io.festicore.feature.festival.crew.CrewViewModel
import io.festicore.feature.festival.festivallist.FestivalListViewModel
import io.festicore.feature.festival.flag.FlagCoordinator
import io.festicore.feature.festival.flag.FlagViewModel
import io.festicore.feature.festival.flagdetail.FlagDetailViewModel
import io.festicore.feature.festival.liveboard.LiveBoardViewModel
import io.festicore.feature.festival.liveboard.crewcreate.CrewCreateViewModel
import io.festicore.feature.festival.liveboard.crewgather.CrewGatherViewModel
import io.festicore.feature.festival.notificationinbox.NotificationInboxViewModel
import io.festicore.feature.festival.widget.createFestivalWidgetUpdater
import io.festicore.feature.festival.zonemap.ZoneMapViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val festivalFeatureModule = module {
    single { createFestivalWidgetUpdater(androidContext(), get()) }
    // 보드가 스택에서 빠지면 보드 공유 선택을 비운다(설계 T1 `clear`).
    viewModel {
        FestivalCoordinator(
            currentUserProvider = get(),
            clearBoardSelection = get<FestivalBoardSelectionUseCase>()::clear,
            packUseCase = get(),
            scheduleUseCase = get(),
        )
    }
    viewModelOf(::FestivalListViewModel)
    viewModelOf(::NotificationInboxViewModel)
    viewModelOf(::FlagCoordinator)
    // 선택 축제는 부모가 onFestivalChanged로 알린다(iOS `festivalChanged`).
    viewModel { FlagViewModel(liveBoardUseCase = get(), flagUseCase = get(), analyticsUseCase = get()) }
    viewModel(qualifier = org.koin.core.qualifier.named("prepFlags")) { params ->
        FlagViewModel(
            liveBoardUseCase = get(),
            flagUseCase = get(),
            analyticsUseCase = get(),
            festivalId = params.get(),
            tracksScreenView = false,
            followTimeoutMillis = 15_000L,
        )
    }
    // 인자: 깃발 ID, 처음 표시 문맥(FlagDetailContext). 이후 문맥은 부모가 onContextChanged로 알린다(iOS `contextChanged`).
    viewModel { params ->
        FlagDetailViewModel(
            flagId = params[0],
            context = params[1],
            detailUseCase = get(),
            flagUseCase = get(),
            analyticsUseCase = get(),
            bearerUseCase = get(),
        )
    }
    // 인자: 축제 ID, 목록이 가진 카탈로그(iOS가 목록 상태를 복사해 push하는 것과 같은 자리).
    viewModel { params ->
        LiveBoardViewModel(
            festivalId = params.get(),
            initialCatalog = params.get(),
            liveBoardUseCase = get(),
            packUseCase = get(),
            catalogUseCase = get(),
            scheduleUseCase = get(),
            preferenceUseCase = get(),
            currentUserProvider = get(),
            notificationBadgeUseCase = get(),
            boardSelectionUseCase = get(),
            analyticsUseCase = get(),
            locationClient = get(),
            mapsClient = get(),
        )
    }
    // 인자: 축제 ID, 처음 고를 존 ID(경로 값, null 가능). 둘 다 문자열이라 순서로 꺼낸다.
    viewModel { params ->
        ZoneMapViewModel(
            festivalId = params[0],
            selectedZoneId = params.values.getOrNull(1) as? String,
            zoneMapUseCase = get(),
            liveBoardUseCase = get(),
            packUseCase = get(),
            currentUserProvider = get(),
        )
    }
    // 인자: 축제 ID. 축제 이름은 보드 선택 문서에서 읽는다.
    viewModel { params -> CrewCreateViewModel(festivalId = params.get(), crewUseCase = get(), boardSelectionUseCase = get()) }
    // 인자: 축제 ID, 크루 ID. 둘 다 문자열이라 순서로 꺼낸다.
    viewModel { params ->
        CrewViewModel(
            festivalId = params[0],
            crewId = params[1],
            crewUseCase = get(),
            liveBoardUseCase = get(),
            boardSelectionUseCase = get(),
            currentUserProvider = get(),
        )
    }
    // 인자: 초대 코드. 로그인 상태는 현재 사용자로 정한다.
    viewModel { params ->
        CrewJoinViewModel(code = params.get(), crewUseCase = get(), liveBoardUseCase = get(), currentUserProvider = get())
    }
    // 인자: 시트를 여는 값(CrewGatherInput). 보드 · 크루 메인이 연다.
    viewModel { params -> CrewGatherViewModel(input = params.get(), crewUseCase = get()) }
}
