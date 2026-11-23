package io.festicore.feature.auth

/** 로그인 시트를 연 문맥. 시트 아이콘 · 문구를 고른다(iOS `LoginFeature.State.Context`). */
sealed interface LoginContext {
    data object Bookmark : LoginContext

    data object Profile : LoginContext

    data object Flag : LoginContext

    data object Crew : LoginContext

    /** 초대 링크로 크루에 들어가기(Figma S08 E). [crewName]은 시트 문구에 넣는 크루 이름이다. */
    data class CrewJoin(val crewName: String?) : LoginContext

    data object Follow : LoginContext

    /** 깃발 상세의 신고하기. */
    data object Report : LoginContext
}
