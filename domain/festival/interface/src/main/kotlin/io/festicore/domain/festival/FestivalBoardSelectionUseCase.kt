package io.festicore.domain.festival

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 보드 선택 공유 상태(설계 T1, 앱 범위 하나). 탭을 바꿔 보드 화면이 빠져도 마지막 값이 남는다.
 * 보드는 [update] · [clear]만, 타임테이블은 [selection] · [requestRetry]만 쓴다.
 */
interface FestivalBoardSelectionUseCase {
    /** 보드가 보여 주는 축제. 보드가 없으면 null(iOS `noFestival`). */
    val selection: StateFlow<FestivalBoardSelection?>

    /** 타임테이블 "다시 시도" → 보드가 온라인 상세를 다시 읽는다(iOS `TimetableCoordinator.delegate(.retry)`). 값은 축제 ID. */
    val retryRequests: Flow<String>

    fun update(selection: FestivalBoardSelection)

    /** 보드 경로가 스택에서 빠질 때 부른다. */
    fun clear()

    suspend fun requestRetry(festivalId: String)
}
