package io.festicore.domain.festival

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** 내 일정 읽기 · 저장 · 저장 알림. 실패는 [FestivalScheduleError]. 규칙은 `domain/festival/.spec/my-schedule.md`. iOS `FestivalScheduleUseCaseInterface`. */
interface FestivalScheduleUseCase {
    suspend fun mySchedule(festivalId: String): FestivalSchedule

    /** 내 일정 전체를 [setIds]로 바꾼다. [clientAt]은 축제마다 이전 요청보다 늦어야 한다(1ms 이상). */
    suspend fun saveMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule

    /** 앱 안 저장 알림(앱 범위 하나). 구독 전에 보낸 알림은 받지 않는다. */
    val savedSchedules: Flow<FestivalScheduleSaved>

    /** 저장 성공 · 충돌 뒤 서버 값 채택 · 고르기 저장을 알린다. 보낼지는 호출한 화면이 정한다(iOS `scheduleSaved` delegate). */
    suspend fun notifyMyScheduleSaved(saved: FestivalScheduleSaved)
}
