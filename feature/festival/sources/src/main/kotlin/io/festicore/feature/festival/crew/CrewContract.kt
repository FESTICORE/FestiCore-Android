package io.festicore.feature.festival.crew

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.feature.festival.liveboard.crewgather.CrewGatherInput
import java.time.Instant

/** iOS `FestivalCrewFeature.Load`. */
enum class CrewLoad {
    Loading,
    Loaded,
    Failed,
}

/** iOS `FestivalCrewFeature.Toast`. */
sealed interface CrewToast {
    data object InviteRotated : CrewToast

    data object Renamed : CrewToast

    data class Transferred(val nickname: String) : CrewToast

    data class Removed(val nickname: String) : CrewToast

    data object StatusFailed : CrewToast

    data object GatherExpired : CrewToast
}

/** 확인 다이얼로그에서 확인을 누르면 실행할 크루장 동작(iOS `Confirm`). */
sealed interface CrewConfirm {
    data class Transfer(
        val userId: String,
        val nickname: String,
    ) : CrewConfirm

    data class Remove(
        val userId: String,
        val nickname: String,
    ) : CrewConfirm

    data object Dissolve : CrewConfirm
}

/** 크루 화면 위에 뜨는 것 하나(iOS `Destination`). */
sealed interface CrewDestination {
    data class Gather(val input: CrewGatherInput) : CrewDestination

    data class Menu(val menu: CrewMenuState) : CrewDestination

    /** 실패 안내(iOS `alert`). */
    data class Alert(
        val title: String,
        val message: String,
    ) : CrewDestination

    /** 크루장 동작 확인(iOS `confirm`). */
    data class Confirm(
        val title: String,
        val message: String,
        val confirmTitle: String,
    ) : CrewDestination
}

/** 크루장 메뉴 시트 안의 단계(iOS `FestivalCrewMenuFeature.Step`). */
enum class CrewMenuStep {
    Menu,
    Rename,
    Transfer,
    Remove,
}

/** 크루장 메뉴 시트(iOS `FestivalCrewMenuFeature.State`). */
data class CrewMenuState(
    val crewName: String,
    /** 크루장을 뺀 크루원. 넘기기 · 내보내기 대상이다. */
    val members: List<Member>,
    val step: CrewMenuStep = CrewMenuStep.Menu,
    val nameDraft: String = crewName,
) {
    data class Member(
        val id: String,
        val nickname: String,
    )

    /** 크루 이름은 1~12자이고 지금 이름과 달라야 저장한다. 이전에 만든 13~20자 이름은 줄여야 저장된다. */
    val canSaveName: Boolean
        get() {
            val name = nameDraft.trim()
            return name.length in 1..FestivalCrew.MAX_NAME_LENGTH && name != crewName
        }
}

data class CrewState(
    val festivalId: String,
    val crewId: String,
    val zones: List<FestivalPackDocument.Zone> = emptyList(),
    /** 크루가 보관되는 시각. 서버와 같게 축제 마지막 날 종료 뒤 자정(KST)으로 잡는다. */
    val archivesAt: Instant? = null,
    /** S07 I 안내 띠에 넣는 축제 이름. */
    val festivalName: String? = null,
    val sessionUserId: String? = null,
    val crew: FestivalCrew? = null,
    /** 크루원 존 · 상태. 라이브 보드 값으로 먼저 그리고 상세 응답으로 바꾼다. */
    val live: FestivalLiveBoardSnapshot.Crew? = null,
    val load: CrewLoad = CrewLoad.Loading,
    val isSendingStatus: Boolean = false,
    /** 모여 응답을 보내는 중. 응답이 올 때까지 다시 누르지 못한다. */
    val isRespondingGather: Boolean = false,
    /** 서버가 확정한 내 모여 응답. 다음 상세 응답 전까지 버튼에 반영한다. */
    val gatherReply: FestivalCrewGatherReply? = null,
    /** 응답하려다 만료(409)를 받은 모여. 배너를 내린다. */
    val expiredGatherId: String? = null,
    val toast: CrewToast? = null,
    val pendingConfirm: CrewConfirm? = null,
    val destination: CrewDestination? = null,
    /** 모여 시트를 연 횟수. 시트 ViewModel 키로 쓴다. */
    val gatherCount: Int = 0,
    /** 화면 문구 기준 시각. 30초마다 바뀐다(iOS `TimelineView(.periodic(by: 30))`). */
    val now: Instant = Instant.EPOCH,
) {
    /** 크루장만 초대 링크와 크루장 메뉴를 쓴다. */
    val isLeader: Boolean
        get() = sessionUserId != null && (crew?.ownerId ?: live?.ownerId) == sessionUserId

    /** 공유 시트로 보내는 초대 링크(`festicore://crew/<코드>`). 앱의 링크 처리가 S08 참여 확인을 연다. */
    val inviteUrl: String?
        get() = crew?.inviteCode?.takeIf { isLeader }?.let { "festicore://crew/$it" }

    val title: String get() = crew?.name ?: live?.name ?: "크루"

    val isArchived: Boolean get() = crew?.archivedAt != null

    val myState: FestivalCrewState?
        get() {
            val me = sessionUserId ?: return null
            val value = live?.members?.firstOrNull { it.id == me }?.state ?: return null
            return FestivalCrewState.entries.firstOrNull { it.rawValue == value }
        }

    /** 내가 이 모여에 고른 응답. 방금 보낸 응답을 서버 목록보다 먼저 본다. */
    fun myGatherResponse(request: FestivalLiveBoardSnapshot.Crew.GatherRequest): FestivalCrewGatherResponse? {
        gatherReply?.takeIf { it.gatherId == request.id }?.let { return it.response }
        val me = sessionUserId ?: return null
        return request.responses.firstOrNull { it.userId == me }?.response
    }

    fun zoneName(id: String?): String? = id?.let { zoneId -> zones.firstOrNull { it.id == zoneId }?.name }

    companion object {
        /** Figma S07: 크루는 마지막 공연이 끝난 날의 자정(KST)까지 유지한다. 서버 `crewArchiveTime`과 같은 기준이다. */
        fun archiveTime(document: FestivalPackDocument?): Instant? {
            val lastEnd = document?.days.orEmpty().mapNotNull { FestivalIsoTime.parse(it.endsAt) }.maxOrNull() ?: return null
            val dayStart = lastEnd.atZone(LiveBoardPresentation.zone).toLocalDate().atStartOfDay(LiveBoardPresentation.zone)
            if (dayStart.toInstant() == lastEnd) return lastEnd
            return dayStart.plusDays(1).toInstant()
        }
    }
}

sealed interface CrewSideEffect {
    data object BackRequested : CrewSideEffect

    /** 해산했거나 크루에서 빠져 화면을 닫는다(iOS `crewClosed`). 보드는 다시 보일 때 새로 받는다. */
    data object CrewClosed : CrewSideEffect
}
