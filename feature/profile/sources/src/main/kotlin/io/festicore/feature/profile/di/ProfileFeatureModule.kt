package io.festicore.feature.profile.di

import io.festicore.feature.profile.ProfileCoordinator
import io.festicore.feature.profile.account.AccountSettingsViewModel
import io.festicore.feature.profile.appearance.AppearanceSettingsViewModel
import io.festicore.feature.profile.main.ProfileViewModel
import io.festicore.feature.profile.notice.NoticeDetailViewModel
import io.festicore.feature.profile.notice.NoticeListViewModel
import io.festicore.feature.profile.notification.NotificationSettingsViewModel
import io.festicore.feature.profile.packdetail.PackDetailViewModel
import io.festicore.feature.profile.settings.HomeWidgetSettingsViewModel
import io.festicore.feature.profile.settings.SettingsHomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** `AppearanceModeSettings`는 app이 `core/storage`를 감싸 등록하고, `LegalDocumentUrls`는 `webFeatureModule`이 등록한다. */
val profileFeatureModule = module {
    // 마이 탭 흐름. 계정이 바뀌면 마이 위 화면을 닫으려고 CurrentUserProvider(domainAuthModule)를 본다.
    viewModelOf(::ProfileCoordinator)
    // 인자: 화면을 만들 때의 계정 상태(AccountState). 이후 변경은 화면이 onAccountChanged로 알린다(설계 쟁점 M13).
    viewModel { params ->
        ProfileViewModel(
            initialAccount = params.get(),
            receivedPackUseCase = get(),
            packDetailUseCase = get(),
            analyticsUseCase = get(),
            bearerUseCase = get(),
        )
    }
    // 인자: 화면을 만들 때의 계정 상태(AccountState).
    viewModel { params ->
        SettingsHomeViewModel(initialAccount = params.get(), legalDocumentUrls = get(), appearanceModeSettings = get())
    }
    viewModelOf(::AppearanceSettingsViewModel)
    viewModelOf(::HomeWidgetSettingsViewModel)
    viewModelOf(::NotificationSettingsViewModel)
    // 인자: 화면을 만들 때의 계정 상태(AccountState). 닉네임 저장은 domain/auth `AuthRepository.updateNickname`.
    viewModel { params -> AccountSettingsViewModel(initialAccount = params.get(), authRepository = get(), analyticsUseCase = get()) }
    // 공지 읽기는 domain/notice `NoticeUseCase`(domainNoticeModule).
    viewModelOf(::NoticeListViewModel)
    // 인자: 목록에서 고른 공지 id · 제목.
    viewModel { (id: String, title: String) -> NoticeDetailViewModel(id = id, title = title, noticeUseCase = get()) }
    // 지도 앱 설치 여부(설계 쟁점 M10). 패키지 이름은 app 매니페스트 `<queries>`에 적는다.
    // 인자: 팩 축제 id · 넘겨받은 축제 이름. 팩 읽기 · 업데이트 · 삭제 · 준비물은 domain/festival `FestivalPackDetailUseCase`.
    viewModel { params ->
        PackDetailViewModel(
            festivalId = params[0],
            title = params[1],
            packDetailUseCase = get(),
            mapAppClient = get(),
            tracksChecklistWrites =
                params.values.getOrNull(2) as? Boolean ?: false,
        )
    }
}
