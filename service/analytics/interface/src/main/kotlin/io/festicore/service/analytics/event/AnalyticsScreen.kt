package io.festicore.service.analytics.event

/** iOS `AnalyticsScreen`. [rawValue]가 `screen` · `referrer` 속성 값이다. */
enum class AnalyticsScreen(val rawValue: String) {
    Splash("splash"),
    Login("login"),
    SignUp("sign_up"),
    FestivalList("festival_list"),
    FestivalFlag("festival_flag"),
    LiveBoard("live_board"),
    Timetable("timetable"),
    Profile("profile"),
    Web("web"),
}
