package io.festicore.feature.festival.di

import io.festicore.feature.festival.FestivalCoordinator
import io.festicore.feature.festival.FestivalListViewModel
import io.festicore.feature.festival.liveboard.LiveBoardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val festivalFeatureModule = module {
    viewModelOf(::FestivalCoordinator)
    viewModelOf(::FestivalListViewModel)
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
        )
    }
}
