package io.festicore.service.api.crew

/** 크루 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `CrewAPI`. */
object CrewApi {
    /** 크루 생성. iOS `CrewAPI.create`. */
    const val CREWS = "api/v1/crews"

    /** 초대 코드로 참여. iOS `CrewAPI.join`. */
    const val JOIN = "$CREWS/join"

    /** 상세(GET) · 이름 변경(PATCH) · 해산(DELETE). iOS `CrewAPI.crew`. */
    fun crew(crewId: String) = "$CREWS/$crewId"

    /** 내 상태 공유. iOS `CrewAPI.status`. */
    fun status(crewId: String) = "$CREWS/$crewId/status"

    /** 초대 코드 재생성. iOS `CrewAPI.rotateInvite`. */
    fun rotateInvite(crewId: String) = "$CREWS/$crewId/invite/rotate"

    /** 크루장 넘기기. iOS `CrewAPI.transfer`. */
    fun transfer(crewId: String) = "$CREWS/$crewId/transfer"

    /** 크루원 내보내기 · 나가기. iOS `CrewAPI.member`. */
    fun member(crewId: String, userId: String) = "$CREWS/$crewId/members/$userId"

    /** 초대 미리보기(인증 없음). iOS `CrewAPI.invite`. */
    fun invite(code: String) = "$CREWS/invite/$code"

    /** 모여 요청. iOS `CrewAPI.gather`. */
    fun gather(crewId: String) = "$CREWS/$crewId/gather"

    /** 모여 응답. iOS `CrewAPI.gatherResponse`. */
    fun gatherResponse(crewId: String, gatherId: String) = "$CREWS/$crewId/gather/$gatherId/response"
}
