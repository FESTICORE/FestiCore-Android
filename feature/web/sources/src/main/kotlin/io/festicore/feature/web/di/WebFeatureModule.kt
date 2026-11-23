package io.festicore.feature.web.di

import io.festicore.feature.web.LegalDocumentUrls
import io.festicore.feature.web.WebViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** [legalBaseUrl]은 app이 `BuildConfig.LEGAL_BASE_URL`로 넘긴다. [LegalDocumentUrls]는 가입 화면 · 설정 홈이 함께 쓴다. */
fun webFeatureModule(legalBaseUrl: String): Module = module {
    single { LegalDocumentUrls(legalBaseUrl) }
    viewModel { params -> WebViewModel(params.get()) }
}
