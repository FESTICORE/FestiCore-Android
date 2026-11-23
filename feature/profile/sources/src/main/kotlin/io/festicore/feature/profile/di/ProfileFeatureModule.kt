package io.festicore.feature.profile.di

import io.festicore.feature.profile.account.AccountSettingsViewModel
import io.festicore.feature.profile.appearance.AppearanceSettingsViewModel
import io.festicore.feature.profile.main.ProfileViewModel
import io.festicore.feature.profile.settings.SettingsHomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** `AppearanceModeSettings`는 app이 `core/storage`를 감싸 등록하고, `LegalDocumentUrls`는 `webFeatureModule`이 등록한다. */
val profileFeatureModule = module {
    // 인자: 화면을 만들 때의 계정 상태(AccountState). 이후 변경은 화면이 onAccountChanged로 알린다(설계 쟁점 M13).
    viewModel { params ->
        ProfileViewModel(initialAccount = params.get(), receivedPackUseCase = get(), packDetailUseCase = get())
    }
    // 인자: 화면을 만들 때의 계정 상태(AccountState).
    viewModel { params ->
        SettingsHomeViewModel(initialAccount = params.get(), legalDocumentUrls = get(), appearanceModeSettings = get())
    }
    viewModelOf(::AppearanceSettingsViewModel)
    // 인자: 화면을 만들 때의 계정 상태(AccountState). 닉네임 저장은 domain/auth `AuthRepository.updateNickname`.
    viewModel { params -> AccountSettingsViewModel(initialAccount = params.get(), authRepository = get()) }
}
