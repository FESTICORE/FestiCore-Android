package io.festicore.feature.timetable.di

import io.festicore.feature.timetable.main.TimetableViewModel
import io.festicore.feature.timetable.schedulepick.SchedulePickViewModel
import io.festicore.feature.timetable.setlist.TimetableSetListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 타임테이블 탭 화면. app Koin 목록 등록은 탭 연결 PR(C1)에서 한다. */
val timetableFeatureModule = module {
    viewModel {
        TimetableViewModel(
            boardSelectionUseCase = get(),
            scheduleUseCase = get(),
            currentUserProvider = get(),
            mapper = get(),
            overlapCalculator = get(),
        )
    }
    // 인자: 목록 모드, 열 때 타임테이블이 가진 날짜(iOS가 타임테이블 상태를 복사해 push하는 것과 같은 자리).
    viewModel { params -> TimetableSetListViewModel(mode = params[0], days = params[1]) }
    // 인자: 고르기 경로(축제 · 지금 내 일정 · 마지막 저장 시각). 날짜는 공유 팩에서 다시 계산한다(설계 T8).
    viewModel { params ->
        SchedulePickViewModel(
            route = params.get(),
            boardSelectionUseCase = get(),
            mapper = get(),
            overlapCalculator = get(),
            scheduleUseCase = get(),
        )
    }
}
