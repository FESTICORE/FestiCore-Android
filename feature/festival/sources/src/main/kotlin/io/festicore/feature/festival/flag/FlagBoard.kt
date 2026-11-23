package io.festicore.feature.festival.flag

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.FlagTabInput
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.liveboard.LiveBoardState

/**
 * 깃발 탭이 따르는 라이브 보드 값(iOS `MainTabCoordinator.flagBoard`). 보드가 고른 축제와 그 보드로 만든 상세 문맥이다.
 * 대표 깃발은 깃발 목록 스냅샷에서, 세션은 현재 사용자에서 채운다([flagDetailContext]).
 */
data class FlagBoard(
    val festivalId: String,
    val detailContext: FlagDetailContext,
) {
    companion object {
        fun of(board: LiveBoardState): FlagBoard = FlagBoard(board.festivalId, FlagDetailContext.board(board, snapshot = null))
    }
}

/** 깃발 목록 입력(iOS `syncFlag`의 `festivalChanged` · `zoneNamesChanged` · `accountChanged`). 보드가 없으면 축제 · 존 이름을 비운다. */
fun FlagBoard?.flagTabInput(accountId: String?): FlagTabInput = FlagTabInput(
    selectedFestivalId = this?.festivalId,
    accountId = accountId,
    zoneNames = this?.detailContext?.zoneNames.orEmpty(),
)

/**
 * 열린 상세에 넘길 문맥(iOS `flagDetailContext(state)`). 대표 깃발은 같은 축제의 목록 스냅샷에서만 읽는다.
 * 세션은 현재 사용자([accountId])로 둔다. 보드가 다른 탭에 있어 갱신이 멈춰도 로그인 · 계정 변경이 상세에 바로 닿게 한다
 * (보드의 세션 값도 같은 현재 사용자에서 온다).
 */
fun FlagBoard?.flagDetailContext(snapshot: FestivalLiveBoardSnapshot?, accountId: String?): FlagDetailContext =
    (this?.detailContext ?: FlagDetailContext()).copy(
        hasSession = accountId != null,
        accountId = accountId,
        pinnedFlagId = snapshot?.takeIf { it.festivalId == this?.festivalId }?.pinnedFlagId,
    )
