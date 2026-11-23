package io.festicore.feature.profile.di

import io.festicore.feature.profile.main.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileFeatureModule = module {
    // 인자: 화면을 만들 때의 계정 상태(AccountState). 이후 변경은 화면이 onAccountChanged로 알린다(설계 쟁점 M13).
    viewModel { params ->
        ProfileViewModel(initialAccount = params.get(), receivedPackUseCase = get(), packDetailUseCase = get())
    }
}
