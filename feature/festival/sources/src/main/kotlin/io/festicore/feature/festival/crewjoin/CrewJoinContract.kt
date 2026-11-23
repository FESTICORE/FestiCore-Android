package io.festicore.feature.festival.crewjoin

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewInvite
import java.time.Instant

/** 초대 불러오기 단계(iOS `FestivalCrewJoinFeature.Load`). */
enum class CrewJoinLoad {
    Loading,
    Loaded,

    /** 없거나 만료된 초대. */
    Unavailable,

    /** 연결 실패. 다시 시도할 수 있다. */
    Failed,
}

/** 불러온 초대로는 참여할 수 없는 까닭(Figma S08 B 만료 · C 정원 초과). 참여 대신 닫기만 둔다. iOS `Blocker`. */
enum class CrewJoinBlocker {
    Expired,
    Full,
}

/** 초대받은 페스티벌에서 지금 들어 있는 크루(iOS `PendingLeave`). 확인을 누르면 나간 뒤 참여한다. */
data class CrewJoinPendingLeave(
    val crewId: String,
    val crewName: String,
)

/** 확인 다이얼로그(iOS `FCAlertState`). */
data class CrewJoinAlert(
    val title: String,
    val message: String,
    val confirmTitle: String = "확인",
    val cancelTitle: String = "닫기",
)

data class CrewJoinState(
    val code: String,
    val hasSession: Boolean,
    val invite: FestivalCrewInvite? = null,
    val load: CrewJoinLoad = CrewJoinLoad.Loading,
    val isJoining: Boolean = false,
    /** S08 D 확인 팝업에서 나갈 기존 크루. */
    val pendingLeave: CrewJoinPendingLeave? = null,
    val alert: CrewJoinAlert? = null,
) {
    /** 로그인 시트 문구(Figma S08 E)에 넣을 초대받은 크루 이름. */
    val crewName: String?
        get() = invite?.crewName

    /** iOS `blocker(now:)`. 만료를 정원보다 먼저 본다. */
    fun blocker(now: Instant): CrewJoinBlocker? {
        val invite = invite ?: return null
        if (!invite.expiresAt.isAfter(now)) return CrewJoinBlocker.Expired
        if (invite.memberCount >= FestivalCrew.MAX_MEMBERS) return CrewJoinBlocker.Full
        return null
    }
}

sealed interface CrewJoinSideEffect {
    data object CloseRequested : CrewJoinSideEffect

    /** 비로그인 · 세션이 끊겼다. 부모가 참여 문맥(크루 이름)으로 로그인을 요청한다. */
    data class LoginRequested(val crewName: String?) : CrewJoinSideEffect

    /** 참여했다. 부모가 크루 화면(S07)을 연다. */
    data class Joined(
        val crewId: String,
        val festivalId: String,
    ) : CrewJoinSideEffect
}
