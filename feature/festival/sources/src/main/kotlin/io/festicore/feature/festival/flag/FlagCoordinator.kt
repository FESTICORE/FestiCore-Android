package io.festicore.feature.festival.flag

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.FlagRoute
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class FlagCoordinatorState(val routes: List<NavKey> = listOf(FlagRoute.Directory))

/** 흐름 안 화면 사이의 전달. [FlagFlow]가 받아 목록에 넘긴다. */
sealed interface FlagCoordinatorSideEffect {
    /** 상세에서 바꾼 팔로우를 목록 카드에 보인다(iOS `followChangedElsewhere`). [baseCount]는 목록이 받은 서버 팔로워 수다. */
    data class FollowChangedElsewhere(
        val result: FestivalFlagFollowResult,
        val baseCount: Int?,
    ) : FlagCoordinatorSideEffect
}

/**
 * 깃발 탭 흐름(iOS `FlagCoordinator`). 첫 화면은 목록(`routes = [.directory]`)이다.
 * 목록 카드 · 깃발 링크는 상세를 쌓는다. 기존 상세는 지우고 하나만 둔다(iOS `showDetail`). 뒤로는 pop이다.
 * 탭 바는 목록 · 상세 모두 보이므로 숨김 상태가 없다(설계 "탭 바 규칙"). 같은 탭을 다시 눌러도 루트로 pop하지 않는다.
 */
class FlagCoordinator :
    ViewModel(),
    OrbitContainerHost<FlagCoordinatorState, FlagCoordinatorState, FlagCoordinatorSideEffect> {
    override val container = orbitContainer<FlagCoordinatorState, FlagCoordinatorSideEffect>(FlagCoordinatorState())

    /** 마지막으로 받은 계정. 처음 받은 값은 기준으로만 둔다. */
    private var lastAccount: Account? = null

    /** iOS 상세 `backRequested` · 시스템 뒤로. 목록뿐이면 스택을 비우지 않는다. */
    fun goBack() = intent {
        reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) }
    }

    /** 목록 카드(iOS `detailRequested`)나 깃발 링크(iOS `showDetail`)로 상세를 연다. 다른 깃발을 열면 쌓지 않고 바꾼다. */
    fun openDetail(flagId: String) = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is FlagRoute.Detail } + FlagRoute.Detail(flagId)) }
    }

    /** 상세 `followChanged`. 기준 수는 목록 스냅샷 `directoryFlags`의 같은 깃발 팔로워 수다(iOS `syncDirectoryFollow`). */
    fun onDetailFollowChanged(result: FestivalFlagFollowResult, directorySnapshot: FestivalLiveBoardSnapshot?) = intent {
        val baseCount = directorySnapshot?.directoryFlags?.firstOrNull { it.id == result.flagId }?.followerCount
        postSideEffect(FlagCoordinatorSideEffect.FollowChangedElsewhere(result, baseCount))
    }

    /**
     * 상위가 알린 계정. 이전에 받은 계정과 다르면 `true`를 돌려준다(목록 `accountChanged`를 보낼 때). 처음 받은 값은 `false`다.
     * 스택은 그대로 두고, 열린 상세는 새 문맥으로 다시 불러온다(iOS `MTC:489-547`).
     */
    fun onAccountChanged(accountId: String?): Boolean {
        val previous = lastAccount
        lastAccount = Account(accountId)
        return previous != null && previous.id != accountId
    }

    private data class Account(val id: String?)
}
