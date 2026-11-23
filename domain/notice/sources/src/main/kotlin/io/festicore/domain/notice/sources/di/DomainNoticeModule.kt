package io.festicore.domain.notice.sources.di

import io.festicore.domain.notice.notice.NoticeRepository
import io.festicore.domain.notice.notice.NoticeUseCase
import io.festicore.domain.notice.sources.notice.NoticeUseCaseImpl
import io.festicore.domain.notice.sources.repository.NoticeRepositoryImpl
import org.koin.dsl.module

val domainNoticeModule = module {
    single<NoticeRepository> { NoticeRepositoryImpl(get()) }
    factory<NoticeUseCase> { NoticeUseCaseImpl(get()) }
}
