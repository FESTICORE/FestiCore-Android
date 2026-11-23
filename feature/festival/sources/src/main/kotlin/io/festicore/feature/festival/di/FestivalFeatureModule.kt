package io.festicore.feature.festival.di

import io.festicore.feature.festival.FestivalCoordinator
import io.festicore.feature.festival.FestivalListViewModel
import io.festicore.feature.festival.flag.FlagCoordinator
import io.festicore.feature.festival.flag.FlagViewModel
import io.festicore.feature.festival.flagdetail.FlagDetailViewModel
import io.festicore.feature.festival.inbox.NotificationInboxViewModel
import io.festicore.feature.festival.liveboard.LiveBoardViewModel
import io.festicore.feature.festival.zonemap.ZoneMapViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val festivalFeatureModule = module {
    viewModelOf(::FestivalCoordinator)
    viewModelOf(::FestivalListViewModel)
    viewModelOf(::NotificationInboxViewModel)
    viewModelOf(::FlagCoordinator)
    // 선택 축제는 부모가 onFestivalChanged로 알린다(iOS `festivalChanged`).
    viewModel { FlagViewModel(liveBoardUseCase = get(), flagUseCase = get()) }
    // 인자: 깃발 ID, 처음 표시 문맥(FlagDetailContext). 이후 문맥은 부모가 onContextChanged로 알린다(iOS `contextChanged`).
    viewModel { params ->
        FlagDetailViewModel(
            flagId = params[0],
            context = params[1],
            detailUseCase = get(),
            flagUseCase = get(),
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
}
