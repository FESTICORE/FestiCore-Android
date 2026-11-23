package io.festicore.feature.festival

/**
 * 라이브 흐름이 상위에 요청하는 로그인의 문맥(iOS `FestivalCoordinator` delegate `loginRequested` · `crewLoginRequested` ·
 * `crewJoinLoginRequested(crewName:)` · `followLoginRequested`). 요청에 문맥이 없으면(`null`) 상위가 탭 기본 문맥을 쓴다.
 */
sealed interface FestivalLoginContext {
    /** 크루 만들기 · 크루 카드(iOS `crewLoginRequested`). */
    data object Crew : FestivalLoginContext

    /** 초대 링크 참여 확인(iOS `crewJoinLoginRequested`). [crewName]은 시트 문구에 넣는 크루 이름이다(불러오기 전이면 null). */
    data class CrewJoin(val crewName: String?) : FestivalLoginContext

    /** 보드 깃발 팔로우(iOS `followLoginRequested`). */
    data object Follow : FestivalLoginContext
}
