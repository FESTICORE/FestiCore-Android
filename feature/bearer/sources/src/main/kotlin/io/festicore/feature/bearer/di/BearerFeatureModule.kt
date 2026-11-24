package io.festicore.feature.bearer.di

import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerSet
import io.festicore.feature.bearer.board.BearerBoardViewModel
import io.festicore.feature.bearer.claim.BearerClaimViewModel
import io.festicore.feature.bearer.plan.BearerPlanPickViewModel
import io.festicore.feature.bearer.profile.BearerProfileViewModel
import io.festicore.feature.bearer.register.BearerRegisterViewModel
import io.festicore.feature.bearer.register.BearerRoleChoiceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 기수 화면. `BearerUseCase`는 `domainBearerModule`, 축제 목록 · 받은 팩 UseCase는 `domainFestivalModule`이 등록한다. */
val bearerFeatureModule = module {
    // 인자: 코드를 받을 축제 id(`BearerRoute.Claim.festivalId`).
    viewModel { (festivalId: String) -> BearerClaimViewModel(festivalId = festivalId, bearerUseCase = get()) }
    // 인자: 축제 id · 깃발 id · 코드 등록 응답의 프로필(없으면 null) · 새 기수 여부(`BearerRoute.Profile`).
    viewModel { params ->
        BearerProfileViewModel(
            festivalId = params.get<String>(0),
            flagId = params.get<String>(1),
            profile = params.get<BearerFlagProfile?>(2),
            isNewBearer = params.get<Boolean>(3),
            bearerUseCase = get(),
        )
    }
    // 인자: 팩 공연 · 이미 고른 오늘 계획(프로필 State의 `sets` · `plan`).
    viewModel { (sets: List<BearerSet>, plan: List<String>) -> BearerPlanPickViewModel(sets = sets, plan = plan) }
    viewModel { BearerRoleChoiceViewModel() }
    // 인자: 라이브 탭에서 연 축제 id(`BearerRoute.Register.preferredFestivalId`, 없으면 null).
    viewModel { params ->
        BearerRegisterViewModel(
            preferredFestivalId = params.getOrNull<String>(),
            bearerUseCase = get(),
            catalogUseCase = get(),
            receivedPackUseCase = get(),
        )
    }
    // 인자: 축제 id · 깃발 id(`BearerRoute.Board`) · 보는 사용자 id(로그인 안 했으면 null).
    viewModel { params ->
        BearerBoardViewModel(
            festivalId = params.get<String>(0),
            flagId = params.get<String>(1),
            viewerUserId = params.get<String?>(2),
            bearerUseCase = get(),
        )
    }
}
