package io.festicore.domain.timetable.sources.di

import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.timetable.FestivalPackTimetableMapper
import io.festicore.domain.timetable.TimetableOverlapCalculator
import io.festicore.domain.timetable.sources.FestivalPackTimetableMapperImpl
import io.festicore.domain.timetable.sources.TimetableOverlapCalculatorImpl
import org.koin.dsl.module

/** 사진 주소는 목록과 같은 결합 규칙(`FestivalCatalogUseCase.imageUrl`)을 쓴다. 등록(app Koin 목록)은 탭 연결 PR(C1)에서 한다. */
val domainTimetableModule = module {
    factory<FestivalPackTimetableMapper> { FestivalPackTimetableMapperImpl(get<FestivalCatalogUseCase>()::imageUrl) }
    factory<TimetableOverlapCalculator> { TimetableOverlapCalculatorImpl() }
}
