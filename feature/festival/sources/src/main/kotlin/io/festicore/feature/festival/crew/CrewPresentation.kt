package io.festicore.feature.festival.crew

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.festivallist.FestivalListPresentation
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/** Figma S07 크루 화면에 그리는 값(iOS `FestivalCrewPresentation`). 서버 값과 지금 시각으로 문구를 만든다. */
data class CrewPresentation(
    val summary: String,
    /** 카드 머리의 "4 / 12". */
    val memberCount: String,
    /** 크루가 끝나 기록만 보는 상태(S07 I). 모여! · 초대 · 내 상태를 숨긴다. */
    val isArchived: Boolean,
    val members: List<Member>,
    val gather: Gather?,
    val gatherTitle: String,
    val canGather: Boolean,
) {
    data class Member(
        val id: String,
        val nickname: String,
        val zone: String?,
        val status: String?,
        val time: String?,
        /** 내 줄이면 이름 옆에 "나" 태그를 붙인다. */
        val isMe: Boolean,
        /** 귀가 중이거나 60분 넘게 바뀌지 않은 크루원은 흐리게 그린다. */
        val isInactive: Boolean,
    )

    /** Figma S07 F 모여 요청 배너. 응답 시간(10분) 안에만 그린다. */
    data class Gather(
        /** "민지: 슬램 존으로 모여!" */
        val title: String,
        /** "방금 · 크루원 모두에게 소리 알림을 보냈어요" */
        val detail: String,
        /** 응답 버튼 모양. 보낸 사람이거나 응답할 수 없는 모여면 null이고 [summary]를 그린다. */
        val actions: Actions?,
        /** 보낸 사람이 보는 응답 요약. "가는 중 2명 · 확인 1명" */
        val summary: String?,
    )

    data class Actions(
        /** "슬램 존으로 가는 중" */
        val goingTitle: String,
        /** 내가 고른 응답. 고른 버튼을 진하게 그린다. */
        val selected: FestivalCrewGatherResponse?,
        val isResponding: Boolean,
    )

    companion object {
        private val INACTIVE_INTERVAL: Duration = Duration.ofMinutes(60)

        /** 서버가 `expiresAt`을 주지 않던 이전 응답에서 쓰는 모여 응답 시간. */
        private val GATHER_LIFETIME: Duration = Duration.ofMinutes(10)

        fun of(crew: CrewState, now: Instant): CrewPresentation {
            val live = crew.live
            val count = live?.members?.size ?: crew.crew?.members?.size ?: 0
            val archivedAt = crew.crew?.archivedAt
            val summary =
                if (archivedAt != null) {
                    "${count}명 · ${festivalDay(archivedAt)}에 끝남"
                } else {
                    // Figma S07: `N명 · M/d(E) 자정까지 유지`. 보관 시각(다음 날 00:00) 직전 날짜를 쓴다.
                    val until = crew.archivesAt?.let { " · ${festivalDay(it.minusSeconds(1))} 자정까지 유지" }.orEmpty()
                    "${count}명$until"
                }
            val members =
                live?.members.orEmpty().map { member ->
                    Member(
                        id = member.id,
                        nickname = member.nickname,
                        zone = crew.zoneName(member.zoneId),
                        status = member.state?.let(LiveBoardPresentation::crewStateText),
                        // 끝난 크루는 상대 시간 대신 마지막으로 바뀐 시각을 보여 준다.
                        time =
                            if (archivedAt == null) {
                                LiveBoardPresentation.relativeTime(member.updatedAt, now)
                            } else {
                                member.updatedAt?.let(LiveBoardPresentation::monthDayTime)
                            },
                        isMe = member.id == crew.sessionUserId,
                        isInactive =
                            member.state == FestivalCrewState.LEAVING.rawValue ||
                                (member.updatedAt?.let { Duration.between(it, now) >= INACTIVE_INTERVAL } ?: false),
                    )
                }
            val gather =
                live?.gatherRequest?.let { request ->
                    val expiresAt = request.expiresAt ?: request.at.plus(GATHER_LIFETIME)
                    val isExpired = request.id != null && request.id == crew.expiredGatherId
                    if (archivedAt != null || isExpired || !now.isBefore(expiresAt)) null else gather(request, crew, now)
                }
            val remaining = live?.gatherAvailableAt?.let { Duration.between(now, it) } ?: Duration.ZERO
            val (gatherTitle, canGather) =
                if (remaining > Duration.ZERO) {
                    val minutes = ceil(remaining.toMillis() / 60_000.0).toInt()
                    "모여! · ${minutes}분 뒤 다시 가능" to false
                } else {
                    "모여!" to (archivedAt == null)
                }
            return CrewPresentation(
                summary = summary,
                memberCount = "$count / ${FestivalCrew.MAX_MEMBERS}",
                isArchived = archivedAt != null,
                members = members,
                gather = gather,
                gatherTitle = gatherTitle,
                canGather = canGather,
            )
        }

        private fun gather(
            request: FestivalLiveBoardSnapshot.Crew.GatherRequest,
            crew: CrewState,
            now: Instant,
        ): Gather {
            val sender = crew.live?.members?.firstOrNull { it.id == request.byUserId }?.nickname ?: "크루원"
            val place = crew.zoneName(request.zoneId)?.let { "${it.withEuro()} " }.orEmpty()
            val time = LiveBoardPresentation.relativeTime(request.at, now)?.let { "$it · " }.orEmpty()
            val isSender = request.byUserId == crew.sessionUserId
            val canRespond = !isSender && request.id != null && crew.sessionUserId != null
            val summary = request.summary
            return Gather(
                title = "$sender: ${place}모여!",
                detail = "${time}크루원 모두에게 소리 알림을 보냈어요",
                actions =
                    if (canRespond) {
                        Actions(
                            goingTitle = "${place}가는 중",
                            selected = crew.myGatherResponse(request),
                            isResponding = crew.isRespondingGather,
                        )
                    } else {
                        null
                    },
                summary =
                    when {
                        !isSender -> null
                        summary.going + summary.ack == 0 -> "아직 응답한 크루원이 없어요"
                        else -> "가는 중 ${summary.going}명 · 확인 ${summary.ack}명"
                    },
            )
        }

        /** iOS `AppDateFormat.festivalDay`(`M/d(E)`, KST). */
        fun festivalDay(value: Instant): String =
            FestivalListPresentation.festivalDay(value.atZone(LiveBoardPresentation.zone).toLocalDate())
    }
}

