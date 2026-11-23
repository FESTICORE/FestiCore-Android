package io.festicore.domain.festival.entity

import java.time.Instant

/** 축제 크루. iOS `FestivalCrew`. */
data class FestivalCrew(
    val id: String,
    val festivalId: String,
    val name: String,
    val ownerId: String,
    val members: List<Member>,
    val inviteCode: String?,
    val inviteExpiresAt: Instant?,
    /** 크루원 존 · 상태와 집결 요청. 상세 · 참여 응답에만 있다. */
    val live: FestivalLiveBoardSnapshot.Crew? = null,
    /** 축제가 끝나 보관된 시각. 보관된 크루는 읽기만 할 수 있다. */
    val archivedAt: Instant? = null,
) {
    data class Member(
        val userId: String,
        val nickname: String,
        val joinedAt: Instant,
    )

    companion object {
        /** 서버가 받는 크루 최대 인원. iOS `maxMembers`. */
        const val MAX_MEMBERS = 12

        /** 크루 이름 최대 글자 수. 이전에 만든 13~20자 이름은 조회만 된다. iOS `maxNameLength`. */
        const val MAX_NAME_LENGTH = 12
    }
}

/** 크루 요청 실패. iOS `FestivalCrewError`. */
sealed class FestivalCrewError : Exception() {
    /** 입력이 규칙에 맞지 않거나 서버가 400으로 거절했다. 잘못된 입력은 요청하지 않는다. */
    class InvalidInput : FestivalCrewError()

    /** 로그인 세션이 없거나 401. */
    class Unauthenticated : FestivalCrewError()

    /** 403 `TERMS_REQUIRED`. */
    class TermsRequired : FestivalCrewError()

    /** 409(이미 다른 크루에 있음). */
    class AlreadyInCrew : FestivalCrewError()

    /** 410. 축제가 끝났다. */
    class FestivalEnded : FestivalCrewError()

    /** 응답 형식 오류 · `code != SUCCESS` · 요청과 다른 응답. */
    class InvalidResponse : FestivalCrewError()

    /** 403(크루장 아님 · 모여 보낸 사람) · 크루장 나가기 409 `OWNER_CANNOT_LEAVE`. */
    class Forbidden : FestivalCrewError()

    /** 429. */
    class Cooldown : FestivalCrewError()

    /** 그 밖의 서버 · 네트워크 실패. */
    class Unavailable : FestivalCrewError()

    /** 404 · 형식이 틀린 초대 코드. 없거나 만료된 초대 코드. */
    class NotFound : FestivalCrewError()

    /** 409 `CREW_FULL`. 정원이 찬 크루. */
    class Full : FestivalCrewError()

    /** 모여 응답 409 `GATHER_EXPIRED`. 응답 시간(10분)이 지난 모여. */
    class GatherExpired : FestivalCrewError()
}
