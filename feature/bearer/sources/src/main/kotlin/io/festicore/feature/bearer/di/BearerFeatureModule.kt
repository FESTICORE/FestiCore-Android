package io.festicore.feature.bearer.di

import io.festicore.feature.bearer.claim.BearerClaimViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 기수 화면. `BearerUseCase`는 `domainBearerModule`이 등록한다. */
val bearerFeatureModule = module {
    // 인자: 코드를 받을 축제 id(`BearerRoute.Claim.festivalId`).
    viewModel { (festivalId: String) -> BearerClaimViewModel(festivalId = festivalId, bearerUseCase = get()) }
}
