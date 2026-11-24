package io.festicore.service.analytics.event

/** iOS `AnalyticsButton`. [rawValue]가 이벤트 속성 값이다. */
enum class AnalyticsButton(val rawValue: String) {
    // 로그인
    LoginGoogle("login_google"),
    LoginApple("login_apple"),

    // 페스티벌 목록
    FestivalLiveActivity("festival_live_activity"),

    // 깃발
    FlagSort("flag_sort"),

    // 타임테이블
    TimetableSearch("timetable_search"),
    TimetableNotify("timetable_notify"),

    // 프로필
    ProfileLogout("profile_logout"),
    ProfileWithdraw("profile_withdraw"),
}