private const val HANGUL_FIRST = 0xAC00
private const val HANGUL_LAST = 0xD7A3
private const val JONGSEONG_COUNT = 28

/** 받침 ㄹ의 종성 번호. */
private const val RIEUL = 8

/** "…으로/로". 받침 ㄹ 뒤와 받침 없는 글자 뒤에는 "로"를 쓴다(iOS `withEuro`). */
internal fun String.withEuro(): String {
    val last = lastOrNull()?.code ?: return this + "로"
    if (last !in HANGUL_FIRST..HANGUL_LAST) return this + "로"
    val jongseong = (last - HANGUL_FIRST) % JONGSEONG_COUNT
    return this + if (jongseong == 0 || jongseong == RIEUL) "로" else "으로"
}

/** "…이/가". 숫자로 끝나면 읽는 소리(영 · 일 · 삼 · 육 · 칠 · 팔은 받침)로 고른다. 한글 · 숫자가 아니면 "이"를 쓴다(iOS `withIGa`). */
internal fun String.withIGa(): String {
    val last = lastOrNull() ?: return this
    if (last in '0'..'9') return this + if (last in "2459") "가" else "이"
    if (last.code !in HANGUL_FIRST..HANGUL_LAST) return this + "이"
    return this + if ((last.code - HANGUL_FIRST) % JONGSEONG_COUNT == 0) "가" else "이"
}
